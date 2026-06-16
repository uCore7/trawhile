package com.trawhile.port.outbound.persistence;

import java.util.UUID;

public interface UserActivationPort {

    void insertActiveUser(UUID id, String displayName, String email);

    void refreshEmail(UUID userId, String email);
}
