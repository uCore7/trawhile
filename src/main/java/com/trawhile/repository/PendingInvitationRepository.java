package com.trawhile.repository;

import com.trawhile.domain.PendingInvitation;
import org.springframework.data.repository.ListCrudRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingInvitationRepository extends ListCrudRepository<PendingInvitation, UUID> {

    Optional<PendingInvitation> findByEmail(String email);

    Optional<PendingInvitation> findByUserId(UUID userId);

    /** Returns all expired invitations for bulk cleanup (SR-009a). */
    List<PendingInvitation> findAllByExpiresAtBefore(OffsetDateTime cutoff);
}
