package com.trawhile.config;

import com.trawhile.repository.PendingMembershipRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    private final PendingMembershipRepository pendingMembershipRepository;

    public SchedulingConfig(PendingMembershipRepository pendingMembershipRepository) {
        this.pendingMembershipRepository = pendingMembershipRepository;
    }

    /**
     * Daily purge of expired pending invitations (GDPR storage limitation — 90 days). SR-009a.
     * Cron configurable via app.pending-membership-purge-cron (default: 02:00 UTC daily).
     */
    @Scheduled(cron = "${app.pending-membership-purge-cron:0 0 2 * * *}")
    @Transactional
    public void purgeExpiredInvitations() {
        pendingMembershipRepository.deleteAllByExpiresAtBefore(OffsetDateTime.now());
    }
}
