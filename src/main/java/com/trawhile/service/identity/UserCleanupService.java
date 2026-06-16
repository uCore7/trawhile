package com.trawhile.service.identity;

import com.trawhile.port.outbound.persistence.NodeAuthorizationPersistencePort;
import com.trawhile.port.outbound.persistence.PendingInvitationLookupPort;
import com.trawhile.port.outbound.persistence.UserActivationPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserCleanupService {

    private final PendingInvitationLookupPort pendingInvitationLookupPort;
    private final NodeAuthorizationPersistencePort nodeAuthorizationPersistencePort;
    private final UserActivationPort userActivationPort;

    public UserCleanupService(
            PendingInvitationLookupPort pendingInvitationLookupPort,
            NodeAuthorizationPersistencePort nodeAuthorizationPersistencePort,
            UserActivationPort userActivationPort) {
        this.pendingInvitationLookupPort = pendingInvitationLookupPort;
        this.nodeAuthorizationPersistencePort = nodeAuthorizationPersistencePort;
        this.userActivationPort = userActivationPort;
    }

    /**
     * SR-07-F01.F02 pending-user cleanup. Idempotent (SR-07-F01.C02):
     * invoking on an already-removed pending user is a no-op.
     *
     * <p>Does not emit audit events. The trigger-specific caller emits
     * invitation_withdrawn, invitation_expired, or equivalent vocabulary.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void cleanupPendingUser(UUID userId) {
        nodeAuthorizationPersistencePort.deleteAllForUser(userId);
        pendingInvitationLookupPort.deleteByUserId(userId);
        userActivationPort.deleteUserById(userId);
    }
}
