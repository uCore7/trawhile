package com.trawhile.port.outbound.persistence;

import java.util.UUID;

public interface AdminAuthorizationPort {

    boolean hasAdminOnRootNode(UUID userId);
}
