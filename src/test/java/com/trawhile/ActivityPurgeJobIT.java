package com.trawhile;

import com.trawhile.config.TrawhileConfig;
import com.trawhile.domain.PurgeJob;
import com.trawhile.lifecycle.ActivityPurgeJob;
import com.trawhile.repository.PurgeJobRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityPurgeJobIT extends BaseIT {

    @Autowired
    private ActivityPurgeJob activityPurgeJob;

    @Autowired
    private PurgeJobRepository purgeJobRepository;

    @Autowired
    private TrawhileConfig trawhileConfig;

    @Test
    @Tag("TE-F050.F01-01")
    void activityPurgeJob_setsStatusActiveAndCutoffDate() {
        activityPurgeJob.trigger();

        PurgeJobState job = loadPurgeJobState("activity");

        assertThat(job.status()).isEqualTo("active");
        assertThat(job.cutoffDate())
            .isEqualTo(currentDateMinusYears(trawhileConfig.getRetentionYears()));
    }

    @Test
    @Tag("TE-F050.F02-01")
    void activityPurgeJob_deletesEntriesAndRequestsOlderThanCutoff() {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Retention Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Historic Activity");

        OffsetDateTime beforeCutoff = historicalTimestampYearsAgo(trawhileConfig.getRetentionYears())
            .minusDays(1);
        OffsetDateTime afterCutoff = historicalTimestampYearsAgo(trawhileConfig.getRetentionYears())
            .plusDays(1);

        UUID staleTimeRecordId = TestFixtures.insertTimeRecord(
            jdbc,
            userId,
            nodeId,
            beforeCutoff,
            beforeCutoff.plusHours(2)
        );
        UUID retainedTimeRecordId = TestFixtures.insertTimeRecord(
            jdbc,
            userId,
            nodeId,
            afterCutoff,
            afterCutoff.plusHours(2)
        );
        UUID staleRequestId = insertRequest(userId, nodeId, beforeCutoff.plusHours(1));

        assertThat(countRowsById("time_records", staleTimeRecordId)).isOne();
        assertThat(countRowsById("time_records", retainedTimeRecordId)).isOne();
        assertThat(countRowsById("requests", staleRequestId)).isOne();

        activityPurgeJob.trigger();
        LocalDate cutoffDate = loadPurgeJobState("activity").cutoffDate();
        runActivityBatches(cutoffDate);

        assertThat(countRowsById("time_records", staleTimeRecordId))
            .as("time_records rows before the purge cutoff must be deleted")
            .isZero();
        assertThat(countRowsById("time_records", retainedTimeRecordId))
            .as("time_records rows after the purge cutoff must be retained")
            .isOne();
        assertThat(countRowsById("requests", staleRequestId))
            .as("requests rows before the purge cutoff must be deleted")
            .isZero();
    }

    @Test
    @Tag("TE-F050.F02-02")
    void activityPurgeJob_idempotent_resumesFromStoredCutoffDateOnRestart() {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Resume Member");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Resume Activity");

        LocalDate storedCutoff = currentDateMinusYears(trawhileConfig.getRetentionYears() + 1);
        OffsetDateTime beforeStoredCutoff = storedCutoff.atStartOfDay().atOffset(ZoneOffset.UTC).minusDays(1);
        OffsetDateTime afterStoredCutoff = storedCutoff.atStartOfDay().atOffset(ZoneOffset.UTC).plusDays(1);

        UUID staleTimeRecordId = TestFixtures.insertTimeRecord(
            jdbc,
            userId,
            nodeId,
            beforeStoredCutoff,
            beforeStoredCutoff.plusHours(2)
        );
        UUID retainedTimeRecordId = TestFixtures.insertTimeRecord(
            jdbc,
            userId,
            nodeId,
            afterStoredCutoff,
            afterStoredCutoff.plusHours(2)
        );
        UUID staleRequestId = insertRequest(userId, nodeId, beforeStoredCutoff.plusHours(1));

        setActivePurgeJob("activity", storedCutoff);

        PurgeJob activeJob = purgeJobRepository.findByJobType("activity").orElseThrow();
        assertThat(activeJob.cutoffDate()).isEqualTo(storedCutoff);

        activityPurgeJob.resume(activeJob);
        runActivityBatches(storedCutoff);

        PurgeJobState jobAfterResume = loadPurgeJobState("activity");
        assertThat(jobAfterResume.cutoffDate())
            .as("restart must keep using the stored cutoff_date instead of recalculating it")
            .isEqualTo(storedCutoff);
        assertThat(countRowsById("time_records", staleTimeRecordId))
            .as("rows before the stored cutoff_date must be deleted on resume")
            .isZero();
        assertThat(countRowsById("time_records", retainedTimeRecordId))
            .as("rows after the stored cutoff_date must be retained on resume")
            .isOne();
        assertThat(countRowsById("requests", staleRequestId))
            .as("requests before the stored cutoff_date must be deleted on resume")
            .isZero();
    }

    private void runActivityBatches(LocalDate cutoffDate) {
        assertThat(cutoffDate).isNotNull();

        for (int attempt = 0; attempt < 10; attempt++) {
            int[] deletedCounts = activityPurgeJob.deleteBatch(cutoffDate);
            assertThat(deletedCounts).hasSize(2);
            if (deletedCounts[0] == 0 && deletedCounts[1] == 0) {
                return;
            }
        }

        throw new AssertionError("activity purge did not quiesce within 10 batch iterations");
    }

    private void setActivePurgeJob(String jobType, LocalDate cutoffDate) {
        jdbc.update(
            """
                UPDATE purge_jobs
                SET status = 'active',
                    cutoff_date = ?,
                    started_at = NOW(),
                    completed_at = NULL,
                    deleted_counts = NULL,
                    last_updated_at = NOW()
                WHERE job_type = ?
                """,
            cutoffDate,
            jobType
        );
    }

    private UUID insertRequest(UUID requesterId, UUID nodeId, OffsetDateTime createdAt) {
        UUID requestId = UUID.randomUUID();
        jdbc.update(
            """
                INSERT INTO requests (id, requester_id, node_id, template, body, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
            requestId,
            requesterId,
            nodeId,
            "free_text",
            "Retention request",
            "open",
            createdAt
        );
        return requestId;
    }

    private int countRowsById(String tableName, UUID id) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + tableName + " WHERE id = ?",
            Integer.class,
            id
        );
    }

    private LocalDate currentDateMinusYears(int years) {
        return jdbc.queryForObject(
            "SELECT (CURRENT_DATE - (? * INTERVAL '1 year'))::date",
            LocalDate.class,
            years
        );
    }

    private OffsetDateTime historicalTimestampYearsAgo(int years) {
        return OffsetDateTime.now(ZoneOffset.UTC)
            .minusYears(years)
            .withHour(12)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);
    }

    private PurgeJobState loadPurgeJobState(String jobType) {
        return jdbc.queryForObject(
            "SELECT status, cutoff_date FROM purge_jobs WHERE job_type = ?",
            (rs, rowNum) -> new PurgeJobState(
                rs.getString("status"),
                rs.getObject("cutoff_date", LocalDate.class)
            ),
            jobType
        );
    }

    private record PurgeJobState(String status, LocalDate cutoffDate) {
    }
}
