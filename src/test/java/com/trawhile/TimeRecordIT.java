package com.trawhile;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TE-F031.F01-01/02/03  POST /api/v1/time-records
 * TE-F032.F01-01/02/03/04/05  PUT /api/v1/time-records/{id}
 * TE-F033.F01-01  DELETE /api/v1/time-records/{id}
 * TE-F034.F01-01  POST /api/v1/time-records/{id}/duplicate
 */
class TimeRecordIT extends BaseIT {

    @Test
    @Tag("TE-F031.F01-01")
    void createRetroactive_validInput_insertsRecordWithTimezone() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Leaf");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "track");

        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(3).withNano(0);
        OffsetDateTime endedAt = startedAt.plusHours(2);

        mvc.perform(post("/api/v1/time-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "nodeId":"%s",
                              "startedAt":"%s",
                              "endedAt":"%s",
                              "timezone":"Europe/Zurich",
                              "description":"Retro note"
                            }
                            """.formatted(nodeId, startedAt, endedAt))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
                .andExpect(jsonPath("$.timezone").value("Europe/Zurich"))
                .andExpect(jsonPath("$.description").value("Retro note"));

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM time_records WHERE user_id = ?",
            Integer.class,
            userId
        );
        assertThat(count).isOne();

        OffsetDateTime persistedStartedAt = jdbc.queryForObject(
            "SELECT started_at FROM time_records WHERE user_id = ?",
            OffsetDateTime.class,
            userId
        );
        OffsetDateTime persistedEndedAt = jdbc.queryForObject(
            "SELECT ended_at FROM time_records WHERE user_id = ?",
            OffsetDateTime.class,
            userId
        );
        String timezone = jdbc.queryForObject(
            "SELECT timezone FROM time_records WHERE user_id = ?",
            String.class,
            userId
        );
        assertThat(persistedStartedAt).isEqualTo(startedAt);
        assertThat(persistedEndedAt).isEqualTo(endedAt);
        assertThat(timezone).isEqualTo("Europe/Zurich");
    }

    @Test
    @Tag("TE-F031.F01-01")
    void createRetroactive_startedAtAfterEndedAt_returns400() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Leaf");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "track");

        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1).withNano(0);
        OffsetDateTime endedAt = startedAt.minusMinutes(1);

        mvc.perform(post("/api/v1/time-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "nodeId":"%s",
                              "startedAt":"%s",
                              "endedAt":"%s",
                              "timezone":"UTC"
                            }
                            """.formatted(nodeId, startedAt, endedAt))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM time_records WHERE user_id = ?",
            Integer.class,
            userId
        );
        assertThat(count).isZero();
    }

    @Test
    @Tag("TE-F031.F01-02")
    void createRetroactive_noTrackAuth_returns403() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Leaf");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "view");

        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).withNano(0);
        OffsetDateTime endedAt = startedAt.plusHours(1);

        mvc.perform(post("/api/v1/time-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "nodeId":"%s",
                              "startedAt":"%s",
                              "endedAt":"%s",
                              "timezone":"UTC"
                            }
                            """.formatted(nodeId, startedAt, endedAt))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("TE-F031.F01-03")
    void createRetroactive_inactiveNode_returns409() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Inactive");
        jdbc.update(
            "UPDATE nodes SET is_active = false, deactivated_at = NOW() WHERE id = ?",
            nodeId
        );
        TestFixtures.grantAuth(jdbc, userId, nodeId, "track");

        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).withNano(0);
        OffsetDateTime endedAt = startedAt.plusHours(1);

        mvc.perform(post("/api/v1/time-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "nodeId":"%s",
                              "startedAt":"%s",
                              "endedAt":"%s",
                              "timezone":"UTC"
                            }
                            """.formatted(nodeId, startedAt, endedAt))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @Tag("TE-F032.F01-01")
    void editRecord_withinFreezeCutoff_updatesFields() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID originalNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Original");
        UUID updatedNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Updated");
        TestFixtures.grantAuth(jdbc, userId, originalNodeId, "track");
        TestFixtures.grantAuth(jdbc, userId, updatedNodeId, "track");

        OffsetDateTime originalStartedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30).withNano(0);
        OffsetDateTime originalEndedAt = originalStartedAt.plusHours(1);
        UUID recordId = insertTimeRecord(
            userId,
            originalNodeId,
            originalStartedAt,
            originalEndedAt,
            "UTC",
            "Original description"
        );

        OffsetDateTime updatedStartedAt = originalStartedAt.plusHours(2);
        OffsetDateTime updatedEndedAt = updatedStartedAt.plusHours(3);

        mvc.perform(put("/api/v1/time-records/" + recordId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "nodeId":"%s",
                              "startedAt":"%s",
                              "endedAt":"%s",
                              "description":"Updated description"
                            }
                            """.formatted(updatedNodeId, updatedStartedAt, updatedEndedAt))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recordId.toString()))
                .andExpect(jsonPath("$.nodeId").value(updatedNodeId.toString()))
                .andExpect(jsonPath("$.description").value("Updated description"));

        UUID persistedNodeId = jdbc.queryForObject(
            "SELECT node_id FROM time_records WHERE id = ?",
            UUID.class,
            recordId
        );
        OffsetDateTime persistedStartedAt = jdbc.queryForObject(
            "SELECT started_at FROM time_records WHERE id = ?",
            OffsetDateTime.class,
            recordId
        );
        OffsetDateTime persistedEndedAt = jdbc.queryForObject(
            "SELECT ended_at FROM time_records WHERE id = ?",
            OffsetDateTime.class,
            recordId
        );
        String description = jdbc.queryForObject(
            "SELECT description FROM time_records WHERE id = ?",
            String.class,
            recordId
        );
        assertThat(persistedNodeId).isEqualTo(updatedNodeId);
        assertThat(persistedStartedAt).isEqualTo(updatedStartedAt);
        assertThat(persistedEndedAt).isEqualTo(updatedEndedAt);
        assertThat(description).isEqualTo("Updated description");
    }

    @Test
    @Tag("TE-F032.F01-01")
    void editRecord_startedAtBeforeFreezeCutoff_returns409() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Leaf");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "track");

        OffsetDateTime originalStartedAt = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(6).withNano(0);
        OffsetDateTime originalEndedAt = originalStartedAt.plusHours(1);
        UUID recordId = insertTimeRecord(userId, nodeId, originalStartedAt, originalEndedAt, "UTC", null);
        OffsetDateTime frozenStartedAt = OffsetDateTime.now(ZoneOffset.UTC).minusYears(5).withNano(0);

        mvc.perform(put("/api/v1/time-records/" + recordId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"startedAt":"%s"}
                            """.formatted(frozenStartedAt))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isConflict());

        OffsetDateTime persistedStartedAt = jdbc.queryForObject(
            "SELECT started_at FROM time_records WHERE id = ?",
            OffsetDateTime.class,
            recordId
        );
        assertThat(persistedStartedAt).isEqualTo(originalStartedAt);
    }

    @Test
    @Tag("TE-F032.F01-02")
    void editRecord_startedAtAfterEndedAt_returns400() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Leaf");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "track");

        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2).withNano(0);
        OffsetDateTime endedAt = startedAt.plusHours(1);
        UUID recordId = insertTimeRecord(userId, nodeId, startedAt, endedAt, "UTC", null);

        mvc.perform(put("/api/v1/time-records/" + recordId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "startedAt":"%s",
                              "endedAt":"%s"
                            }
                            """.formatted(endedAt.plusHours(2), endedAt))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Tag("TE-F032.F01-03")
    void editRecord_reassignToInactiveNode_returns409() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID originalNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Original");
        UUID inactiveNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Inactive");
        jdbc.update(
            "UPDATE nodes SET is_active = false, deactivated_at = NOW() WHERE id = ?",
            inactiveNodeId
        );
        TestFixtures.grantAuth(jdbc, userId, originalNodeId, "track");
        TestFixtures.grantAuth(jdbc, userId, inactiveNodeId, "track");

        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).withNano(0);
        UUID recordId = insertTimeRecord(userId, originalNodeId, startedAt, startedAt.plusHours(1), "UTC", null);

        mvc.perform(put("/api/v1/time-records/" + recordId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nodeId":"%s"}
                            """.formatted(inactiveNodeId))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @Tag("TE-F032.F01-04")
    void editRecord_reassignToNodeWithActiveChildren_returns409() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID originalNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Original");
        UUID parentNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Parent");
        TestFixtures.insertNode(jdbc, parentNodeId, "Active Child");
        TestFixtures.grantAuth(jdbc, userId, originalNodeId, "track");
        TestFixtures.grantAuth(jdbc, userId, parentNodeId, "track");

        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).withNano(0);
        UUID recordId = insertTimeRecord(userId, originalNodeId, startedAt, startedAt.plusHours(1), "UTC", null);

        mvc.perform(put("/api/v1/time-records/" + recordId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nodeId":"%s"}
                            """.formatted(parentNodeId))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @Tag("TE-F032.F01-05")
    void editRecord_reassignWithoutTrackAuth_returns403() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID originalNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Original");
        UUID restrictedNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Restricted");
        TestFixtures.grantAuth(jdbc, userId, originalNodeId, "track");
        TestFixtures.grantAuth(jdbc, userId, restrictedNodeId, "view");

        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).withNano(0);
        UUID recordId = insertTimeRecord(userId, originalNodeId, startedAt, startedAt.plusHours(1), "UTC", null);

        mvc.perform(put("/api/v1/time-records/" + recordId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nodeId":"%s"}
                            """.formatted(restrictedNodeId))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("TE-F033.F01-01")
    void deleteRecord_notFrozen_deletesRow() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Leaf");
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(2).withNano(0);
        UUID recordId = insertTimeRecord(userId, nodeId, startedAt, startedAt.plusHours(1), "UTC", null);

        mvc.perform(delete("/api/v1/time-records/" + recordId)
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM time_records WHERE id = ?",
            Integer.class,
            recordId
        );
        assertThat(count).isZero();
    }

    @Test
    @Tag("TE-F033.F01-01")
    void deleteRecord_frozen_returns409() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Leaf");
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC).minusYears(5).withNano(0);
        UUID recordId = insertTimeRecord(userId, nodeId, startedAt, startedAt.plusHours(1), "UTC", null);

        mvc.perform(delete("/api/v1/time-records/" + recordId)
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isConflict());

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM time_records WHERE id = ?",
            Integer.class,
            recordId
        );
        assertThat(count).isOne();
    }

    @Test
    @Tag("TE-F034.F01-01")
    void duplicateRecord_insertsNewRowWithNewTimesAndCopiedDescription() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Leaf");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "track");

        OffsetDateTime originalStartedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2).withNano(0);
        UUID originalRecordId = insertTimeRecord(
            userId,
            nodeId,
            originalStartedAt,
            originalStartedAt.plusHours(1),
            "Europe/Zurich",
            "Carry me over"
        );

        OffsetDateTime newStartedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(6).withNano(0);
        OffsetDateTime newEndedAt = newStartedAt.plusHours(2);

        mvc.perform(post("/api/v1/time-records/" + originalRecordId + "/duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "startedAt":"%s",
                              "endedAt":"%s"
                            }
                            """.formatted(newStartedAt, newEndedAt))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Carry me over"))
                .andExpect(jsonPath("$.startedAt").value(newStartedAt.toString()))
                .andExpect(jsonPath("$.endedAt").value(newEndedAt.toString()));

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM time_records WHERE user_id = ?",
            Integer.class,
            userId
        );
        assertThat(count).isEqualTo(2);

        UUID duplicatedRecordId = jdbc.queryForObject(
            """
                SELECT id
                FROM time_records
                WHERE user_id = ? AND started_at = ? AND ended_at = ?
                """,
            UUID.class,
            userId,
            newStartedAt,
            newEndedAt
        );
        assertThat(duplicatedRecordId).isNotEqualTo(originalRecordId);

        String description = jdbc.queryForObject(
            "SELECT description FROM time_records WHERE id = ?",
            String.class,
            duplicatedRecordId
        );
        assertThat(description).isEqualTo("Carry me over");
    }

    @Test
    @Tag("TE-F034.F01-01")
    void duplicateRecord_invalidTimeRange_returns400() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Leaf");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "track");

        OffsetDateTime originalStartedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).withNano(0);
        UUID originalRecordId = insertTimeRecord(
            userId,
            nodeId,
            originalStartedAt,
            originalStartedAt.plusHours(1),
            "UTC",
            "Original"
        );
        OffsetDateTime newStartedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1).withNano(0);
        OffsetDateTime newEndedAt = newStartedAt.minusMinutes(30);

        mvc.perform(post("/api/v1/time-records/" + originalRecordId + "/duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "startedAt":"%s",
                              "endedAt":"%s"
                            }
                            """.formatted(newStartedAt, newEndedAt))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM time_records WHERE user_id = ?",
            Integer.class,
            userId
        );
        assertThat(count).isOne();
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
}
