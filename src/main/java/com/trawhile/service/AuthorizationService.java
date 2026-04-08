package com.trawhile.service;

import com.trawhile.domain.AuthLevel;
import com.trawhile.repository.AuthorizationQueries;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Thin wrapper over AuthorizationQueries. Used by all services for authorization guards. */
@Service
public class AuthorizationService {

    private final AuthorizationQueries queries;

    public AuthorizationService(AuthorizationQueries queries) {
        this.queries = queries;
    }

    public void requireView(UUID userId, UUID nodeId) {
        require(userId, nodeId, AuthLevel.VIEW);
    }

    public void requireTrack(UUID userId, UUID nodeId) {
        require(userId, nodeId, AuthLevel.TRACK);
    }

    public void requireAdmin(UUID userId, UUID nodeId) {
        require(userId, nodeId, AuthLevel.ADMIN);
    }

    public boolean hasView(UUID userId, UUID nodeId) {
        return queries.hasAuthorization(userId, nodeId, AuthLevel.VIEW);
    }

    public boolean hasAdmin(UUID userId, UUID nodeId) {
        return queries.hasAuthorization(userId, nodeId, AuthLevel.ADMIN);
    }

    private void require(UUID userId, UUID nodeId, AuthLevel level) {
        if (!queries.hasAuthorization(userId, nodeId, level)) {
            throw new AccessDeniedException("Insufficient authorization on node " + nodeId);
        }
    }
}
