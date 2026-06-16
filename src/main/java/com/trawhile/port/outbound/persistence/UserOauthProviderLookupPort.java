package com.trawhile.port.outbound.persistence;

import java.util.Optional;
import java.util.UUID;

public interface UserOauthProviderLookupPort {

    Optional<UUID> findUserId(String provider, String subject);

    void insert(UUID userId, String provider, String subject);
}
