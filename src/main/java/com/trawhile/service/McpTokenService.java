package com.trawhile.service;

import com.trawhile.repository.McpTokenRepository;
import com.trawhile.repository.SecurityEventRepository;
import com.trawhile.sse.SseDispatcher;
import com.trawhile.sse.SseEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * MCP access token management. SR-065–SR-068.
 *
 * Token generation:
 *   1. Generate 32 random bytes → hex string (64 chars) — the raw token, shown once.
 *   2. SHA-256 hash the raw token → store only the hash.
 *   3. Return raw token + McpToken record to caller.
 *
 * Token authentication (SR-069):
 *   1. Hash the incoming Bearer token with SHA-256.
 *   2. Look up by token_hash.
 *   3. Check revokedAt IS NULL and expiresAt IS NULL OR expiresAt > NOW().
 *   4. Update last_used_at.
 *   All steps logged to security_events.
 */
@Service
@Transactional
public class McpTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final McpTokenRepository mcpTokenRepository;
    private final SecurityEventRepository securityEventRepository;
    private final SseDispatcher sseDispatcher;

    public McpTokenService(McpTokenRepository mcpTokenRepository,
                           SecurityEventRepository securityEventRepository,
                           SseDispatcher sseDispatcher) {
        this.mcpTokenRepository = mcpTokenRepository;
        this.securityEventRepository = securityEventRepository;
        this.sseDispatcher = sseDispatcher;
    }

    // TODO: implement listOwnTokens(userId)
    // TODO: implement generateToken(userId, label, expiresAt) → returns [rawToken, McpToken]
    // TODO: implement revokeToken(tokenId, userId) — owns-check; log MCP_TOKEN_REVOKED
    // TODO: implement adminListAllTokens() — System Admin only
    // TODO: implement adminRevokeToken(tokenId) — System Admin; log MCP_TOKEN_REVOKED; dispatch MCP_TOKEN_REVOKED SSE to owner
    // TODO: implement authenticate(rawToken) — used by MCP endpoint filter; log MCP_TOKEN_USED

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256Hex(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
