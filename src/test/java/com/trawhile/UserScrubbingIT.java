package com.trawhile;

import com.trawhile.service.UserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TE-F070.F01-01  scrubUser with active time record: record ended, profile deleted, stub retained
 * TE-F070.F01-02  scrubUser with no time records: users row deleted
 * TE-F070.F01-03  scrubUser with active MCP tokens: tokens revoked (revoked_at set)
 *
 * SR-F070.F01 is the shared "Ready for Scrubbing" state transition invoked by invitation
 * withdrawal (SR-F007.F01), expiry (SR-C010.C01), user removal (SR-F008.F01), and
 * self-anonymisation (SR-F047.F01). These tests exercise the state machine in isolation
 * by calling the HTTP remove endpoint as admin, which triggers SR-F070.F01 per SR-F008.F01.
 */
class UserScrubbingIT extends BaseIT {

    @Autowired
    private UserService userService;

    // -----------------------------------------------------------------------
    // TE-F070.F01-01
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-F070.F01-01")
    void scrubUser_withTimeRecords_endsActiveRecord_deletesProfile_retainsStub() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Project X");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "track");

        // Active time record (ended_at IS NULL)
        UUID recordId = TestFixtures.insertTimeRecord(
                jdbc, userId, nodeId,
                OffsetDateTime.now().minusHours(2), null);

        // Also insert a completed record to ensure users stub is retained
        TestFixtures.insertTimeRecord(
                jdbc, userId, nodeId,
                OffsetDateTime.now().minusHours(5),
                OffsetDateTime.now().minusHours(4));

        // Trigger scrubbing via HTTP remove endpoint (SR-F008.F01 → SR-F070.F01)
        mvc.perform(delete("/api/v1/users/" + userId)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Active record must have ended_at set (SR-F070.F01)
        OffsetDateTime endedAt = jdbc.queryForObject(
                "SELECT ended_at FROM time_records WHERE id = ?",
                OffsetDateTime.class, recordId);
        assertThat(endedAt)
                .as("ended_at must be set on the active time record during scrubbing")
                .isNotNull();

        // user_profile must be deleted (cascades to user_oauth_providers, quick_access)
        int profileCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_profile WHERE user_id = ?",
                Integer.class, userId);
        assertThat(profileCount).as("user_profile must be deleted during scrubbing").isZero();

        // node_authorizations must be deleted
        int authCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM node_authorizations WHERE user_id = ?",
                Integer.class, userId);
        assertThat(authCount).as("all node_authorizations must be deleted during scrubbing").isZero();

        // users row must be RETAINED because time_records reference this user_id
        int usersCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class, userId);
        assertThat(usersCount)
                .as("users stub must be retained when time_records reference the user")
                .isOne();
    }

    // -----------------------------------------------------------------------
    // TE-F070.F01-02
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-F070.F01-02")
    void scrubUser_noTimeRecords_noRequests_deletesUsersRow() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        // User with profile but no time records and no requests
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Ephemeral Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Team B");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "view");

        mvc.perform(delete("/api/v1/users/" + userId)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // users row must be DELETED when no time_records or requests reference it
        int usersCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class, userId);
        assertThat(usersCount)
                .as("users row must be deleted when no time_records or requests reference it")
                .isZero();
    }

    // -----------------------------------------------------------------------
    // TE-F070.F01-03
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-F070.F01-03")
    void scrubUser_revokesMcpTokens_setsRevokedAt() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Token User");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Task Board");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "track");

        // Insert an active MCP token (revoked_at IS NULL)
        UUID tokenId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO mcp_tokens (id, user_id, token_hash, label) VALUES (?, ?, ?, ?)",
                tokenId, userId, "sha256hashvalue0000000000000000000000000000000000000000000000000",
                "My Claude Token");

        // Confirm token is active before scrubbing
        Integer revokedBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mcp_tokens WHERE id = ? AND revoked_at IS NULL",
                Integer.class, tokenId);
        assertThat(revokedBefore).isOne();

        mvc.perform(delete("/api/v1/users/" + userId)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // All MCP tokens for the user must have revoked_at set (SR-F070.F01)
        Integer revokedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mcp_tokens WHERE user_id = ? AND revoked_at IS NOT NULL",
                Integer.class, userId);
        assertThat(revokedCount)
                .as("all MCP tokens must have revoked_at set during scrubbing")
                .isOne();

        Integer activeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mcp_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, userId);
        assertThat(activeCount)
                .as("no active (non-revoked) MCP tokens must remain after scrubbing")
                .isZero();
    }
}
