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
 * Activity purge job — chunked DELETEs in batches of 1000.
 * Triggered on the configured purge schedule. Also resumes on startup if status = 'active'.
 *
 * Loop:
 *   DELETE FROM time_records WHERE started_at < :cutoff LIMIT 1000  → deletedRecords
 *   DELETE FROM requests    WHERE created_at  < :cutoff LIMIT 1000  → deletedRequests
 *   UPDATE purge_jobs SET deleted_counts = ..., last_updated_at = NOW()
 *   COMMIT (REQUIRES_NEW)
 *   if deletedRecords + deletedRequests == 0: break
 * SET status = 'idle', completed_at = NOW()
 * COMMIT
 */
@Component
public class ActivityPurgeJob implements PurgeJobCoordinator.Resumable {

    private static final Logger log = LoggerFactory.getLogger(ActivityPurgeJob.class);
    private static final String JOB_TYPE = "activity";
    private static final String SECURITY_EVENT_TYPE = "ACTIVITY_PURGE_EXECUTED";
    private static final int BATCH_SIZE = 1000;

    private final PurgeJobRepository purgeJobRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final SecurityEventService securityEventService;
    private final TrawhileConfig trawhileConfig;
    private final ObjectProvider<ActivityPurgeJob> selfProvider;
    private final Counter deletedTotalCounter;
    private final Counter failureCounter;
    private final AtomicLong lastCompletedSeconds;

    public ActivityPurgeJob(PurgeJobRepository purgeJobRepository,
                            NamedParameterJdbcTemplate jdbc,
                            SecurityEventService securityEventService,
                            TrawhileConfig trawhileConfig,
                            ObjectProvider<ActivityPurgeJob> selfProvider,
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
                UPDATE purge_jobs
                SET status = 'active',
                    cutoff_date = (CURRENT_DATE - (:retentionYears * INTERVAL '1 year'))::date,
                    started_at = NOW(),
                    completed_at = NULL,
                    deleted_counts = '{"timeRecords":0,"requests":0}'::jsonb,
                    last_updated_at = NOW()
                WHERE job_type = :jobType
                  AND status = 'idle'
                """,
            new MapSqlParameterSource()
                .addValue("jobType", JOB_TYPE)
                .addValue("retentionYears", trawhileConfig.getRetentionYears())
        );
        if (updated == 1) {
            log.info("Activated activity purge job");
        }
    }

    @Override
    public void resume(PurgeJob job) {
        log.info("Resuming activity purge (cutoff: {})", job.cutoffDate());
        if (job.cutoffDate() == null) {
            log.warn("Cannot resume activity purge without a stored cutoff_date");
            return;
        }

        try {
            while (true) {
                int[] deletedCounts = self().deleteBatch(job.cutoffDate());
                if (deletedCounts[0] == 0 && deletedCounts[1] == 0) {
                    return;
                }
            }
        } catch (RuntimeException ex) {
            failureCounter.increment();
            log.error("Activity purge failed", ex);
            throw ex;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int[] deleteBatch(LocalDate cutoff) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("cutoffDate", cutoff)
            .addValue("batchSize", BATCH_SIZE);

        int deletedRecords = countDeletedRows(
            """
                WITH deleted AS (
                    DELETE FROM time_records
                    WHERE id IN (
                        SELECT id
                        FROM time_records
                        WHERE started_at < :cutoffDate
                        ORDER BY started_at, id
                        LIMIT :batchSize
                    )
                    RETURNING 1
                )
                SELECT COUNT(*) FROM deleted
                """,
            parameters
        );
        int deletedRequests = countDeletedRows(
            """
                WITH deleted AS (
                    DELETE FROM requests
                    WHERE id IN (
                        SELECT id
                        FROM requests
                        WHERE created_at < :cutoffDate
                        ORDER BY created_at, id
                        LIMIT :batchSize
                    )
                    RETURNING 1
                )
                SELECT COUNT(*) FROM deleted
                """,
            parameters
        );

        if (deletedRecords == 0 && deletedRequests == 0) {
            completeJob(cutoff);
            return new int[] {0, 0};
        }

        jdbc.update(
            """
                UPDATE purge_jobs
                SET deleted_counts = jsonb_build_object(
                        'timeRecords', COALESCE((deleted_counts ->> 'timeRecords')::integer, 0) + :deletedRecords,
                        'requests', COALESCE((deleted_counts ->> 'requests')::integer, 0) + :deletedRequests
                    ),
                    last_updated_at = NOW()
                WHERE job_type = :jobType
                """,
            new MapSqlParameterSource()
                .addValue("jobType", JOB_TYPE)
                .addValue("deletedRecords", deletedRecords)
                .addValue("deletedRequests", deletedRequests)
        );
        deletedTotalCounter.increment(deletedRecords + deletedRequests);
        return new int[] {deletedRecords, deletedRequests};
    }

    private ActivityPurgeJob self() {
        return selfProvider.getObject();
    }

    private int countDeletedRows(String sql, MapSqlParameterSource parameters) {
        Integer count = jdbc.queryForObject(sql, parameters, Integer.class);
        return count == null ? 0 : count;
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
                SELECT COALESCE((deleted_counts ->> 'timeRecords')::integer, 0) AS time_records,
                       COALESCE((deleted_counts ->> 'requests')::integer, 0) AS requests
                FROM purge_jobs
                WHERE job_type = :jobType
                """,
            new MapSqlParameterSource("jobType", JOB_TYPE),
            (rs, rowNum) -> Map.of(
                "timeRecords", rs.getInt("time_records"),
                "requests", rs.getInt("requests")
            )
        );

        lastCompletedSeconds.set(OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond());
        securityEventService.log(
            SECURITY_EVENT_TYPE,
            null,
            Map.of(
                "cutoffDate", cutoff.toString(),
                "deletedCounts", deletedCounts == null ? Map.of("timeRecords", 0, "requests", 0) : deletedCounts
            )
        );
    }
}
