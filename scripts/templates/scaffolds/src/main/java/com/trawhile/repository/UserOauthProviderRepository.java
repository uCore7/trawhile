package com.trawhile.repository;

import com.trawhile.domain.UserOauthProvider;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserOauthProviderRepository extends ListCrudRepository<UserOauthProvider, UUID> {

    Optional<UserOauthProvider> findByProviderAndSubject(String provider, String subject);

    List<UserOauthProvider> findByProfileId(UUID profileId);
}
