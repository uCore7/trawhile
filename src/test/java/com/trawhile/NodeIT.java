package com.trawhile;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TE-F014.F01-01  GET /api/v1/nodes/{id} — return node details and direct children
 * TE-F015.F01-01  POST /api/v1/nodes/{id}/children — create child node as admin
 * TE-F016.F01-01  PATCH /api/v1/nodes/{id} — update node fields; oversized logo rejected
 * TE-F016.F01-02  PUT /api/v1/nodes/{id}/logo — store and retrieve valid logo; reject invalid MIME type
 * TE-F017.F01-01  PUT /api/v1/nodes/{id}/children/order — reorder direct children
 * TE-F018.F01-01  POST /api/v1/nodes/{id}/deactivate — deactivate node unless active children exist
 * TE-F018.F01-02  POST /api/v1/nodes/{id}/deactivate — active time record on node does not block
 * TE-F019.F01-01  POST /api/v1/nodes/{id}/reactivate — reactivate node
 * TE-F020.F01-01  POST /api/v1/nodes/{id}/move — move node when admin on both ends
 * TE-F020.F01-02  POST /api/v1/nodes/{id}/move — reject move to descendant or without destination admin
 */
class NodeIT extends BaseIT {

    private static final byte[] PNG_BYTES = new byte[] {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
        (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
        0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF, 0x00, 0x00,
        0x02, 0x05, 0x01, 0x02, (byte) 0xA7, 0x69, (byte) 0xE6, (byte) 0xD5,
        0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
        (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    @Test
    @Tag("TE-F014.F01-01")
    void getNode_viewAuth_returnsNodeDetailsAndDirectChildren() throws Exception {
        UUID viewerId = TestFixtures.insertUserWithProfile(jdbc, "Viewer");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");
        UUID childAId = TestFixtures.insertNode(jdbc, nodeId, "Project A");
        UUID childBId = TestFixtures.insertNode(jdbc, nodeId, "Project B");
        jdbc.update("UPDATE nodes SET sort_order = ? WHERE id = ?", 0, childAId);
        jdbc.update("UPDATE nodes SET sort_order = ? WHERE id = ?", 1, childBId);
        TestFixtures.grantAuth(jdbc, viewerId, nodeId, "view");

        mvc.perform(get("/api/v1/nodes/" + nodeId)
                        .with(TestSecurityHelper.authenticatedAs(viewerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(nodeId.toString()))
                .andExpect(jsonPath("$.name").value("Department"))
                .andExpect(jsonPath("$.children").isArray())
                .andExpect(jsonPath("$.children", hasSize(2)))
                .andExpect(jsonPath("$.children[0].id").value(childAId.toString()))
                .andExpect(jsonPath("$.children[1].id").value(childBId.toString()));
    }

    @Test
    @Tag("TE-F014.F01-01")
    void getNode_noAuth_returns403() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "No Access");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Restricted");

        mvc.perform(get("/api/v1/nodes/" + nodeId)
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("TE-F015.F01-01")
    void createChild_adminAuth_insertsNodeWithSortOrderOneGreaterThanMax() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID parentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Parent");
        UUID existingAId = TestFixtures.insertNode(jdbc, parentId, "Existing A");
        UUID existingBId = TestFixtures.insertNode(jdbc, parentId, "Existing B");
        jdbc.update("UPDATE nodes SET sort_order = ? WHERE id = ?", 2, existingAId);
        jdbc.update("UPDATE nodes SET sort_order = ? WHERE id = ?", 7, existingBId);
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        int countBefore = jdbc.queryForObject(
            "SELECT COUNT(*) FROM nodes WHERE parent_id = ?",
            Integer.class,
            parentId
        );
        Integer maxSortOrderBefore = jdbc.queryForObject(
            "SELECT COALESCE(MAX(sort_order), -1) FROM nodes WHERE parent_id = ?",
            Integer.class,
            parentId
        );

        mvc.perform(post("/api/v1/nodes/" + parentId + "/children")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Created Child","description":"Created by test"}
                            """)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Created Child"))
                .andExpect(jsonPath("$.parentId").value(parentId.toString()));

        int countAfter = jdbc.queryForObject(
            "SELECT COUNT(*) FROM nodes WHERE parent_id = ?",
            Integer.class,
            parentId
        );
        assertThat(countAfter).isEqualTo(countBefore + 1);

        Map<String, Object> createdRow = jdbc.queryForMap(
            """
                SELECT sort_order, is_active, deactivated_at
                FROM nodes
                WHERE parent_id = ? AND name = ?
                """,
            parentId,
            "Created Child"
        );
        assertThat(createdRow.get("sort_order")).isEqualTo(maxSortOrderBefore + 1);
        assertThat(createdRow.get("is_active")).isEqualTo(true);
        assertThat(createdRow.get("deactivated_at")).isNull();
    }

    @Test
    @Tag("TE-F015.F01-01")
    void createChild_nonAdmin_returns403() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Non Admin");
        UUID parentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Parent");

        mvc.perform(post("/api/v1/nodes/" + parentId + "/children")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Blocked Child"}
                            """)
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("TE-F016.F01-01")
    void updateNode_name_persistedInDatabase() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Old Name");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        mvc.perform(patch("/api/v1/nodes/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Renamed Node"}
                            """)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Node"));

        String persistedName = jdbc.queryForObject(
            "SELECT name FROM nodes WHERE id = ?",
            String.class,
            nodeId
        );
        assertThat(persistedName).isEqualTo("Renamed Node");
    }

    @Test
    @Tag("TE-F016.F01-01")
    void uploadLogo_exceeds256KB_returns400() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Logo Node");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        byte[] tooLargePayload = new byte[256 * 1024 + 1];
        MockMultipartFile logo = new MockMultipartFile(
            "logo",
            "too-large.png",
            MediaType.IMAGE_PNG_VALUE,
            tooLargePayload
        );

        mvc.perform(multipart("/api/v1/nodes/" + nodeId + "/logo")
                        .file(logo)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Tag("TE-F016.F01-02")
    void uploadLogo_validPng_stored() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID viewerId = TestFixtures.insertUserWithProfile(jdbc, "Viewer");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Logo Node");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");
        TestFixtures.grantAuth(jdbc, viewerId, nodeId, "view");

        MockMultipartFile logo = new MockMultipartFile(
            "logo",
            "logo.png",
            MediaType.IMAGE_PNG_VALUE,
            PNG_BYTES
        );

        mvc.perform(multipart("/api/v1/nodes/" + nodeId + "/logo")
                        .file(logo)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").isString());

        byte[] storedLogo = jdbc.queryForObject(
            "SELECT logo FROM nodes WHERE id = ?",
            byte[].class,
            nodeId
        );
        String storedMimeType = jdbc.queryForObject(
            "SELECT logo_mime_type FROM nodes WHERE id = ?",
            String.class,
            nodeId
        );
        assertThat(storedLogo).isEqualTo(PNG_BYTES);
        assertThat(storedMimeType).isEqualTo(MediaType.IMAGE_PNG_VALUE);

        mvc.perform(get("/api/v1/nodes/" + nodeId + "/logo")
                        .with(TestSecurityHelper.authenticatedAs(viewerId)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG_BYTES));
    }

    @Test
    @Tag("TE-F016.F01-02")
    void uploadLogo_imageGifMimeType_returns400() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Logo Node");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        MockMultipartFile logo = new MockMultipartFile(
            "logo",
            "logo.gif",
            MediaType.IMAGE_GIF_VALUE,
            new byte[] {0x47, 0x49, 0x46}
        );

        mvc.perform(multipart("/api/v1/nodes/" + nodeId + "/logo")
                        .file(logo)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Tag("TE-F017.F01-01")
    void reorderChildren_updatesAllSortOrderValues() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID parentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Parent");
        UUID childAId = TestFixtures.insertNode(jdbc, parentId, "Child A");
        UUID childBId = TestFixtures.insertNode(jdbc, parentId, "Child B");
        UUID childCId = TestFixtures.insertNode(jdbc, parentId, "Child C");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        List<UUID> submittedOrder = List.of(childCId, childAId, childBId);

        mvc.perform(put("/api/v1/nodes/" + parentId + "/children/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"childIds":["%s","%s","%s"]}
                            """.formatted(childCId, childAId, childBId))
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> orderedRows = jdbc.queryForList(
            "SELECT id, sort_order FROM nodes WHERE parent_id = ? ORDER BY id",
            parentId
        );
        Map<UUID, Integer> expectedSortOrders = Map.of(
            submittedOrder.get(0), 0,
            submittedOrder.get(1), 1,
            submittedOrder.get(2), 2
        );
        for (Map<String, Object> row : orderedRows) {
            UUID childId = (UUID) row.get("id");
            assertThat(row.get("sort_order")).isEqualTo(expectedSortOrders.get(childId));
        }
    }

    @Test
    @Tag("TE-F017.F01-01")
    void reorderChildren_nonAdmin_returns403() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Non Admin");
        UUID parentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Parent");
        UUID childId = TestFixtures.insertNode(jdbc, parentId, "Child");

        mvc.perform(put("/api/v1/nodes/" + parentId + "/children/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"childIds":["%s"]}
                            """.formatted(childId))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("TE-F018.F01-01")
    void deactivateNode_leafNode_setsIsActiveFalseAndDeactivatedAt() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Leaf");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        mvc.perform(post("/api/v1/nodes/" + nodeId + "/deactivate")
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false))
                .andExpect(jsonPath("$.deactivatedAt").isNotEmpty());

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT is_active, deactivated_at FROM nodes WHERE id = ?",
            nodeId
        );
        assertThat(row.get("is_active")).isEqualTo(false);
        assertThat(row.get("deactivated_at")).isNotNull();
    }

    @Test
    @Tag("TE-F018.F01-01")
    void deactivateNode_withActiveChildren_returns409() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Parent");
        TestFixtures.insertNode(jdbc, nodeId, "Active Child");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        mvc.perform(post("/api/v1/nodes/" + nodeId + "/deactivate")
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @Tag("TE-F018.F01-02")
    void deactivateNode_withActiveTimeRecord_notBlocked() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID trackedUserId = TestFixtures.insertUserWithProfile(jdbc, "Tracked User");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Trackable Leaf");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");
        TestFixtures.insertTimeRecord(
            jdbc,
            trackedUserId,
            nodeId,
            OffsetDateTime.now().minusHours(1),
            null
        );

        mvc.perform(post("/api/v1/nodes/" + nodeId + "/deactivate")
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().is2xxSuccessful());

        Boolean isActive = jdbc.queryForObject(
            "SELECT is_active FROM nodes WHERE id = ?",
            Boolean.class,
            nodeId
        );
        assertThat(isActive).isFalse();
    }

    @Test
    @Tag("TE-F019.F01-01")
    void reactivateNode_setsIsActiveTrueAndClearsDeactivatedAt() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Inactive Node");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");
        jdbc.update(
            "UPDATE nodes SET is_active = false, deactivated_at = ? WHERE id = ?",
            OffsetDateTime.now().minusDays(1),
            nodeId
        );

        mvc.perform(post("/api/v1/nodes/" + nodeId + "/reactivate")
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.deactivatedAt").value(nullValue()));

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT is_active, deactivated_at FROM nodes WHERE id = ?",
            nodeId
        );
        assertThat(row.get("is_active")).isEqualTo(true);
        assertThat(row.get("deactivated_at")).isNull();
    }

    @Test
    @Tag("TE-F019.F01-01")
    void reactivateNode_nonAdmin_returns403() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Non Admin");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Inactive Node");
        jdbc.update(
            "UPDATE nodes SET is_active = false, deactivated_at = ? WHERE id = ?",
            OffsetDateTime.now().minusDays(1),
            nodeId
        );

        mvc.perform(post("/api/v1/nodes/" + nodeId + "/reactivate")
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("TE-F020.F01-01")
    void moveNode_adminOnBothEnds_updatesParentIdAndAppendsSortOrder() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID sourceParentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Source Parent");
        UUID destinationParentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Destination Parent");
        UUID movedNodeId = TestFixtures.insertNode(jdbc, sourceParentId, "Moved Node");
        UUID destinationChildAId = TestFixtures.insertNode(jdbc, destinationParentId, "Destination A");
        UUID destinationChildBId = TestFixtures.insertNode(jdbc, destinationParentId, "Destination B");
        jdbc.update("UPDATE nodes SET sort_order = ? WHERE id = ?", 3, destinationChildAId);
        jdbc.update("UPDATE nodes SET sort_order = ? WHERE id = ?", 8, destinationChildBId);
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        mvc.perform(post("/api/v1/nodes/" + movedNodeId + "/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"destinationParentId":"%s"}
                            """.formatted(destinationParentId))
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(destinationParentId.toString()));

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT parent_id, sort_order FROM nodes WHERE id = ?",
            movedNodeId
        );
        assertThat(row.get("parent_id")).isEqualTo(destinationParentId);
        assertThat(row.get("sort_order")).isEqualTo(9);
    }

    @Test
    @Tag("TE-F020.F01-02")
    void moveNode_destinationIsOwnDescendant_returns409() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID parentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Parent");
        UUID nodeId = TestFixtures.insertNode(jdbc, parentId, "Node");
        UUID childId = TestFixtures.insertNode(jdbc, nodeId, "Child");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        mvc.perform(post("/api/v1/nodes/" + nodeId + "/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"destinationParentId":"%s"}
                            """.formatted(childId))
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @Tag("TE-F020.F01-02")
    void moveNode_noAdminOnDestination_returns403() throws Exception {
        UUID limitedAdminId = TestFixtures.insertUserWithProfile(jdbc, "Limited Admin");
        UUID sourceParentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Source Parent");
        UUID destinationParentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Destination Parent");
        UUID nodeId = TestFixtures.insertNode(jdbc, sourceParentId, "Movable");
        TestFixtures.grantAuth(jdbc, limitedAdminId, sourceParentId, "admin");

        mvc.perform(post("/api/v1/nodes/" + nodeId + "/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"destinationParentId":"%s"}
                            """.formatted(destinationParentId))
                        .with(TestSecurityHelper.authenticatedAs(limitedAdminId))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
