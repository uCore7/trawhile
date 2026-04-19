package com.trawhile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Epic 9 token-management integration tests.
 *
 * Note: the task brief short-hands the endpoints as `/api/v1/mcp/tokens`, while the OpenAPI
 * contract exposes them under `/api/v1/account/mcp-tokens` and `/api/v1/admin/mcp-tokens`.
 * These tests follow the documented REST contract.
 */
class McpTokenIT extends BaseIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @Tag("TE-F053.F01-01")
    void generateToken_insertsRowWithHash_returnsRawTokenOnce() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Token Owner");

        MvcResult firstResult = mvc.perform(post("/api/v1/account/mcp-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "label": "Claude Desktop",
                              "expiresAt": "2030-01-01T00:00:00Z"
                            }
                            """)
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.mcpToken.label").value("Claude Desktop"))
                .andReturn();

        JsonNode firstBody = OBJECT_MAPPER.readTree(firstResult.getResponse().getContentAsString());
        String firstRawToken = firstBody.path("token").asText();
        UUID firstTokenId = UUID.fromString(firstBody.path("mcpToken").path("id").asText());

        assertThat(firstRawToken).isNotBlank();
        assertThat(jdbc.queryForObject(
            "SELECT token_hash FROM mcp_tokens WHERE id = ?",
            String.class,
            firstTokenId
        )).isEqualTo(sha256Hex(firstRawToken));
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM mcp_tokens WHERE token_hash = ? OR label = ?",
            Integer.class,
            firstRawToken,
            firstRawToken
        )).as("the raw token must never be stored in clear text").isZero();

        MvcResult secondResult = mvc.perform(post("/api/v1/account/mcp-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"label":"Claude Code"}
                            """)
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.mcpToken.label").value("Claude Code"))
                .andReturn();

        JsonNode secondBody = OBJECT_MAPPER.readTree(secondResult.getResponse().getContentAsString());
        String secondRawToken = secondBody.path("token").asText();
        UUID secondTokenId = UUID.fromString(secondBody.path("mcpToken").path("id").asText());

        assertThat(secondRawToken).isNotBlank().isNotEqualTo(firstRawToken);
        assertThat(jdbc.queryForObject(
            "SELECT token_hash FROM mcp_tokens WHERE id = ?",
            String.class,
            secondTokenId
        )).isEqualTo(sha256Hex(secondRawToken));
    }

    @Test
    @Tag("TE-F054.F01-01")
    void listOwnTokens_returnsNonRevokedTokensOnly() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Token Owner");
        UUID activeTokenId = insertMcpToken(
            userId,
            "active-token-value",
            "Active token",
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
            null
        );
        UUID revokedTokenId = insertMcpToken(
            userId,
            "revoked-token-value",
            "Revoked token",
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
            OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)
        );

        MvcResult result = mvc.perform(get("/api/v1/account/mcp-tokens")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(activeTokenId.toString()))
                .andExpect(jsonPath("$[0].label").value("Active token"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
            .contains(activeTokenId.toString())
            .doesNotContain(revokedTokenId.toString())
            .doesNotContain("Revoked token");
    }

    @Test
    @Tag("TE-F053.F02-01")
    void mcpRequest_validToken_authenticates_updatesLastUsedAt() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "MCP User");
        UUID tokenId = insertMcpToken(userId, "valid-mcp-token", "Desktop", null, null);
        OffsetDateTime beforeCall = OffsetDateTime.now(ZoneOffset.UTC);

        MvcResult result = performMcpToolCall(
            "valid-mcp-token",
            "{}",
            "get_tracking_status",
            "getTrackingStatus"
        );

        assertThat(result.getResponse().getStatus()).isBetween(200, 299);

        OffsetDateTime lastUsedAt = jdbc.queryForObject(
            "SELECT last_used_at FROM mcp_tokens WHERE id = ?",
            OffsetDateTime.class,
            tokenId
        );
        assertThat(lastUsedAt).isNotNull();
        assertThat(lastUsedAt).isAfterOrEqualTo(beforeCall.minusSeconds(1));
        assertThat(lastUsedAt).isBeforeOrEqualTo(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(5));
    }

    @Test
    @Tag("TE-F053.F02-02")
    void mcpRequest_invalidToken_returns401() throws Exception {
        MvcResult result = performMcpToolCall(
            "missing-token-value",
            "{}",
            "get_tracking_status",
            "getTrackingStatus"
        );

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @Tag("TE-F053.F02-03")
    void mcpRequest_revokedToken_returns401() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Revoked User");
        insertMcpToken(
            userId,
            "revoked-mcp-token",
            "Desktop",
            null,
            OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5)
        );

        MvcResult result = performMcpToolCall(
            "revoked-mcp-token",
            "{}",
            "get_tracking_status",
            "getTrackingStatus"
        );

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @Tag("TE-F055.F01-01")
    void revokeOwnToken_setsRevokedAt_logsSecurityEvent() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Token Owner");
        UUID tokenId = insertMcpToken(userId, "own-token-value", "Desktop", null, null);
        int before = countSecurityEvents("MCP_TOKEN_REVOKED");

        mvc.perform(delete("/api/v1/account/mcp-tokens/" + tokenId)
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
            "SELECT revoked_at FROM mcp_tokens WHERE id = ?",
            OffsetDateTime.class,
            tokenId
        )).isNotNull();
        assertThat(countSecurityEvents("MCP_TOKEN_REVOKED")).isEqualTo(before + 1);
    }

    @Test
    @Tag("TE-F056.F01-01")
    void adminListTokens_admin_returnsAllActiveWithOwnerName() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Root Admin");
        UUID aliceId = TestFixtures.insertUserWithProfile(jdbc, "Alice");
        UUID bobId = TestFixtures.insertUserWithProfile(jdbc, "Bob");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        UUID aliceTokenId = insertMcpToken(aliceId, "alice-active-token", "Alice Desktop", null, null);
        UUID bobTokenId = insertMcpToken(bobId, "bob-active-token", "Bob Desktop", null, null);
        UUID revokedTokenId = insertMcpToken(
            bobId,
            "bob-revoked-token",
            "Bob Old Laptop",
            null,
            OffsetDateTime.now(ZoneOffset.UTC).minusHours(2)
        );

        MvcResult result = mvc.perform(get("/api/v1/admin/mcp-tokens")
                        .with(TestSecurityHelper.adminUser(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id == '%s' && @.userId == '%s' && @.userName == 'Alice')]"
                    .formatted(aliceTokenId, aliceId)).isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == '%s' && @.userId == '%s' && @.userName == 'Bob')]"
                    .formatted(bobTokenId, bobId)).isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
            .contains("Alice")
            .contains("Bob")
            .doesNotContain(revokedTokenId.toString())
            .doesNotContain("Bob Old Laptop");
    }

    @Test
    @Tag("TE-F056.F01-01")
    void adminListTokens_nonAdmin_returns403() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Regular User");

        mvc.perform(get("/api/v1/admin/mcp-tokens")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("TE-F057.F01-01")
    void adminRevokeToken_anyUsersToken_setsRevokedAt_logsSecurityEvent() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Root Admin");
        UUID ownerId = TestFixtures.insertUserWithProfile(jdbc, "Token Owner");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");
        UUID tokenId = insertMcpToken(ownerId, "target-token-value", "Laptop", null, null);
        int before = countSecurityEvents("MCP_TOKEN_REVOKED");

        mvc.perform(delete("/api/v1/admin/mcp-tokens/" + tokenId)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
            "SELECT revoked_at FROM mcp_tokens WHERE id = ?",
            OffsetDateTime.class,
            tokenId
        )).isNotNull();
        assertThat(countSecurityEvents("MCP_TOKEN_REVOKED")).isEqualTo(before + 1);
    }

    @Test
    @Tag("TE-F057.F01-01")
    void adminRevokeToken_nonAdmin_returns403() throws Exception {
        UUID nonAdminId = TestFixtures.insertUserWithProfile(jdbc, "Regular User");
        UUID ownerId = TestFixtures.insertUserWithProfile(jdbc, "Token Owner");
        UUID tokenId = insertMcpToken(ownerId, "protected-token-value", "Desktop", null, null);

        mvc.perform(delete("/api/v1/admin/mcp-tokens/" + tokenId)
                        .with(TestSecurityHelper.authenticatedAs(nonAdminId))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    private UUID insertMcpToken(
        UUID userId,
        String rawToken,
        String label,
        OffsetDateTime expiresAt,
        OffsetDateTime revokedAt
    ) {
        UUID tokenId = UUID.randomUUID();
        jdbc.update(
            """
                INSERT INTO mcp_tokens (id, user_id, token_hash, label, expires_at, revoked_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
            tokenId,
            userId,
            sha256Hex(rawToken),
            label,
            expiresAt,
            revokedAt
        );
        return tokenId;
    }

    private int countSecurityEvents(String eventType) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM security_events WHERE event_type = ?",
            Integer.class,
            eventType
        );
    }

    private MvcResult performMcpToolCall(String rawToken, String argumentsJson, String... toolAliases)
        throws Exception {
        MvcResult fallback = null;
        for (String toolAlias : toolAliases) {
            MvcResult result = mvc.perform(post("/mcp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + rawToken)
                            .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": "it-request",
                                  "method": "tools/call",
                                  "params": {
                                    "name": "%s",
                                    "arguments": %s
                                  }
                                }
                                """.formatted(toolAlias, argumentsJson)))
                    .andReturn();
            int status = result.getResponse().getStatus();
            if ((status >= 200 && status < 300) || status == 401) {
                return result;
            }
            fallback = result;
        }
        return fallback;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute SHA-256", ex);
        }
    }
}
