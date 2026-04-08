package com.trawhile.repository;

import com.trawhile.domain.PendingMembership;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface PendingMembershipRepository extends ListCrudRepository<PendingMembership, UUID> {

    Optional<PendingMembership> findByEmail(String email);
}
