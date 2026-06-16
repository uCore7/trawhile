package com.trawhile.port.outbound.persistence;

import java.util.UUID;

public interface NodeAuthorizationPersistencePort {

    void deleteAllForUser(UUID userId);
}
