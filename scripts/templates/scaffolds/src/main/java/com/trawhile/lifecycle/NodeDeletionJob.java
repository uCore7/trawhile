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
    private static final int BATCH_SIZE = 100;

    private final PurgeJobRepository purgeJobRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final SecurityEventService securityEventService;

    public NodeDeletionJob(PurgeJobRepository purgeJobRepository,
                           NamedParameterJdbcTemplate jdbc,
                           SecurityEventService securityEventService) {
        this.purgeJobRepository = purgeJobRepository;
        this.jdbc = jdbc;
        this.securityEventService = securityEventService;
    }

    @Scheduled(cron = "${trawhile.purge-cron:0 59 23 * * *}", zone = "${trawhile.timezone:UTC}")
    public void trigger() {
        // TODO: check whether the activity purge for the configured schedule completed
        // and then set cutoff_date and status = 'active' before running
    }

    @Override
    public void resume(PurgeJob job) {
        log.info("Resuming node deletion job (cutoff: {})", job.cutoffDate());
        // TODO: implement iterative bottom-up deletion loop using job.cutoffDate()
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteLeafBatch(java.time.LocalDate cutoff) {
        // TODO: find and delete eligible leaf nodes, return count
        return 0;
    }
}
