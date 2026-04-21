package com.trawhile.lifecycle;

import com.trawhile.config.TrawhileConfig;
import com.trawhile.domain.PurgeJob;
import com.trawhile.repository.PurgeJobRepository;
import com.trawhile.service.SecurityEventService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Node deletion job — iterative bottom-up loop, batches of 100.
 * Runs after each scheduled activity purge completes.
 *
 * Loop:
 *   Find deactivated nodes WHERE deactivated_at < :cutoff
 *     AND id NOT IN (SELECT parent_id FROM nodes WHERE parent_id IS NOT NULL)  -- leaf only
 *     AND subtree has no remaining time_records
 *     AND subtree has no remaining requests
 *   LIMIT 100
 *   if none found: break
 *   DELETE FROM nodes WHERE id IN (...)  -- cascades to node_authorizations
 *   UPDATE purge_jobs SET deleted_counts = ...
 *   COMMIT (REQUIRES_NEW)
 *
 * Deletion is bottom-up and only considers current leaf nodes, but the implementation must still
 * enforce that no time_records or requests remain anywhere in the candidate subtree.
 */
@Component
public class NodeDeletionJob implements PurgeJobCoordinator.Resumable {

    private static final Logger log = LoggerFactory.getLogger(NodeDeletionJob.class);
    private static final String JOB_TYPE = "node";
    private static final String ACTIVITY_JOB_TYPE = "activity";
    private static final String SECURITY_EVENT_TYPE = "NODE_DELETION_EXECUTED";
    private static final int BATCH_SIZE = 100;

    private final PurgeJobRepository purgeJobRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final SecurityEventService securityEventService;
    private final TrawhileConfig trawhileConfig;
    private final ObjectProvider<NodeDeletionJob> selfProvider;
    private final Counter deletedTotalCounter;
    private final Counter failureCounter;
    private final AtomicLong lastCompletedSeconds;

    public NodeDeletionJob(PurgeJobRepository purgeJobRepository,
                           NamedParameterJdbcTemplate jdbc,
                           SecurityEventService securityEventService,
                           TrawhileConfig trawhileConfig,
                           ObjectProvider<NodeDeletionJob> selfProvider,
                           MeterRegistry meterRegistry) {
        this.purgeJobRepository = purgeJobRepository;
        this.jdbc = jdbc;
        this.securityEventService = securityEventService;
        this.trawhileConfig = trawhileConfig;
        this.selfProvider = selfProvider;
        this.deletedTotalCounter = meterRegistry.counter("trawhile_purge_job_deleted_total", "job_type", JOB_TYPE);
        this.failureCounter = meterRegistry.counter("trawhile_purge_job_failures_total", "job_type", JOB_TYPE);
        this.lastCompletedSeconds = meterRegistry.gauge(
            "trawhile_purge_job_last_completed_seconds",
            io.micrometer.core.instrument.Tags.of("job_type", JOB_TYPE),
            new AtomicLong(0)
        );
    }

    @Scheduled(cron = "${trawhile.purge-cron:0 59 23 * * *}", zone = "${trawhile.timezone:UTC}")
    public void trigger() {
        int updated = jdbc.update(
            """
                UPDATE purge_jobs node
                SET status = 'active',
                    cutoff_date = (CURRENT_DATE - (:retentionYears * INTERVAL '1 year'))::date,
                    started_at = NOW(),
                    completed_at = NULL,
                    deleted_counts = '{"nodes":0}'::jsonb,
                    last_updated_at = NOW()
                WHERE node.job_type = :jobType
                  AND node.status = 'idle'
                  AND EXISTS (
                    SELECT 1
                    FROM purge_jobs activity
                    WHERE activity.job_type = :activityJobType
                      AND activity.status = 'idle'
                      AND activity.completed_at IS NOT NULL
                      AND (node.completed_at IS NULL OR node.completed_at < activity.completed_at)
                  )
                """,
            new MapSqlParameterSource()
                .addValue("jobType", JOB_TYPE)
                .addValue("activityJobType", ACTIVITY_JOB_TYPE)
                .addValue(
                    "retentionYears",
                    trawhileConfig.getRetentionYears() + trawhileConfig.getNodeRetentionExtraYears()
                )
        );
        if (updated == 1) {
            log.info("Activated node deletion job");
        }
    }

