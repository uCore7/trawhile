package com.trawhile;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TE-F039.F01-01  POST /api/v1/nodes/{nodeId}/requests — submit request
 * TE-F041.F01-01  GET /api/v1/nodes/{nodeId}/requests — list node requests
 * TE-F042.F01-01  POST /api/v1/nodes/{nodeId}/requests/{requestId}/close — close request
 * TE-F042.F01-02  POST /api/v1/nodes/{nodeId}/requests/{requestId}/close — reject already closed request
 */
class RequestIT extends BaseIT {

    @Test
    @Tag("TE-F039.F01-01")
    void submitRequest_viewAuth_insertsRow() throws Exception {
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Target Node");
        TestFixtures.grantAuth(jdbc, requesterId, nodeId, "view");

        int countBefore = jdbc.queryForObject(
            "SELECT COUNT(*) FROM requests WHERE node_id = ?",
            Integer.class,
            nodeId
        );

        mvc.perform(post("/api/v1/nodes/" + nodeId + "/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"template":"free_text","body":"Please review this node."}
                            """)
                        .with(TestSecurityHelper.authenticatedAs(requesterId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
                .andExpect(jsonPath("$.requesterId").value(requesterId.toString()))
                .andExpect(jsonPath("$.requesterName").value("Requester"))
                .andExpect(jsonPath("$.template").value("free_text"))
                .andExpect(jsonPath("$.body").value("Please review this node."))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.resolvedAt").isEmpty())
                .andExpect(jsonPath("$.resolvedBy").isEmpty());

        int countAfter = jdbc.queryForObject(
            "SELECT COUNT(*) FROM requests WHERE node_id = ?",
            Integer.class,
            nodeId
        );
        assertThat(countAfter).isEqualTo(countBefore + 1);

        Map<String, Object> createdRow = jdbc.queryForMap(
            """
                SELECT requester_id, node_id, template, body, status, resolved_at, resolved_by
                FROM requests
                WHERE requester_id = ? AND node_id = ?
                ORDER BY created_at DESC
                LIMIT 1
                """,
            requesterId,
            nodeId
        );
        assertThat(createdRow.get("requester_id")).isEqualTo(requesterId);
        assertThat(createdRow.get("node_id")).isEqualTo(nodeId);
        assertThat(createdRow.get("template")).isEqualTo("free_text");
        assertThat(createdRow.get("body")).isEqualTo("Please review this node.");
        assertThat(createdRow.get("status")).isEqualTo("open");
        assertThat(createdRow.get("resolved_at")).isNull();
        assertThat(createdRow.get("resolved_by")).isNull();
    }

    @Test
    @Tag("TE-F039.F01-01")
    void submitRequest_noViewAuth_returns403() throws Exception {
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Blocked User");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Restricted Node");

        int countBefore = jdbc.queryForObject(
            "SELECT COUNT(*) FROM requests WHERE node_id = ?",
            Integer.class,
            nodeId
        );

        mvc.perform(post("/api/v1/nodes/" + nodeId + "/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"template":"free_text","body":"This should be rejected."}
                            """)
                        .with(TestSecurityHelper.authenticatedAs(requesterId))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        int countAfter = jdbc.queryForObject(
            "SELECT COUNT(*) FROM requests WHERE node_id = ?",
            Integer.class,
            nodeId
        );
        assertThat(countAfter).isEqualTo(countBefore);
    }

    @Test
    @Tag("TE-F041.F01-01")
    void listRequests_viewAuth_returnsOpenAndClosedRequests() throws Exception {
        UUID viewerId = TestFixtures.insertUserWithProfile(jdbc, "Viewer");
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Request Board");
        TestFixtures.grantAuth(jdbc, viewerId, TestFixtures.ROOT_NODE_ID, "view");

        UUID openRequestId = insertRequest(requesterId, nodeId, "grant_authorization", "Open request");
        UUID closedRequestId = insertClosedRequest(
            requesterId,
            nodeId,
            "create_child_node",
            "Closed request",
            viewerId
        );

        mvc.perform(get("/api/v1/nodes/" + nodeId + "/requests")
                        .with(TestSecurityHelper.authenticatedAs(viewerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id == '%s' && @.status == 'open')]"
                    .formatted(openRequestId), hasSize(1)))
                .andExpect(jsonPath("$[?(@.id == '%s' && @.status == 'closed')]"
                    .formatted(closedRequestId), hasSize(1)))
                .andExpect(jsonPath("$[?(@.id == '%s' && @.resolvedBy == '%s')]"
                    .formatted(closedRequestId, viewerId), hasSize(1)));
    }

