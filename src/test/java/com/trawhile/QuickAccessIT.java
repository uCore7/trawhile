package com.trawhile;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TE-F027.F01-01  GET /api/v1/quick-access
 * TE-F030.F01-01/02/03  POST/DELETE/PUT /api/v1/quick-access
 */
class QuickAccessIT extends BaseIT {

    @Test
    @Tag("TE-F027.F01-01")
    void listEntries_deactivatedNode_annotatedWithNonTrackableFlag() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Old Task");
        insertQuickAccess(userId, nodeId, 0);
        jdbc.update(
            "UPDATE nodes SET is_active = false, deactivated_at = NOW() WHERE id = ?",
            nodeId
        );

        mvc.perform(get("/api/v1/quick-access")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nodeId").value(nodeId.toString()))
                .andExpect(jsonPath("$[0].nonTrackable").value(true));

        Integer count = jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM quick_access qa
                JOIN user_profile up ON up.id = qa.profile_id
                WHERE up.user_id = ?
                """,
            Integer.class,
            userId
        );
        assertThat(count).isOne();
    }

    @Test
    @Tag("TE-F030.F01-01")
    void addEntry_insertsRowWithCorrectSortOrder() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        List<UUID> existingNodes = createQuickAccessNodes(userId, 8);
        UUID newNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Ninth");

        mvc.perform(post("/api/v1/quick-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nodeId":"%s"}
                            """.formatted(newNodeId))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        Integer count = jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM quick_access qa
                JOIN user_profile up ON up.id = qa.profile_id
                WHERE up.user_id = ?
                """,
            Integer.class,
            userId
        );
        assertThat(count).isEqualTo(9);

        Integer sortOrder = jdbc.queryForObject(
            """
                SELECT qa.sort_order
                FROM quick_access qa
                JOIN user_profile up ON up.id = qa.profile_id
                WHERE up.user_id = ? AND qa.node_id = ?
                """,
            Integer.class,
            userId,
            newNodeId
        );
        assertThat(sortOrder).isEqualTo(existingNodes.size());
    }

    @Test
    @Tag("TE-F030.F01-01")
    void addEntry_tenthEntry_returns409() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        createQuickAccessNodes(userId, 9);
        UUID tenthNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Tenth");

        mvc.perform(post("/api/v1/quick-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nodeId":"%s"}
                            """.formatted(tenthNodeId))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isConflict());

        Integer count = jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM quick_access qa
                JOIN user_profile up ON up.id = qa.profile_id
                WHERE up.user_id = ?
                """,
            Integer.class,
            userId
        );
        assertThat(count).isEqualTo(9);
    }

    @Test
    @Tag("TE-F030.F01-02")
    void removeEntry_deletesRow() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID keepNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Keep");
        UUID removeNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Remove");
        insertQuickAccess(userId, keepNodeId, 0);
        insertQuickAccess(userId, removeNodeId, 1);

        mvc.perform(delete("/api/v1/quick-access/" + removeNodeId)
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        Integer count = jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM quick_access qa
                JOIN user_profile up ON up.id = qa.profile_id
                WHERE up.user_id = ?
                """,
            Integer.class,
            userId
        );
        assertThat(count).isOne();

        Integer remaining = jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM quick_access qa
                JOIN user_profile up ON up.id = qa.profile_id
                WHERE up.user_id = ? AND qa.node_id = ?
                """,
            Integer.class,
            userId,
            removeNodeId
        );
        assertThat(remaining).isZero();
    }

    @Test
    @Tag("TE-F030.F01-03")
    void reorderEntries_updatesSortOrderForAllRows() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID firstNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "First");
        UUID secondNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Second");
        UUID thirdNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Third");
        insertQuickAccess(userId, firstNodeId, 0);
        insertQuickAccess(userId, secondNodeId, 1);
        insertQuickAccess(userId, thirdNodeId, 2);

        List<UUID> reordered = List.of(thirdNodeId, firstNodeId, secondNodeId);

        mvc.perform(put("/api/v1/quick-access/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nodeIds":[%s]}
                            """.formatted(asJsonUuidArray(reordered)))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        List<String> persistedOrder = jdbc.queryForList(
            """
                SELECT qa.node_id::text
                FROM quick_access qa
                JOIN user_profile up ON up.id = qa.profile_id
                WHERE up.user_id = ?
                ORDER BY qa.sort_order
                """,
            String.class,
            userId
        );
        assertThat(persistedOrder).containsExactly(
            thirdNodeId.toString(),
            firstNodeId.toString(),
            secondNodeId.toString()
        );
    }

    private List<UUID> createQuickAccessNodes(UUID userId, int count) {
        List<UUID> nodeIds = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Node " + i);
            insertQuickAccess(userId, nodeId, i);
            nodeIds.add(nodeId);
        }
        return nodeIds;
    }

    private void insertQuickAccess(UUID userId, UUID nodeId, int sortOrder) {
        UUID profileId = jdbc.queryForObject(
            "SELECT id FROM user_profile WHERE user_id = ?",
            UUID.class,
            userId
        );
        jdbc.update(
            "INSERT INTO quick_access (id, profile_id, node_id, sort_order) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(),
            profileId,
            nodeId,
            sortOrder
        );
    }

    private String asJsonUuidArray(List<UUID> nodeIds) {
        return nodeIds.stream()
            .map(id -> "\"" + id + "\"")
            .collect(Collectors.joining(","));
    }
}
