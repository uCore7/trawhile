package com.trawhile.lifecycle;

import com.trawhile.domain.PurgeJob;
import com.trawhile.repository.PurgeJobRepository;
import com.trawhile.service.SecurityEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activity purge job — chunked DELETEs in batches of 1000.
 * Triggered daily at 23:59. Also resumes on startup if status = 'active'.
 *
 * Loop:
 *   DELETE FROM time_entries WHERE started_at < :cutoff LIMIT 1000  → deletedEntries
 *   DELETE FROM requests    WHERE created_at  < :cutoff LIMIT 1000  → deletedRequests
 *   UPDATE purge_jobs SET deleted_counts = ..., last_updated_at = NOW()
 *   COMMIT (REQUIRES_NEW)
 *   if deletedEntries + deletedRequests == 0: break
 * SET status = 'idle', completed_at = NOW()
 * COMMIT
 */
@Component
public class ActivityPurgeJob implements PurgeJobCoordinator.Resumable {

    private static final Logger log = LoggerFactory.getLogger(ActivityPurgeJob.class);
    private static final int BATCH_SIZE = 1000;

    private final PurgeJobRepository purgeJobRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final SecurityEventService securityEventService;

    public ActivityPurgeJob(PurgeJobRepository purgeJobRepository,
                            NamedParameterJdbcTemplate jdbc,
                            SecurityEventService securityEventService) {
        this.purgeJobRepository = purgeJobRepository;
        this.jdbc = jdbc;
        this.securityEventService = securityEventService;
    }

    @Scheduled(cron = "0 59 23 * * *")
    public void trigger() {
        // TODO: check if today is a scheduled purge date per company settings
        // If so, set cutoff_date and status = 'active', then run()
    }

    @Override
    public void resume(PurgeJob job) {
        // TODO: implement chunked purge loop using job.cutoffDate()
        log.info("Resuming activity purge (cutoff: {})", job.cutoffDate());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int[] deleteBatch(java.time.LocalDate cutoff) {
        // TODO: DELETE FROM time_entries / requests with LIMIT, return [deletedEntries, deletedRequests]
        return new int[]{0, 0};
    }
}