    @Test
    @Tag("TE-F041.F01-01")
    void listRequests_noViewAuth_returns403() throws Exception {
        UUID viewerId = TestFixtures.insertUserWithProfile(jdbc, "Blocked Viewer");
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Request Board");
        insertRequest(requesterId, nodeId, "free_text", "Visible only to authorized users");

        mvc.perform(get("/api/v1/nodes/" + nodeId + "/requests")
                        .with(TestSecurityHelper.authenticatedAs(viewerId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("TE-F042.F01-01")
    void closeRequest_adminAuth_setsStatusClosedAndTimestamp() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Escalation Node");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");
        UUID requestId = insertRequest(requesterId, nodeId, "free_text", "Needs action");

        mvc.perform(post("/api/v1/nodes/" + nodeId + "/requests/" + requestId + "/close")
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("closed"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.resolvedBy").value(adminId.toString()));

        Map<String, Object> closedRow = jdbc.queryForMap(
            "SELECT status, resolved_at, resolved_by FROM requests WHERE id = ?",
            requestId
        );
        assertThat(closedRow.get("status")).isEqualTo("closed");
        assertThat(closedRow.get("resolved_at")).isNotNull();
        assertThat(closedRow.get("resolved_by")).isEqualTo(adminId);
    }

    @Test
    @Tag("TE-F042.F01-01")
    void closeRequest_nonAdmin_returns403() throws Exception {
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID viewerId = TestFixtures.insertUserWithProfile(jdbc, "Viewer");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Escalation Node");
        TestFixtures.grantAuth(jdbc, viewerId, nodeId, "view");
        UUID requestId = insertRequest(requesterId, nodeId, "free_text", "Needs admin review");

        mvc.perform(post("/api/v1/nodes/" + nodeId + "/requests/" + requestId + "/close")
                        .with(TestSecurityHelper.authenticatedAs(viewerId))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        Map<String, Object> persistedRow = jdbc.queryForMap(
            "SELECT status, resolved_at, resolved_by FROM requests WHERE id = ?",
            requestId
        );
        assertThat(persistedRow.get("status")).isEqualTo("open");
        assertThat(persistedRow.get("resolved_at")).isNull();
        assertThat(persistedRow.get("resolved_by")).isNull();
    }

    @Test
    @Tag("TE-F042.F01-02")
    void closeRequest_alreadyClosed_returns409() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Escalation Node");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");
        UUID requestId = insertClosedRequest(
            requesterId,
            nodeId,
            "free_text",
            "Already resolved",
            requesterId
        );

        OffsetDateTime resolvedAtBefore = jdbc.queryForObject(
            "SELECT resolved_at FROM requests WHERE id = ?",
            OffsetDateTime.class,
            requestId
        );

        mvc.perform(post("/api/v1/nodes/" + nodeId + "/requests/" + requestId + "/close")
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isConflict());

        Map<String, Object> persistedRow = jdbc.queryForMap(
            "SELECT status, resolved_at, resolved_by FROM requests WHERE id = ?",
            requestId
        );
        assertThat(persistedRow.get("status")).isEqualTo("closed");
        assertThat(persistedRow.get("resolved_at")).isEqualTo(resolvedAtBefore);
        assertThat(persistedRow.get("resolved_by")).isEqualTo(requesterId);
    }

    private UUID insertRequest(UUID requesterId, UUID nodeId, String template, String body) {
        UUID requestId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO requests (id, requester_id, node_id, template, body) VALUES (?, ?, ?, ?, ?)",
            requestId,
            requesterId,
            nodeId,
            template,
            body
        );
        return requestId;
    }

    private UUID insertClosedRequest(
        UUID requesterId,
        UUID nodeId,
        String template,
        String body,
        UUID resolvedBy
    ) {
        UUID requestId = insertRequest(requesterId, nodeId, template, body);
        jdbc.update(
            "UPDATE requests SET status = 'closed', resolved_at = ?, resolved_by = ? WHERE id = ?",
            OffsetDateTime.now().withNano(0),
            resolvedBy,
            requestId
        );
        return requestId;
    }
}
