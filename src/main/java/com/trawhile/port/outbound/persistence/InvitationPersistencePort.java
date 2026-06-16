package com.trawhile.port.outbound.persistence;

import java.time.Instant;
import java.util.UUID;

public interface InvitationPersistencePort {

    boolean existsNonExpiredPendingInvitationByEmail(String email);

    boolean existsNonAnonymisedUserByEmail(String email);

    CreatedInvitationRow createPendingUserAndInvitation(String email, UUID invitedBy);

    record CreatedInvitationRow(
            UUID id,
            UUID userId,
            String email,
            Instant invitedAt,
            Instant expiresAt) {}
}
