package com.trawhile.lifecycle;

import com.trawhile.domain.PurgeJob;
import com.trawhile.repository.PurgeJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On startup: reads both purge_jobs rows. If either has status = 'active',
 * resumes the corresponding job using the stored cutoff_date.
 */
@Component
public class PurgeJobCoordinator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PurgeJobCoordinator.class);

    private final PurgeJobRepository purgeJobRepository;
    private final ActivityPurgeJob activityPurgeJob;
    private final NodeDeletionJob nodeDeletionJob;

    public PurgeJobCoordinator(PurgeJobRepository purgeJobRepository,
                               ActivityPurgeJob activityPurgeJob,
                               NodeDeletionJob nodeDeletionJob) {
        this.purgeJobRepository = purgeJobRepository;
        this.activityPurgeJob = activityPurgeJob;
        this.nodeDeletionJob = nodeDeletionJob;
    }

    @Override
    public void run(ApplicationArguments args) {
        resume("activity", activityPurgeJob);
        resume("node", nodeDeletionJob);
    }

    private void resume(String jobType, Resumable job) {
        purgeJobRepository.findByJobType(jobType).ifPresent(purgeJob -> {
            if ("active".equals(purgeJob.status())) {
                log.info("Resuming {} purge job (cutoff: {})", jobType, purgeJob.cutoffDate());
                job.resume(purgeJob);
            }
        });
    }

    interface Resumable {
        void resume(PurgeJob job);
    }
}
