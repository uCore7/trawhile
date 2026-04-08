package com.trawhile.repository;

import com.trawhile.domain.UserProfile;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends ListCrudRepository<UserProfile, UUID> {

    Optional<UserProfile> findByUserId(UUID userId);
}
