package com.trawhile.port.outbound.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationPersistencePort {

    boolean existsNonExpiredPendingInvitationByEmail(String email);

    boolean existsNonAnonymisedUserByEmail(String email);

    CreatedInvitationRow createPendingUserAndInvitation(String email, UUID invitedBy);

    List<InvitationListRow> listAllPendingWithInviterAndGrantCount();

    Optional<ResendableInvitation> findById(UUID invitationId);

    ResendableInvitation refreshExpiresAtToNinetyDaysFromNow(UUID invitationId);

    record CreatedInvitationRow(
            UUID id,
            UUID userId,
            String email,
            Instant invitedAt,
            Instant expiresAt) {}

    record InvitationListRow(
            UUID id,
            String email,
            UUID inviterId,
            String inviterDisplayName,
            Instant invitedAt,
            Instant expiresAt,
            UUID userId,
            int preAssignedGrantCount) {}

    record ResendableInvitation(
            UUID id,
            UUID userId,
            String email,
            Instant invitedAt,
            Instant expiresAt) {}
}
