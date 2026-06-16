package com.trawhile.port.outbound.persistence;

import java.util.Optional;
import java.util.UUID;

public interface PendingInvitationLookupPort {

    Optional<UUID> findUserIdByEmail(String email);

    void deleteByUserId(UUID userId);
}
