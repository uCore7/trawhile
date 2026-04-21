package com.trawhile.web;

import com.trawhile.service.McpTokenService;
import com.trawhile.web.api.McpApi;
import com.trawhile.web.dto.GenerateMcpToken201Response;
import com.trawhile.web.dto.GenerateMcpTokenRequest;
import com.trawhile.web.dto.McpToken;
import com.trawhile.web.dto.McpTokenWithOwner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * MCP token management endpoints. SR-F053.F01, SR-F054.F01, SR-F055.F01, SR-F056.F01, SR-F057.F01.
 */
@RestController
@RequestMapping("/api/v1")
public class McpTokenController implements McpApi {

    private final McpTokenService mcpTokenService;

    public McpTokenController(McpTokenService mcpTokenService) {
        this.mcpTokenService = mcpTokenService;
    }

    @Override
    public ResponseEntity<GenerateMcpToken201Response> generateMcpToken(GenerateMcpTokenRequest generateMcpTokenRequest) {
        McpTokenService.GeneratedToken generated = mcpTokenService.generateToken(
            currentUserId(),
            generateMcpTokenRequest.getLabel(),
            generateMcpTokenRequest.getExpiresAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new GenerateMcpToken201Response(generated.rawToken(), generated.mcpToken()));
    }

    @Override
    public ResponseEntity<List<McpToken>> listOwnMcpTokens() {
        return ResponseEntity.ok(mcpTokenService.listOwnTokens(currentUserId()));
    }

    @Override
    public ResponseEntity<Void> revokeMcpToken(UUID tokenId) {
        mcpTokenService.revokeOwn(currentUserId(), tokenId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<McpTokenWithOwner>> listAllMcpTokens() {
        return ResponseEntity.ok(mcpTokenService.listAllTokens(currentUserId()));
    }

    @Override
    public ResponseEntity<Void> adminRevokeMcpToken(UUID tokenId) {
        mcpTokenService.adminRevoke(currentUserId(), tokenId);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
