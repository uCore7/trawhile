package com.trawhile.web;

import com.trawhile.service.NodeService;
import com.trawhile.web.api.AuthorizationsApi;
import com.trawhile.web.dto.GrantAuthorizationRequest;
import com.trawhile.web.dto.NodeAuthorization;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** /api/v1/nodes/{nodeId}/authorizations — grant/revoke/view node authorization assignments. */
@RestController
@RequestMapping("/api/v1")
public class AuthorizationController implements AuthorizationsApi {

    private final NodeService nodeService;

    public AuthorizationController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Override
    public ResponseEntity<Void> grantAuthorization(UUID nodeId, UUID userId, GrantAuthorizationRequest grantAuthorizationRequest) {
        nodeService.grantAuthorization(currentUserId(), nodeId, userId, grantAuthorizationRequest.getAuthorization());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<NodeAuthorization>> listNodeAuthorizations(UUID nodeId) {
        return ResponseEntity.ok(nodeService.listAuthorizations(currentUserId(), nodeId));
    }

    @Override
    public ResponseEntity<Void> revokeAuthorization(UUID nodeId, UUID userId) {
        nodeService.revokeAuthorization(currentUserId(), nodeId, userId);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
