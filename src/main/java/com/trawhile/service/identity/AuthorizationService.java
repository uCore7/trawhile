package com.trawhile.service.identity;

import com.trawhile.port.outbound.persistence.AdminAuthorizationPort;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    private final AdminAuthorizationPort adminAuthorizationPort;

    public AuthorizationService(AdminAuthorizationPort adminAuthorizationPort) {
        this.adminAuthorizationPort = adminAuthorizationPort;
    }

    public void checkAdminOnRoot(UUID userId) {
        if (!adminAuthorizationPort.hasAdminOnRootNode(userId)) {
            throw new AccessDeniedException("Caller lacks effective admin on root node");
        }
    }
}
