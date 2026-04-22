package com.trawhile;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Epic 6 account integration tests.
 */
class AccountIT extends BaseIT {

    @Test
    @Tag("TE-F043.F01-01")
    void getProfile_returnsNameLinkedProvidersAndLastReportSettings() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Account Owner");
        insertProvider(userId, "google", "google-sub-account-owner");
        insertProvider(userId, "apple", "apple-sub-account-owner");

        mvc.perform(get("/api/v1/account")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("Account Owner"))
                .andExpect(jsonPath("$.providers", hasSize(2)))
                .andExpect(jsonPath("$.providers[?(@ == 'google')]").isNotEmpty())
                .andExpect(jsonPath("$.providers[?(@ == 'apple')]").isNotEmpty())
                .andExpect(jsonPath("$.lastReportSettings").value(nullValue()));
    }

    @Test
    @Tag("TE-F043.F01-01")
    void getProfile_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/v1/account"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("TE-F046.F01-01")
    void getOwnAuthorizations_returnsPathAnnotatedList() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Authorized Member");
        insertProvider(userId, "google", "google-sub-authorized-member");

        UUID departmentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");
        UUID projectId = TestFixtures.insertNode(jdbc, departmentId, "Project");
        TestFixtures.grantAuth(jdbc, userId, projectId, "track");

        mvc.perform(get("/api/v1/account/authorizations")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nodeId").value(projectId.toString()))
                .andExpect(jsonPath("$[0].authorization").value("track"))
                .andExpect(jsonPath("$[0].nodePath", hasSize(3)))
                .andExpect(jsonPath("$[0].nodePath[0].id").value(TestFixtures.ROOT_NODE_ID.toString()))
                .andExpect(jsonPath("$[0].nodePath[1].id").value(departmentId.toString()))
                .andExpect(jsonPath("$[0].nodePath[2].id").value(projectId.toString()));
    }

    @Test
    @Tag("TE-F066.F01-01")
    void saveReportSettings_persistedAsJsonbInUserProfile() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Report User");
        insertProvider(userId, "google", "google-sub-report-user");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Report Node");
        UUID colleagueId = TestFixtures.insertUserWithProfile(jdbc, "Colleague");

        mvc.perform(put("/api/v1/account/report-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "dateRange": {"from": "2026-03-01", "to": "2026-03-31"},
                              "interval": "month",
                              "nodeId": "%s",
                              "userId": "%s"
                            }
                            """.formatted(nodeId, colleagueId))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        String persistedSettings = jdbc.queryForObject(
            "SELECT last_report_settings::text FROM user_profile WHERE user_id = ?",
            String.class,
            userId
        );
        assertThat(persistedSettings).isNotBlank();

        mvc.perform(get("/api/v1/account")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReportSettings.dateRange.from").value("2026-03-01"))
                .andExpect(jsonPath("$.lastReportSettings.dateRange.to").value("2026-03-31"))
                .andExpect(jsonPath("$.lastReportSettings.interval").value("month"))
                .andExpect(jsonPath("$.lastReportSettings.nodeId").value(nodeId.toString()))
                .andExpect(jsonPath("$.lastReportSettings.userId").value(colleagueId.toString()));
    }

    @Test
    @Tag("TE-F044.F01-01")
    void linkProvider_insertsOauthRow() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Link Target");
        insertProvider(userId, "google", "google-sub-link-target");

        int before = countProvidersForUser(userId);

        mvc.perform(post("/api/v1/account/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"provider":"apple"}
                            """)
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(countProvidersForUser(userId)).isEqualTo(before + 1);
    }

    @Test
    @Tag("TE-F044.F01-01")
    void linkProvider_providerSubjectAlreadyLinkedToAnotherUser_returns409() throws Exception {
        UUID currentUserId = TestFixtures.insertUserWithProfile(jdbc, "Current User");
        insertProvider(currentUserId, "google", "google-sub-current-user");

        UUID otherUserId = TestFixtures.insertUserWithProfile(jdbc, "Other User");
        insertProvider(otherUserId, "apple", currentUserId.toString());

        int before = countProvidersForUser(currentUserId);

        mvc.perform(post("/api/v1/account/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"provider":"apple"}
                            """)
                        .with(TestSecurityHelper.authenticatedAs(currentUserId))
                        .with(csrf()))
                .andExpect(status().isConflict());

        assertThat(countProvidersForUser(currentUserId)).isEqualTo(before);
    }

    @Test
    @Tag("TE-F045.F01-01")
    void unlinkProvider_multipleProviders_deletesOneRow() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Multi Provider User");
        insertProvider(userId, "google", "google-sub-multi-provider-user");
        insertProvider(userId, "apple", "apple-sub-multi-provider-user");

        int before = countProvidersForUser(userId);

        mvc.perform(delete("/api/v1/account/providers/apple")
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(countProvidersForUser(userId)).isEqualTo(before - 1);
        assertThat(countSpecificProviderForUser(userId, "apple")).isZero();
        assertThat(countSpecificProviderForUser(userId, "google")).isOne();
    }

    @Test
    @Tag("TE-F045.F01-01")
    void unlinkProvider_lastProvider_returns409() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Single Provider User");
        insertProvider(userId, "google", "google-sub-single-provider-user");

        int before = countProvidersForUser(userId);

        mvc.perform(delete("/api/v1/account/providers/google")
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isConflict());

        assertThat(countProvidersForUser(userId)).isEqualTo(before);
    }

    @Test
    @Tag("TE-F047.F01-01")
    void anonymizeAccount_withTimeEntries_profileDeletedStubRetained() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Tracked User");
        insertProvider(userId, "google", "google-sub-tracked-user");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Tracked Node");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "track");
        TestFixtures.insertTimeRecord(
            jdbc,
            userId,
            nodeId,
            OffsetDateTime.now().minusHours(4),
            OffsetDateTime.now().minusHours(2)
        );
        jdbc.update(
            "INSERT INTO mcp_tokens (id, user_id, token_hash, label) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(),
            userId,
            "tracked-user-token-hash-0000000000000000000000000000000000000000000000001",
            "desktop"
        );
        jdbc.update(
            "INSERT INTO mcp_tokens (id, user_id, token_hash, label) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(),
            userId,
            "tracked-user-token-hash-0000000000000000000000000000000000000000000000002",
            "laptop"
        );

        mvc.perform(post("/api/v1/account/anonymize")
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_profile WHERE user_id = ?",
            Integer.class,
            userId
        )).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE id = ?",
            Integer.class,
            userId
        )).isOne();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM mcp_tokens WHERE user_id = ?",
            Integer.class,
            userId
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM mcp_tokens WHERE user_id = ? AND revoked_at IS NOT NULL",
            Integer.class,
            userId
        )).isEqualTo(2);
    }

    @Test
    @Tag("TE-F047.F01-02")
    void anonymizeAccount_noTimeEntries_usersRowDeleted() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Disposable User");
        insertProvider(userId, "google", "google-sub-disposable-user");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "View Node");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "view");

        mvc.perform(post("/api/v1/account/anonymize")
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE id = ?",
            Integer.class,
            userId
        )).isZero();
    }

    private void insertProvider(UUID userId, String provider, String subject) {
        UUID profileId = jdbc.queryForObject(
            "SELECT id FROM user_profile WHERE user_id = ?",
            UUID.class,
            userId
        );
        jdbc.update(
            "INSERT INTO user_oauth_providers (id, profile_id, provider, subject) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(),
            profileId,
            provider,
            subject
        );
    }

    private int countProvidersForUser(UUID userId) {
        return jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM user_oauth_providers uop
                JOIN user_profile up ON up.id = uop.profile_id
                WHERE up.user_id = ?
                """,
            Integer.class,
            userId
        );
    }

    private int countSpecificProviderForUser(UUID userId, String provider) {
        return jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM user_oauth_providers uop
                JOIN user_profile up ON up.id = uop.profile_id
                WHERE up.user_id = ? AND uop.provider = ?
                """,
            Integer.class,
            userId,
            provider
        );
    }
}
