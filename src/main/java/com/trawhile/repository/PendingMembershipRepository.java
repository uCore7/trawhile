package com.trawhile.repository;

import com.trawhile.domain.PendingMembership;
import org.springframework.data.repository.ListCrudRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PendingMembershipRepository extends ListCrudRepository<PendingMembership, UUID> {

    Optional<PendingMembership> findByEmail(String email);

    /** Used by the daily purge task to remove expired invitations (SR-009a). */
    void deleteAllByExpiresAtBefore(OffsetDateTime cutoff);
}
