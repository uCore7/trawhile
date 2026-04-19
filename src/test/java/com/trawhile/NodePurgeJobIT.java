package com.trawhile;

import com.trawhile.config.TrawhileConfig;
import com.trawhile.domain.PurgeJob;
import com.trawhile.lifecycle.NodeDeletionJob;
import com.trawhile.repository.PurgeJobRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NodePurgeJobIT extends BaseIT {

    @Autowired
    private NodeDeletionJob nodeDeletionJob;

    @Autowired
    private PurgeJobRepository purgeJobRepository;

    @Autowired
    private TrawhileConfig trawhileConfig;

    @Test
    @Tag("TE-F050.F03-01")
    void nodePurgeJob_triggeredAfterActivityPurge_setsStatusActiveWithCombinedCutoff() {
        jdbc.update(
            """
                UPDATE purge_jobs
                SET status = 'idle',
                    cutoff_date = ?,
                    started_at = NOW() - INTERVAL '5 minutes',
                    completed_at = NOW(),
                    last_updated_at = NOW()
                WHERE job_type = 'activity'
                """,
            currentDateMinusYears(trawhileConfig.getRetentionYears())
        );

        nodeDeletionJob.trigger();

        PurgeJobState job = loadPurgeJobState("node");

        assertThat(job.status()).isEqualTo("active");
        assertThat(job.cutoffDate())
            .isEqualTo(currentDateMinusYears(
                trawhileConfig.getRetentionYears() + trawhileConfig.getNodeRetentionExtraYears()
            ));
    }

    @Test
    @Tag("TE-F050.F04-01")
    void nodePurgeJob_deletesDeactivatedLeafNodesOlderThanCutoffWithNoReferences() {
        OffsetDateTime staleDeactivatedAt = historicalTimestampYearsAgo(
            trawhileConfig.getRetentionYears() + trawhileConfig.getNodeRetentionExtraYears()
        ).minusDays(1);

        UUID deletableLeafId = insertDeactivatedLeafNode("Deletable Leaf", staleDeactivatedAt);
        UUID retainedLeafId = insertDeactivatedLeafNode("Referenced Leaf", staleDeactivatedAt.minusHours(2));

        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Node Retention Member");
        TestFixtures.insertTimeRecord(
            jdbc,
            userId,
            retainedLeafId,
            OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).withNano(0),
            OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).plusHours(2).withNano(0)
        );

        assertThat(countRowsById("nodes", deletableLeafId)).isOne();
        assertThat(countRowsById("nodes", retainedLeafId)).isOne();

        nodeDeletionJob.trigger();
        LocalDate cutoffDate = loadPurgeJobState("node").cutoffDate();
        runNodeDeletionBatches(cutoffDate);

        assertThat(countRowsById("nodes", deletableLeafId))
            .as("deactivated leaf nodes older than the node purge cutoff and without references must be deleted")
            .isZero();
        assertThat(countRowsById("nodes", retainedLeafId))
            .as("a deactivated node with remaining time_records references must be retained")
            .isOne();
    }

    @Test
    @Tag("TE-F050.F04-02")
    void nodePurgeJob_idempotent_resumesFromStoredCutoffOnRestart() {
        LocalDate storedCutoff = currentDateMinusYears(
            trawhileConfig.getRetentionYears() + trawhileConfig.getNodeRetentionExtraYears() + 1
        );
        OffsetDateTime beforeStoredCutoff = storedCutoff.atStartOfDay().atOffset(ZoneOffset.UTC).minusDays(1);
        OffsetDateTime afterStoredCutoff = storedCutoff.atStartOfDay().atOffset(ZoneOffset.UTC).plusDays(1);

        UUID deletableLeafId = insertDeactivatedLeafNode("Restart Deletable Leaf", beforeStoredCutoff);
        UUID retainedLeafId = insertDeactivatedLeafNode("Restart Retained Leaf", afterStoredCutoff);

        setActivePurgeJob("node", storedCutoff);

        PurgeJob activeJob = purgeJobRepository.findByJobType("node").orElseThrow();
        assertThat(activeJob.cutoffDate()).isEqualTo(storedCutoff);

        nodeDeletionJob.resume(activeJob);
        runNodeDeletionBatches(storedCutoff);

        PurgeJobState afterFirstRun = loadPurgeJobState("node");
        assertThat(afterFirstRun.cutoffDate())
            .as("restart must continue with the stored node cutoff_date")
            .isEqualTo(storedCutoff);
        assertThat(countRowsById("nodes", deletableLeafId))
            .as("nodes older than the stored cutoff_date must be removed on resume")
            .isZero();
        assertThat(countRowsById("nodes", retainedLeafId))
            .as("nodes newer than the stored cutoff_date must be retained on resume")
            .isOne();

        runNodeDeletionBatches(storedCutoff);

        PurgeJobState afterSecondRun = loadPurgeJobState("node");
        assertThat(afterSecondRun.cutoffDate())
            .as("a second idempotent pass must not change the stored cutoff_date")
            .isEqualTo(storedCutoff);
        assertThat(countRowsById("nodes", deletableLeafId))
            .as("the already deleted node must remain absent on the second run")
            .isZero();
        assertThat(countRowsById("nodes", retainedLeafId))
            .as("the retained node must remain unchanged on the second run")
            .isOne();
    }

    private UUID insertDeactivatedLeafNode(String name, OffsetDateTime deactivatedAt) {
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, name);
        jdbc.update(
            "UPDATE nodes SET is_active = false, deactivated_at = ? WHERE id = ?",
            deactivatedAt,
            nodeId
        );
        return nodeId;
    }

    private void runNodeDeletionBatches(LocalDate cutoffDate) {
        assertThat(cutoffDate).isNotNull();

        for (int attempt = 0; attempt < 10; attempt++) {
            int deleted = nodeDeletionJob.deleteLeafBatch(cutoffDate);
            if (deleted == 0) {
                return;
            }
        }

        throw new AssertionError("node purge did not quiesce within 10 batch iterations");
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
