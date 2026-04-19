package com.trawhile;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TE-F021.F01-01  PUT /api/v1/nodes/{id}/authorizations/{userId} — insert or update authorization
 * TE-F022.F01-01  DELETE /api/v1/nodes/{id}/authorizations/{userId} — delete row unless last admin
 * TE-F022.F01-02  DELETE /api/v1/nodes/{id}/authorizations/{userId} — reject non-admin
 * TE-F023.F01-01  GET /api/v1/nodes/{id}/authorizations — distinguish direct and inherited rows
 */
class AuthorizationIT extends BaseIT {

    @Test
    @Tag("TE-F021.F01-01")
    void grantAuth_admin_insertsNodeAuthorizationsRow() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID targetUserId = TestFixtures.insertUserWithProfile(jdbc, "Target User");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        mvc.perform(put("/api/v1/nodes/" + nodeId + "/authorizations/" + targetUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"authorization":"view"}
                            """)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
            "SELECT auth_level::text FROM node_authorizations WHERE node_id = ? AND user_id = ?",
            String.class,
            nodeId,
            targetUserId
        )).isEqualTo("view");

        mvc.perform(put("/api/v1/nodes/" + nodeId + "/authorizations/" + targetUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"authorization":"admin"}
                            """)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        Integer rowCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM node_authorizations WHERE node_id = ? AND user_id = ?",
            Integer.class,
            nodeId,
            targetUserId
        );
        String updatedLevel = jdbc.queryForObject(
            "SELECT auth_level::text FROM node_authorizations WHERE node_id = ? AND user_id = ?",
            String.class,
            nodeId,
            targetUserId
        );
        assertThat(rowCount).isOne();
        assertThat(updatedLevel).isEqualTo("admin");
    }

    @Test
    @Tag("TE-F021.F01-01")
    void grantAuth_nonAdmin_returns403() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Non Admin");
        UUID targetUserId = TestFixtures.insertUserWithProfile(jdbc, "Target User");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");

        mvc.perform(put("/api/v1/nodes/" + nodeId + "/authorizations/" + targetUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"authorization":"view"}
                            """)
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        Integer rowCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM node_authorizations WHERE node_id = ? AND user_id = ?",
            Integer.class,
            nodeId,
            targetUserId
        );
        assertThat(rowCount).isZero();
    }

    @Test
    @Tag("TE-F022.F01-01")
    void revokeAuth_admin_deletesRow() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID targetUserId = TestFixtures.insertUserWithProfile(jdbc, "Target User");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");
        TestFixtures.grantAuth(jdbc, targetUserId, nodeId, "view");

        mvc.perform(delete("/api/v1/nodes/" + nodeId + "/authorizations/" + targetUserId)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        Integer remainingRows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM node_authorizations WHERE node_id = ? AND user_id = ?",
            Integer.class,
            nodeId,
            targetUserId
        );
        assertThat(remainingRows).isZero();
    }

    @Test
    @Tag("TE-F022.F01-01")
    void revokeAuth_lastAdmin_returns409() throws Exception {
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID targetAdminId = TestFixtures.insertUserWithProfile(jdbc, "Target Admin");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");
        TestFixtures.grantAuth(jdbc, requesterId, TestFixtures.ROOT_NODE_ID, "admin");
        TestFixtures.grantAuth(jdbc, targetAdminId, nodeId, "admin");

        mvc.perform(delete("/api/v1/nodes/" + nodeId + "/authorizations/" + targetAdminId)
                        .with(TestSecurityHelper.adminUser(requesterId))
                        .with(csrf()))
                .andExpect(status().isConflict());

        Integer remainingRows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM node_authorizations WHERE node_id = ? AND user_id = ?",
            Integer.class,
            nodeId,
            targetAdminId
        );
        assertThat(remainingRows).isOne();
    }

    @Test
    @Tag("TE-F022.F01-02")
    void revokeAuth_nonAdmin_returns403() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Non Admin");
        UUID targetUserId = TestFixtures.insertUserWithProfile(jdbc, "Target User");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");
        TestFixtures.grantAuth(jdbc, targetUserId, nodeId, "view");

        mvc.perform(delete("/api/v1/nodes/" + nodeId + "/authorizations/" + targetUserId)
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        Integer remainingRows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM node_authorizations WHERE node_id = ? AND user_id = ?",
            Integer.class,
            nodeId,
            targetUserId
        );
        assertThat(remainingRows).isOne();
    }

    @Test
    @Tag("TE-F023.F01-01")
    void listNodeAuthorizations_admin_distinguishesDirectFromInherited() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID directUserId = TestFixtures.insertUserWithProfile(jdbc, "Direct User");
        UUID inheritedUserId = TestFixtures.insertUserWithProfile(jdbc, "Inherited User");
        UUID ancestorNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Ancestor");
        UUID nodeId = TestFixtures.insertNode(jdbc, ancestorNodeId, "Department");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");
        TestFixtures.grantAuth(jdbc, directUserId, nodeId, "view");
        TestFixtures.grantAuth(jdbc, inheritedUserId, ancestorNodeId, "track");

        mvc.perform(get("/api/v1/nodes/" + nodeId + "/authorizations")
                        .with(TestSecurityHelper.adminUser(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == '%s' && @.authorization == 'view' && @.inherited == false)]"
                    .formatted(directUserId)).isNotEmpty())
                .andExpect(jsonPath("$[?(@.userId == '%s' && @.authorization == 'track' && @.inherited == true && @.inheritedFromNodeId == '%s')]"
                    .formatted(inheritedUserId, ancestorNodeId)).isNotEmpty());
    }

    @Test
    @Tag("TE-F023.F01-01")
    void listNodeAuthorizations_nonAdmin_returns403() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Non Admin");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");

        mvc.perform(get("/api/v1/nodes/" + nodeId + "/authorizations")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isForbidden());
    }
}
