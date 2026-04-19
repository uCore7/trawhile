package com.trawhile;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Epic 9 MCP tool integration tests.
 *
 * The requirements use snake_case tool names (`get_node_tree` etc.), while the task brief and
 * traceability table use camelCase labels (`getNodeTree` etc.). The helper below tries both
 * aliases against the same `/mcp` endpoint so the tests stay aligned with the spec semantics
 * rather than one naming convention.
 */
class McpToolIT extends BaseIT {

    @Test
    @Tag("TE-F069.F01-01")
    void getNodeTree_returnsSubtreeVisibleToTokenOwner() throws Exception {
        UUID ownerId = TestFixtures.insertUserWithProfile(jdbc, "MCP Owner");
        UUID visibleParentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible Department");
        UUID visibleChildId = TestFixtures.insertNode(jdbc, visibleParentId, "Visible Task");
        UUID hiddenParentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Hidden Department");
        UUID hiddenChildId = TestFixtures.insertNode(jdbc, hiddenParentId, "Hidden Task");
        TestFixtures.grantAuth(jdbc, ownerId, visibleParentId, "view");
        insertMcpToken(ownerId, "visible-tree-token", "Desktop", null, null);

        MvcResult result = performMcpToolCall(
            "visible-tree-token",
            "{}",
            "get_node_tree",
            "getNodeTree"
        );

        assertThat(result.getResponse().getStatus()).isBetween(200, 299);

        String body = result.getResponse().getContentAsString();
        assertThat(body)
            .contains(visibleParentId.toString())
            .contains(visibleChildId.toString())
            .contains("Visible Department")
            .contains("Visible Task")
            .doesNotContain(hiddenParentId.toString())
            .doesNotContain(hiddenChildId.toString())
            .doesNotContain("Hidden Department")
            .doesNotContain("Hidden Task");
    }

    @Test
    @Tag("TE-F069.F01-01")
    void mcpTool_expiredToken_returns401() throws Exception {
        UUID ownerId = TestFixtures.insertUserWithProfile(jdbc, "Expired Token Owner");
        insertMcpToken(
            ownerId,
            "expired-tool-token",
            "Old desktop",
            OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
            null
        );

        MvcResult result = performMcpToolCall(
            "expired-tool-token",
            "{}",
            "get_node_tree",
            "getNodeTree"
        );

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @Tag("TE-F069.F01-02")
    void getTimeRecords_ownRecords_returned() throws Exception {
        UUID ownerId = TestFixtures.insertUserWithProfile(jdbc, "Owner");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Tracked Task");
        TestFixtures.grantAuth(jdbc, ownerId, nodeId, "track");
        UUID recordId = insertTimeRecord(
            ownerId,
            nodeId,
            OffsetDateTime.of(2024, 6, 19, 8, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2024, 6, 19, 10, 30, 0, 0, ZoneOffset.UTC),
            "Europe/Zurich",
            "Own detailed record"
        );
        insertMcpToken(ownerId, "own-records-token", "Desktop", null, null);

        MvcResult result = performMcpToolCall(
            "own-records-token",
            "{}",
            "get_time_records",
            "getTimeRecords"
        );

        assertThat(result.getResponse().getStatus()).isBetween(200, 299);

        String body = result.getResponse().getContentAsString();
        assertThat(body)
            .contains(recordId.toString())
            .contains(ownerId.toString())
            .contains(nodeId.toString())
            .contains("Own detailed record")
            .contains("Europe/Zurich");
        assertThat(body.contains("startedAt") || body.contains("started_at")).isTrue();
        assertThat(body.contains("endedAt") || body.contains("ended_at")).isTrue();
    }

    @Test
    @Tag("TE-F069.F01-03")
    void getTimeRecords_otherUserWithViewAuth_returnsAggregatedDailyTotalsOnly() throws Exception {
        UUID ownerId = TestFixtures.insertUserWithProfile(jdbc, "Viewer");
        UUID otherUserId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Shared Task");
        TestFixtures.grantAuth(jdbc, ownerId, nodeId, "view");

        UUID firstRecordId = insertTimeRecord(
            otherUserId,
            nodeId,
            OffsetDateTime.of(2024, 6, 20, 8, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2024, 6, 20, 10, 0, 0, 0, ZoneOffset.UTC),
            "UTC",
            "Morning session"
        );
        UUID secondRecordId = insertTimeRecord(
            otherUserId,
            nodeId,
            OffsetDateTime.of(2024, 6, 20, 11, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2024, 6, 20, 12, 0, 0, 0, ZoneOffset.UTC),
            "UTC",
            "Afternoon session"
        );
        insertMcpToken(ownerId, "aggregated-records-token", "Desktop", null, null);

        MvcResult result = performMcpToolCall(
            "aggregated-records-token",
            """
                {
                  "user_id": "%1$s",
                  "userId": "%1$s",
                  "date_from": "2024-06-20",
                  "dateFrom": "2024-06-20",
                  "date_to": "2024-06-20",
                  "dateTo": "2024-06-20"
                }
                """.formatted(otherUserId),
            "get_time_records",
            "getTimeRecords"
        );

        assertThat(result.getResponse().getStatus()).isBetween(200, 299);

        String body = result.getResponse().getContentAsString();
        assertThat(body)
            .contains(otherUserId.toString())
            .contains("2024-06-20")
            .contains("10800")
            .doesNotContain(firstRecordId.toString())
            .doesNotContain(secondRecordId.toString())
            .doesNotContain("Morning session")
            .doesNotContain("Afternoon session")
            .doesNotContain("timezone")
            .doesNotContain("description")
            .doesNotContain("startedAt")
            .doesNotContain("endedAt")
            .doesNotContain("started_at")
            .doesNotContain("ended_at");
    }

    @Test
    @Tag("TE-F069.F01-04")
    void getTrackingStatus_returnsCurrentState() throws Exception {
        UUID ownerId = TestFixtures.insertUserWithProfile(jdbc, "Tracker");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Active Task");
        TestFixtures.grantAuth(jdbc, ownerId, nodeId, "track");
        UUID activeRecordId = insertTimeRecord(
            ownerId,
            nodeId,
            OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15).withNano(0),
            null,
            "UTC",
            "Running work"
        );
        insertMcpToken(ownerId, "tracking-status-token", "Desktop", null, null);

        MvcResult result = performMcpToolCall(
            "tracking-status-token",
            "{}",
            "get_tracking_status",
            "getTrackingStatus"
        );

        assertThat(result.getResponse().getStatus()).isBetween(200, 299);

        String body = result.getResponse().getContentAsString();
        assertThat(body)
            .contains("active")
            .contains(activeRecordId.toString())
            .contains(nodeId.toString());
    }

