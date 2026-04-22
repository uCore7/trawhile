package com.trawhile;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TE-F004.F01-01  GET /api/v1/users — list all users as admin
 * TE-F008.F01-01  DELETE /api/v1/users/{id} — remove user triggers SR-F070.F01 scrubbing state
 * TE-F009.F01-01  GET /api/v1/users/{id}/authorizations — returns path-annotated assignments
 * TE-F009.F02-01  User-management flow reuses node authorization grant/revoke endpoints
 */
class UserManagementIT extends BaseIT {

    // -----------------------------------------------------------------------
    // TE-F004.F01-01
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-F004.F01-01")
    void listUsers_admin_returnsAllUsersWithCorrectStatus() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin User");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        // Active user (has user_profile)
        UUID activeId = TestFixtures.insertUserWithProfile(jdbc, "Active User");

        // Pending user (has pending_invitations row, no profile)
        UUID pendingId = TestFixtures.insertUser(jdbc);
        TestFixtures.insertPendingInvitation(jdbc, "pending@example.com", pendingId);

        // Anonymised user (no profile, no pending invitation — stub only)
        UUID anonymisedId = TestFixtures.insertUser(jdbc);

        mvc.perform(get("/api/v1/users")
                        .with(TestSecurityHelper.adminUser(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + activeId + "')].status").value("active"))
                .andExpect(jsonPath("$[?(@.id == '" + pendingId + "')].status").value("pending"))
                .andExpect(jsonPath("$[?(@.id == '" + anonymisedId + "')].status").value("anonymised"));
    }

    @Test
    @Tag("TE-F004.F01-01")
    void listUsers_nonAdmin_returns403() throws Exception {
        UUID nonAdminId = TestFixtures.insertUserWithProfile(jdbc, "Regular User");

        mvc.perform(get("/api/v1/users")
                        .with(TestSecurityHelper.authenticatedAs(nonAdminId)))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // TE-F008.F01-01
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-F008.F01-01")
    void removeUser_admin_triggersScrubbing() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin User");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        // Target user with active time record and authorization
        UUID targetId = TestFixtures.insertUserWithProfile(jdbc, "Target User");
        UUID childNode = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Project A");
        TestFixtures.grantAuth(jdbc, targetId, childNode, "track");
        UUID activeRecordId = TestFixtures.insertTimeRecord(
                jdbc, targetId, childNode,
                OffsetDateTime.now().minusHours(1), null);  // active: ended_at = NULL

        mvc.perform(delete("/api/v1/users/" + targetId)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Active time record must have ended_at set (SR-F070.F01)
        OffsetDateTime endedAt = jdbc.queryForObject(
                "SELECT ended_at FROM time_records WHERE id = ?",
                OffsetDateTime.class, activeRecordId);
        assertThat(endedAt).as("active time record must be stopped on scrubbing").isNotNull();

        // user_profile must be deleted (cascades to user_oauth_providers)
        int profileCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_profile WHERE user_id = ?",
                Integer.class, targetId);
        assertThat(profileCount).as("user_profile must be deleted on scrubbing").isZero();

        // node_authorizations must be deleted
        int authCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM node_authorizations WHERE user_id = ?",
                Integer.class, targetId);
        assertThat(authCount).as("node_authorizations must be deleted on scrubbing").isZero();

        // users row must be retained because time_records reference it
        int userCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class, targetId);
        assertThat(userCount).as("users row must be retained as anonymous stub when time_records exist").isOne();
    }

    @Test
    @Tag("TE-F008.F01-01")
    void removeUser_nonAdmin_returns403() throws Exception {
        UUID nonAdminId = TestFixtures.insertUserWithProfile(jdbc, "Regular User");
        UUID targetId = TestFixtures.insertUserWithProfile(jdbc, "Target User");

        mvc.perform(delete("/api/v1/users/" + targetId)
                        .with(TestSecurityHelper.authenticatedAs(nonAdminId))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // TE-F009.F01-01
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-F009.F01-01")
    void getUserAuthorizations_admin_returnsPathAnnotatedAssignments() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin User");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        UUID targetId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID childNode = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");
        TestFixtures.grantAuth(jdbc, targetId, childNode, "track");

        mvc.perform(get("/api/v1/users/" + targetId + "/authorizations")
                        .with(TestSecurityHelper.adminUser(adminId)))
                .andExpect(status().isOk())
                // Each entry must have nodePath (array from root to granted node) and authorization level
                .andExpect(jsonPath("$[0].nodeId").value(childNode.toString()))
                .andExpect(jsonPath("$[0].nodePath").isArray())
                .andExpect(jsonPath("$[0].nodePath", hasSize(2)))  // root + childNode
                .andExpect(jsonPath("$[0].authorization").value("track"));
    }

    @Test
    @Tag("TE-F009.F01-01")
    void getUserAuthorizations_nonAdmin_returns403() throws Exception {
        UUID nonAdminId = TestFixtures.insertUserWithProfile(jdbc, "Regular User");
        UUID targetId = TestFixtures.insertUserWithProfile(jdbc, "Member");

        mvc.perform(get("/api/v1/users/" + targetId + "/authorizations")
                        .with(TestSecurityHelper.authenticatedAs(nonAdminId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("TE-F009.F02-01")
    void manageUserAuthorizations_fromUserView_grantsAndRevokesWithinAdminScope() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin User");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        UUID targetId = TestFixtures.insertUserWithProfile(jdbc, "Managed User");
        UUID departmentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");

        // The user-management view selects a target user, then reuses the node authorization
        // endpoints with a node picker to apply the same SR-F021/SR-F022 rules.
        mvc.perform(put("/api/v1/nodes/" + departmentId + "/authorizations/" + targetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"authorization":"track"}
                            """)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
            "SELECT auth_level::text FROM node_authorizations WHERE node_id = ? AND user_id = ?",
            String.class,
            departmentId,
            targetId
        )).isEqualTo("track");

        mvc.perform(delete("/api/v1/nodes/" + departmentId + "/authorizations/" + targetId)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        Integer remainingRows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM node_authorizations WHERE node_id = ? AND user_id = ?",
            Integer.class,
            departmentId,
            targetId
        );
        assertThat(remainingRows).isZero();
    }
}
