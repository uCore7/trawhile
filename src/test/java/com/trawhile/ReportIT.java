package com.trawhile;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TE-F036.F01-01/02/03, TE-F036.F02-01/02, TE-F038.F01-01, TE-F052.F01-01/02/03
 */
class ReportIT extends BaseIT {

    @Test
    @Tag("TE-F036.F01-01")
    void getReport_summaryMode_returnsAggregatedTotalsPerNode() throws Exception {
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID memberId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID projectAlphaId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Project Alpha");
        UUID projectBetaId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Project Beta");
        TestFixtures.grantAuth(jdbc, requesterId, TestFixtures.ROOT_NODE_ID, "view");

        OffsetDateTime base = OffsetDateTime.of(2024, 6, 10, 8, 0, 0, 0, ZoneOffset.UTC);
        insertTimeRecord(memberId, projectAlphaId, base, base.plusHours(2), "UTC", "Alpha morning");
        insertTimeRecord(memberId, projectAlphaId, base.plusHours(3), base.plusHours(4), "UTC", "Alpha afternoon");
        insertTimeRecord(memberId, projectBetaId, base.plusHours(1), base.plusHours(2), "UTC", "Beta");

        MvcResult result = mvc.perform(get("/api/v1/reports")
                        .param("mode", "summary")
                        .param("from", "2024-06-10")
                        .param("to", "2024-06-10")
                        .with(TestSecurityHelper.authenticatedAs(requesterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("summary"))
                .andExpect(jsonPath("$.summary", hasSize(2)))
                .andExpect(jsonPath("$.summary[?(@.nodeId == '%s' && @.totalSeconds == 10800)]"
                    .formatted(projectAlphaId)).isNotEmpty())
                .andExpect(jsonPath("$.summary[?(@.nodeId == '%s' && @.totalSeconds == 3600)]"
                    .formatted(projectBetaId)).isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
            .doesNotContain("startedAt")
            .doesNotContain("endedAt");
    }

    @Test
    @Tag("TE-F036.F01-02")
    void getReport_detailedMode_returnsIndividualRows() throws Exception {
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID memberId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Task");
        TestFixtures.grantAuth(jdbc, requesterId, TestFixtures.ROOT_NODE_ID, "view");

        OffsetDateTime firstStartedAt = OffsetDateTime.of(2024, 6, 11, 8, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime firstEndedAt = firstStartedAt.plusHours(2);
        OffsetDateTime secondStartedAt = firstStartedAt.plusHours(3);
        OffsetDateTime secondEndedAt = secondStartedAt.plusMinutes(30);
        UUID firstRecordId = insertTimeRecord(
            memberId,
            nodeId,
            firstStartedAt,
            firstEndedAt,
            "UTC",
            "First detailed row"
        );
        UUID secondRecordId = insertTimeRecord(
            memberId,
            nodeId,
            secondStartedAt,
            secondEndedAt,
            "UTC",
            "Second detailed row"
        );

        MvcResult result = mvc.perform(get("/api/v1/reports")
                        .param("mode", "detailed")
                        .param("from", "2024-06-11")
                        .param("to", "2024-06-11")
                        .with(TestSecurityHelper.authenticatedAs(requesterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("detailed"))
                .andExpect(jsonPath("$.detailed", hasSize(2)))
                .andExpect(jsonPath("$.detailed[?(@.id == '%s' && @.nodeId == '%s')]"
                    .formatted(firstRecordId, nodeId)).isNotEmpty())
                .andExpect(jsonPath("$.detailed[?(@.id == '%s' && @.nodeId == '%s')]"
                    .formatted(secondRecordId, nodeId)).isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
            .contains("startedAt")
            .contains("endedAt")
            .contains(nodeId.toString())
            .contains(firstRecordId.toString())
            .contains(secondRecordId.toString());
    }

    @Test
    @Tag("TE-F036.F01-03")
    void getReport_restrictedToNodesVisibleToRequestingUser() throws Exception {
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID memberId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID visibleNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible Node");
        UUID hiddenNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Hidden Node");
        TestFixtures.grantAuth(jdbc, requesterId, visibleNodeId, "view");

        OffsetDateTime base = OffsetDateTime.of(2024, 6, 12, 9, 0, 0, 0, ZoneOffset.UTC);
        UUID visibleRecordId = insertTimeRecord(
            memberId,
            visibleNodeId,
            base,
            base.plusHours(1),
            "UTC",
            "Visible"
        );
        UUID hiddenRecordId = insertTimeRecord(
            memberId,
            hiddenNodeId,
            base.plusHours(2),
            base.plusHours(3),
            "UTC",
            "Hidden"
        );

        MvcResult result = mvc.perform(get("/api/v1/reports")
                        .param("mode", "detailed")
                        .param("from", "2024-06-12")
                        .param("to", "2024-06-12")
                        .with(TestSecurityHelper.authenticatedAs(requesterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailed[?(@.id == '%s' && @.nodeId == '%s')]"
                    .formatted(visibleRecordId, visibleNodeId)).isNotEmpty())
                .andExpect(jsonPath("$.detailed[?(@.id == '%s')]"
                    .formatted(hiddenRecordId)).isEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
            .contains(visibleNodeId.toString())
            .contains(visibleRecordId.toString())
            .doesNotContain(hiddenNodeId.toString())
            .doesNotContain(hiddenRecordId.toString());
    }

    @Test
    @Tag("TE-F036.F02-01")
    void getReport_detailedMode_overlappingEntries_overlapFlagSet() throws Exception {
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID memberId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Task");
        TestFixtures.grantAuth(jdbc, requesterId, TestFixtures.ROOT_NODE_ID, "view");

        OffsetDateTime base = OffsetDateTime.of(2024, 6, 13, 9, 0, 0, 0, ZoneOffset.UTC);
        UUID firstRecordId = insertTimeRecord(
            memberId,
            nodeId,
            base,
            base.plusHours(2),
            "UTC",
            null
        );
        UUID secondRecordId = insertTimeRecord(
            memberId,
            nodeId,
            base.plusHours(1),
            base.plusHours(3),
            "UTC",
            null
        );

        mvc.perform(get("/api/v1/reports")
                        .param("mode", "detailed")
                        .param("from", "2024-06-13")
                        .param("to", "2024-06-13")
                        .with(TestSecurityHelper.authenticatedAs(requesterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailed", hasSize(2)))
                .andExpect(jsonPath("$.detailed[?(@.id == '%s' && @.overlapping == true)]"
                    .formatted(firstRecordId)).isNotEmpty())
                .andExpect(jsonPath("$.detailed[?(@.id == '%s' && @.overlapping == true)]"
                    .formatted(secondRecordId)).isNotEmpty());
    }

    @Test
    @Tag("TE-F036.F02-02")
    void getReport_detailedMode_gapBetweenConsecutiveEntries_gapFlagSet() throws Exception {
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID memberId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Task");
        TestFixtures.grantAuth(jdbc, requesterId, TestFixtures.ROOT_NODE_ID, "view");

        OffsetDateTime base = OffsetDateTime.of(2024, 6, 14, 9, 0, 0, 0, ZoneOffset.UTC);
        UUID olderRecordId = insertTimeRecord(
            memberId,
            nodeId,
            base,
            base.plusHours(1),
            "UTC",
            null
        );
        UUID newerRecordId = insertTimeRecord(
            memberId,
            nodeId,
            base.plusHours(2),
            base.plusHours(3),
            "UTC",
            null
        );

        mvc.perform(get("/api/v1/reports")
                        .param("mode", "detailed")
                        .param("from", "2024-06-14")
                        .param("to", "2024-06-14")
                        .with(TestSecurityHelper.authenticatedAs(requesterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailed", hasSize(2)))
                .andExpect(jsonPath("$.detailed[?(@.id == '%s' && @.hasGapBefore == true)]"
                    .formatted(newerRecordId)).isNotEmpty())
                .andExpect(jsonPath("$.detailed[?(@.id == '%s' && @.hasGapBefore == true)]"
                    .formatted(olderRecordId)).isEmpty());
    }

    @Test
    @Tag("TE-F038.F01-01")
    void exportCsv_returnsFileDownloadWithCsvContentType() throws Exception {
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID memberId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Task");
        TestFixtures.grantAuth(jdbc, requesterId, TestFixtures.ROOT_NODE_ID, "view");

        OffsetDateTime startedAt = OffsetDateTime.of(2024, 6, 15, 8, 0, 0, 0, ZoneOffset.UTC);
        insertTimeRecord(memberId, nodeId, startedAt, startedAt.plusHours(1), "UTC", "CSV row");

        MvcResult result = mvc.perform(get("/api/v1/reports/export")
                        .param("mode", "summary")
                        .param("from", "2024-06-15")
                        .param("to", "2024-06-15")
                        .with(TestSecurityHelper.authenticatedAs(requesterId)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isNotBlank();
    }

    @Test
    @Tag("TE-F052.F01-01")
    void getMemberSummaries_returnsAggregatedPerMemberPerBucket() throws Exception {
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID firstMemberId = TestFixtures.insertUserWithProfile(jdbc, "Alice");
        UUID secondMemberId = TestFixtures.insertUserWithProfile(jdbc, "Bob");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");
        TestFixtures.grantAuth(jdbc, requesterId, TestFixtures.ROOT_NODE_ID, "view");

        OffsetDateTime base = OffsetDateTime.of(2024, 6, 16, 8, 0, 0, 0, ZoneOffset.UTC);
        insertTimeRecord(firstMemberId, nodeId, base, base.plusHours(2), "UTC", "Alice work");
        insertTimeRecord(secondMemberId, nodeId, base.plusHours(1), base.plusHours(2), "UTC", "Bob work");

        MvcResult result = mvc.perform(get("/api/v1/reports/members")
                        .param("interval", "day")
                        .param("from", "2024-06-16")
                        .param("to", "2024-06-16")
                        .with(TestSecurityHelper.authenticatedAs(requesterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.userId == '%s' && @.buckets[0].totalSeconds == 7200)]"
                    .formatted(firstMemberId)).isNotEmpty())
                .andExpect(jsonPath("$[?(@.userId == '%s' && @.buckets[0].totalSeconds == 3600)]"
                    .formatted(secondMemberId)).isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
            .contains("buckets")
            .doesNotContain("startedAt")
            .doesNotContain("endedAt");
    }

    @Test
    @Tag("TE-F052.F01-02")
    void getMemberSummaries_hasDataQualityIssues_trueWhenOverlapExists() throws Exception {
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID memberId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");
        TestFixtures.grantAuth(jdbc, requesterId, TestFixtures.ROOT_NODE_ID, "view");

        OffsetDateTime base = OffsetDateTime.of(2024, 6, 17, 9, 0, 0, 0, ZoneOffset.UTC);
        insertTimeRecord(memberId, nodeId, base, base.plusHours(2), "UTC", null);
        insertTimeRecord(memberId, nodeId, base.plusHours(1), base.plusHours(3), "UTC", null);

        mvc.perform(get("/api/v1/reports/members")
                        .param("interval", "day")
                        .param("from", "2024-06-17")
                        .param("to", "2024-06-17")
                        .with(TestSecurityHelper.authenticatedAs(requesterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == '%s' && @.buckets[0].hasDataQualityIssues == true)]"
                    .formatted(memberId)).isNotEmpty());
    }

    @Test
    @Tag("TE-F052.F01-03")
    void getMemberSummaries_doesNotExposeIndividualEntryDetails() throws Exception {
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID memberId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");
        TestFixtures.grantAuth(jdbc, requesterId, TestFixtures.ROOT_NODE_ID, "view");

        OffsetDateTime startedAt = OffsetDateTime.of(2024, 6, 18, 9, 0, 0, 0, ZoneOffset.UTC);
        insertTimeRecord(memberId, nodeId, startedAt, startedAt.plusHours(1), "Europe/Zurich", "Private");

        MvcResult result = mvc.perform(get("/api/v1/reports/members")
                        .param("interval", "day")
                        .param("from", "2024-06-18")
                        .param("to", "2024-06-18")
                        .with(TestSecurityHelper.authenticatedAs(requesterId)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
            .doesNotContain("startedAt")
            .doesNotContain("endedAt")
            .doesNotContain("timezone")
            .doesNotContain("description")
            .doesNotContain("overlapping")
            .doesNotContain("hasGapBefore");
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