    @Test
    @Tag("TE-F069.F01-05")
    void getMemberSummaries_returnsBucketedTotalsPerMember() throws Exception {
        UUID ownerId = TestFixtures.insertUserWithProfile(jdbc, "Viewer");
        UUID firstMemberId = TestFixtures.insertUserWithProfile(jdbc, "Alice");
        UUID secondMemberId = TestFixtures.insertUserWithProfile(jdbc, "Bob");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");
        TestFixtures.grantAuth(jdbc, ownerId, TestFixtures.ROOT_NODE_ID, "view");

        insertTimeRecord(
            firstMemberId,
            nodeId,
            OffsetDateTime.of(2024, 6, 21, 8, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2024, 6, 21, 10, 0, 0, 0, ZoneOffset.UTC),
            "UTC",
            "Alice work"
        );
        insertTimeRecord(
            secondMemberId,
            nodeId,
            OffsetDateTime.of(2024, 6, 21, 9, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2024, 6, 21, 10, 0, 0, 0, ZoneOffset.UTC),
            "UTC",
            "Bob work"
        );
        insertMcpToken(ownerId, "member-summaries-token", "Desktop", null, null);

        MvcResult result = performMcpToolCall(
            "member-summaries-token",
            """
                {
                  "interval": "day",
                  "date_from": "2024-06-21",
                  "dateFrom": "2024-06-21",
                  "date_to": "2024-06-21",
                  "dateTo": "2024-06-21",
                  "node_id": "%1$s",
                  "nodeId": "%1$s"
                }
                """.formatted(nodeId),
            "get_member_summaries",
            "getMemberSummaries"
        );

        assertThat(result.getResponse().getStatus()).isBetween(200, 299);

        String body = result.getResponse().getContentAsString();
        assertThat(body)
            .contains(firstMemberId.toString())
            .contains(secondMemberId.toString())
            .contains("7200")
            .contains("3600")
            .contains("buckets")
            .doesNotContain("startedAt")
            .doesNotContain("endedAt")
            .doesNotContain("started_at")
            .doesNotContain("ended_at")
            .doesNotContain("timezone")
            .doesNotContain("description");
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

    private UUID insertTimeRecord(
        UUID userId,
        UUID nodeId,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        String timezone,
        String description
    ) {
        UUID recordId = UUID.randomUUID();
        jdbc.update(
            """
                INSERT INTO time_records (id, user_id, node_id, started_at, ended_at, timezone, description)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
            recordId,
            userId,
            nodeId,
            startedAt,
            endedAt,
            timezone,
            description
        );
        return recordId;
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