    @Override
    public void resume(PurgeJob job) {
        log.info("Resuming node deletion job (cutoff: {})", job.cutoffDate());
        if (job.cutoffDate() == null) {
            log.warn("Cannot resume node deletion job without a stored cutoff_date");
            return;
        }

        try {
            while (true) {
                int deleted = self().deleteLeafBatch(job.cutoffDate());
                if (deleted == 0) {
                    return;
                }
            }
        } catch (RuntimeException ex) {
            failureCounter.increment();
            log.error("Node deletion job failed", ex);
            throw ex;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteLeafBatch(LocalDate cutoff) {
        Integer deleted = jdbc.queryForObject(
            """
                WITH candidate_nodes AS (
                    SELECT n.id
                    FROM nodes n
                    WHERE n.is_active = FALSE
                      AND n.deactivated_at < :cutoffDate
                      AND NOT EXISTS (
                        SELECT 1
                        FROM nodes child
                        WHERE child.parent_id = n.id
                      )
                      AND NOT EXISTS (
                        SELECT 1
                        FROM time_records tr
                        WHERE tr.node_id = n.id
                      )
                      AND NOT EXISTS (
                        SELECT 1
                        FROM requests r
                        WHERE r.node_id = n.id
                      )
                    ORDER BY n.deactivated_at, n.id
                    LIMIT :batchSize
                ),
                deleted AS (
                    DELETE FROM nodes
                    WHERE id IN (SELECT id FROM candidate_nodes)
                    RETURNING 1
                )
                SELECT COUNT(*) FROM deleted
                """,
            new MapSqlParameterSource()
                .addValue("cutoffDate", cutoff)
                .addValue("batchSize", BATCH_SIZE),
            Integer.class
        );

        int deletedCount = deleted == null ? 0 : deleted;
        if (deletedCount == 0) {
            completeJob(cutoff);
            return 0;
        }

        jdbc.update(
            """
                UPDATE purge_jobs
                SET deleted_counts = jsonb_build_object(
                        'nodes', COALESCE((deleted_counts ->> 'nodes')::integer, 0) + :deletedCount
                    ),
                    last_updated_at = NOW()
                WHERE job_type = :jobType
                """,
            new MapSqlParameterSource()
                .addValue("jobType", JOB_TYPE)
                .addValue("deletedCount", deletedCount)
        );
        deletedTotalCounter.increment(deletedCount);
        return deletedCount;
    }

    private NodeDeletionJob self() {
        return selfProvider.getObject();
    }

    private void completeJob(LocalDate cutoff) {
        jdbc.update(
            """
                UPDATE purge_jobs
                SET status = 'idle',
                    completed_at = NOW(),
                    last_updated_at = NOW()
                WHERE job_type = :jobType
                """,
            new MapSqlParameterSource("jobType", JOB_TYPE)
        );

        Map<String, Integer> deletedCounts = jdbc.queryForObject(
            """
                SELECT COALESCE((deleted_counts ->> 'nodes')::integer, 0) AS nodes
                FROM purge_jobs
                WHERE job_type = :jobType
                """,
            new MapSqlParameterSource("jobType", JOB_TYPE),
            (rs, rowNum) -> Map.of("nodes", rs.getInt("nodes"))
        );

        lastCompletedSeconds.set(OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond());
        securityEventService.log(
            SECURITY_EVENT_TYPE,
            null,
            Map.of(
                "cutoffDate", cutoff.toString(),
                "deletedCounts", deletedCounts == null ? Map.of("nodes", 0) : deletedCounts
            )
        );
    }
}
