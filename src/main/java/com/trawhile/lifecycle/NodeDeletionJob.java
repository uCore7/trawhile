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
 * Fires one month after each activity purge (Jan 31, etc.).
 *
 * Loop:
 *   Find deactivated nodes WHERE deactivated_at < :cutoff
 *     AND id NOT IN (SELECT parent_id FROM nodes WHERE parent_id IS NOT NULL)  -- leaf only
 *     AND NOT EXISTS (SELECT 1 FROM time_entries WHERE node_id = id)
 *     AND NOT EXISTS (SELECT 1 FROM requests WHERE node_id = id)
 *   LIMIT 100
 *   if none found: break
 *   DELETE FROM nodes WHERE id IN (...)  -- cascades to node_authorizations
 *   UPDATE purge_jobs SET deleted_counts = ...
 *   COMMIT (REQUIRES_NEW)
 *
 * The NOT EXISTS checks are on the node itself (not subtree) because deletion is bottom-up:
 * by the time a node becomes a leaf, its entire subtree has already been deleted.
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

    @Scheduled(cron = "0 59 23 * * *")
    public void trigger() {
        // TODO: check if today is a scheduled node deletion date (Jan 31, etc.)
        // If so, set cutoff_date and status = 'active', then run()
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
