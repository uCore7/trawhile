package com.trawhile.config;

import com.trawhile.repository.PendingInvitationRepository;
import com.trawhile.repository.UserRepository;
import com.trawhile.repository.NodeAuthorizationRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    private final PendingInvitationRepository pendingInvitationRepository;
    private final NodeAuthorizationRepository nodeAuthorizationRepository;
    private final UserRepository userRepository;

    public SchedulingConfig(PendingInvitationRepository pendingInvitationRepository,
                            NodeAuthorizationRepository nodeAuthorizationRepository,
                            UserRepository userRepository) {
        this.pendingInvitationRepository = pendingInvitationRepository;
        this.nodeAuthorizationRepository = nodeAuthorizationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Daily purge of expired pending invitations (SR-009a).
     * For each expired invitation: deletes node_authorizations, pending_invitations, users
     * (pending users have no time_entries, so users row is always deleted).
     * Cron configurable via app.pending-membership-purge-cron (default: 02:00 UTC daily).
     */
    @Scheduled(cron = "${app.pending-membership-purge-cron:0 0 2 * * *}")
    @Transactional
    public void purgeExpiredInvitations() {
        var expired = pendingInvitationRepository.findAllByExpiresAtBefore(OffsetDateTime.now());
        for (var invitation : expired) {
            // TODO: implement full cleanup — delete node_authorizations, pending_invitations, users
        }
    }
}
