package com.trawhile;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TE-F024.F01-01/02  GET /api/v1/tracking
 * TE-F025.F01-01/02  GET /api/v1/tracking/history
 * TE-F026.F01-01/02/03  POST /api/v1/tracking/start
 * TE-F028.F01-01  POST /api/v1/tracking/start while another record is active
 * TE-F029.F01-01  POST /api/v1/tracking/stop
 */
class TrackingIT extends BaseIT {

    @Test
    @Tag("TE-F024.F01-01")
    void getStatus_activeEntry_returnsNodePathAndElapsedTime() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Tracker");
        UUID departmentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");
        UUID taskId = TestFixtures.insertNode(jdbc, departmentId, "Task");
        UUID recordId = insertTimeRecord(
            userId,
            taskId,
            OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10).withNano(0),
            null,
            "UTC",
            null
        );

        mvc.perform(get("/api/v1/tracking")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.recordId").value(recordId.toString()))
                .andExpect(jsonPath("$.nodeId").value(taskId.toString()))
                .andExpect(jsonPath("$.nodePath", hasSize(3)))
                .andExpect(jsonPath("$.nodePath[0].id").value(TestFixtures.ROOT_NODE_ID.toString()))
                .andExpect(jsonPath("$.nodePath[1].id").value(departmentId.toString()))
                .andExpect(jsonPath("$.nodePath[2].id").value(taskId.toString()))
                .andExpect(jsonPath("$.startedAt").isString())
                .andExpect(jsonPath("$.elapsedSeconds").value(greaterThan(0)));
    }

    @Test
    @Tag("TE-F024.F01-02")
    void getStatus_noActiveEntry_returnsEmptyTrackingState() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Tracker");

        mvc.perform(get("/api/v1/tracking")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.recordId").isEmpty())
                .andExpect(jsonPath("$.nodeId").isEmpty())
                .andExpect(jsonPath("$.startedAt").isEmpty())
                .andExpect(jsonPath("$.elapsedSeconds").isEmpty());
    }

    @Test
    @Tag("TE-F025.F01-01")
    void recentEntries_overlappingPair_bothFlaggedWithOverlap() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Tracker");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Task");
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).withNano(0);
        UUID firstId = insertTimeRecord(userId, nodeId, base.plusHours(9), base.plusHours(11), "UTC", null);
        UUID secondId = insertTimeRecord(userId, nodeId, base.plusHours(10), base.plusHours(12), "UTC", null);

        mvc.perform(get("/api/v1/tracking/history?limit=10&offset=0")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.id == '%s' && @.overlapping == true)]"
                    .formatted(firstId)).isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.id == '%s' && @.overlapping == true)]"
                    .formatted(secondId)).isNotEmpty());
    }

    @Test
    @Tag("TE-F025.F01-02")
    void recentEntries_gapBetweenConsecutiveEntries_flaggedWithGap() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Tracker");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Task");
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).withNano(0);
        UUID olderId = insertTimeRecord(userId, nodeId, base.plusHours(9), base.plusHours(10), "UTC", null);
        UUID newerId = insertTimeRecord(userId, nodeId, base.plusHours(11), base.plusHours(12), "UTC", null);

        mvc.perform(get("/api/v1/tracking/history?limit=10&offset=0")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.id == '%s' && @.hasGapBefore == true)]"
                    .formatted(newerId)).isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.id == '%s' && @.hasGapBefore == true)]"
                    .formatted(olderId)).isEmpty());
    }

    @Test
    @Tag("TE-F026.F01-01")
    void startTracking_trackAuthOnActiveLeafNode_insertsOpenEntry() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Tracker");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Leaf");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "track");

        mvc.perform(post("/api/v1/tracking/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nodeId":"%s","timezone":"Europe/Zurich"}
                            """.formatted(nodeId))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
                .andExpect(jsonPath("$.timezone").value("Europe/Zurich"));

        Integer openCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM time_records WHERE user_id = ? AND ended_at IS NULL",
            Integer.class,
            userId
        );
        assertThat(openCount).isOne();

        String storedTimezone = jdbc.queryForObject(
            "SELECT timezone FROM time_records WHERE user_id = ? AND ended_at IS NULL",
            String.class,
            userId
        );
        assertThat(storedTimezone).isEqualTo("Europe/Zurich");
    }

    @Test
    @Tag("TE-F026.F01-01")
    void startTracking_inactiveNode_returns409() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Tracker");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Inactive");
        jdbc.update(
            "UPDATE nodes SET is_active = false, deactivated_at = NOW() WHERE id = ?",
            nodeId
        );
        TestFixtures.grantAuth(jdbc, userId, nodeId, "track");

        mvc.perform(post("/api/v1/tracking/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nodeId":"%s","timezone":"UTC"}
                            """.formatted(nodeId))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isConflict());

        Integer openCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM time_records WHERE user_id = ? AND ended_at IS NULL",
            Integer.class,
            userId
        );
        assertThat(openCount).isZero();
    }

    @Test
    @Tag("TE-F026.F01-02")
    void startTracking_nodeWithActiveChildren_returns409() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Tracker");
        UUID parentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Parent");
        TestFixtures.insertNode(jdbc, parentId, "Active Child");
        TestFixtures.grantAuth(jdbc, userId, parentId, "track");

        mvc.perform(post("/api/v1/tracking/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nodeId":"%s","timezone":"UTC"}
                            """.formatted(parentId))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isConflict());

        Integer openCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM time_records WHERE user_id = ? AND ended_at IS NULL",
            Integer.class,
            userId
        );
        assertThat(openCount).isZero();
    }

    @Test
    @Tag("TE-F026.F01-03")
    void startTracking_noTrackAuth_returns403() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Tracker");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Leaf");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "view");

        mvc.perform(post("/api/v1/tracking/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nodeId":"%s","timezone":"UTC"}
                            """.formatted(nodeId))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        Integer openCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM time_records WHERE user_id = ? AND ended_at IS NULL",
            Integer.class,
            userId
        );
        assertThat(openCount).isZero();
    }

    @Test
    @Tag("TE-F028.F01-01")
    void switchTracking_endsExistingEntryAndInsertsNew_withinSingleTransaction() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Tracker");
        UUID oldNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Old Task");
        UUID newNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "New Task");
        TestFixtures.grantAuth(jdbc, userId, oldNodeId, "track");
        TestFixtures.grantAuth(jdbc, userId, newNodeId, "track");

        UUID oldRecordId = insertTimeRecord(
            userId,
            oldNodeId,
            OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).withNano(0),
            null,
            "UTC",
            "Existing record"
        );

        mvc.perform(post("/api/v1/tracking/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nodeId":"%s","timezone":"Europe/Berlin"}
                            """.formatted(newNodeId))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.nodeId").value(newNodeId.toString()))
                .andExpect(jsonPath("$.timezone").value("Europe/Berlin"));

        OffsetDateTime oldEndedAt = jdbc.queryForObject(
            "SELECT ended_at FROM time_records WHERE id = ?",
            OffsetDateTime.class,
            oldRecordId
        );
        assertThat(oldEndedAt).isNotNull();

        Integer openCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM time_records WHERE user_id = ? AND ended_at IS NULL",
            Integer.class,
            userId
        );
        assertThat(openCount).isOne();

        Integer totalCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM time_records WHERE user_id = ?",
            Integer.class,
            userId
        );
        assertThat(totalCount).isEqualTo(2);

        UUID openNodeId = jdbc.queryForObject(
            "SELECT node_id FROM time_records WHERE user_id = ? AND ended_at IS NULL",
            UUID.class,
            userId
        );
        assertThat(openNodeId).isEqualTo(newNodeId);
    }

    @Test
    @Tag("TE-F029.F01-01")
    void stopTracking_setsEndedAtOnActiveEntry() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Tracker");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Task");
        UUID recordId = insertTimeRecord(
            userId,
            nodeId,
            OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30).withNano(0),
            null,
            "UTC",
            null
        );

        mvc.perform(post("/api/v1/tracking/stop")
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.recordId").isEmpty())
                .andExpect(jsonPath("$.nodeId").isEmpty());

        OffsetDateTime endedAt = jdbc.queryForObject(
            "SELECT ended_at FROM time_records WHERE id = ?",
            OffsetDateTime.class,
            recordId
        );
        assertThat(endedAt).isNotNull();

        mvc.perform(get("/api/v1/tracking")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.recordId").isEmpty())
                .andExpect(jsonPath("$.nodeId").isEmpty());
    }

    @Test
    @Tag("TE-F029.F01-01")
    void stopTracking_noActiveEntry_returns400() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Tracker");

        mvc.perform(post("/api/v1/tracking/stop")
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
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
