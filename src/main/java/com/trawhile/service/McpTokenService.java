package com.trawhile.service;

import com.trawhile.exception.EntityNotFoundException;
import com.trawhile.exception.InputValidationException;
import com.trawhile.repository.McpTokenRepository;
import com.trawhile.sse.SseDispatcher;
import com.trawhile.sse.SseEvent;
import com.trawhile.web.dto.McpTokenWithOwner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP access token management. SR-F053.F01, SR-F053.F02, SR-F054.F01, SR-F055.F01, SR-F056.F01, SR-F057.F01.
 */
@Service
public class McpTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final UUID ROOT_NODE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ANONYMISED_PLACEHOLDER = "Anonymised user";

    private final McpTokenRepository mcpTokenRepository;
    private final AuthorizationService authorizationService;
    private final SecurityEventService securityEventService;
    private final SseDispatcher sseDispatcher;
    private final JdbcTemplate jdbcTemplate;

    public McpTokenService(McpTokenRepository mcpTokenRepository,
                           AuthorizationService authorizationService,
                           SecurityEventService securityEventService,
                           SseDispatcher sseDispatcher,
                           JdbcTemplate jdbcTemplate) {
        this.mcpTokenRepository = mcpTokenRepository;
        this.authorizationService = authorizationService;
        this.securityEventService = securityEventService;
        this.sseDispatcher = sseDispatcher;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<com.trawhile.web.dto.McpToken> listOwnTokens(UUID userId) {
        return mcpTokenRepository.findByUserIdAndRevokedAtIsNull(userId).stream()
            .sorted(Comparator.comparing(com.trawhile.domain.McpToken::createdAt).reversed())
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public GeneratedToken generateToken(UUID userId, String label, OffsetDateTime expiresAt) {
        String trimmedLabel = requireNonBlank(label, "label");
        String rawToken = generateRawToken();
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

        com.trawhile.domain.McpToken saved = mcpTokenRepository.save(new com.trawhile.domain.McpToken(
            null,
            userId,
            sha256Hex(rawToken),
            trimmedLabel,
            createdAt,
            null,
            expiresAt,
            null
        ));
        securityEventService.log(
            "MCP_TOKEN_GENERATED",
            userId,
            Map.of("tokenId", saved.id(), "label", saved.label())
        );
        return new GeneratedToken(rawToken, toDto(saved));
    }

    @Transactional
    public void revokeOwn(UUID actingUserId, UUID tokenId) {
        com.trawhile.domain.McpToken token = requireToken(tokenId);
        if (!actingUserId.equals(token.userId())) {
            throw new EntityNotFoundException("MCP token", tokenId);
        }
        revoke(token, actingUserId);
    }

    @Transactional(readOnly = true)
    public List<McpTokenWithOwner> listAllTokens(UUID actingUserId) {
        authorizationService.requireAdmin(actingUserId, ROOT_NODE_ID);
        return jdbcTemplate.query(
            """
                SELECT mt.id,
                       mt.user_id,
                       COALESCE(up.name, ?) AS user_name,
                       mt.label,
                       mt.created_at,
                       mt.last_used_at,
                       mt.expires_at
                FROM mcp_tokens mt
                LEFT JOIN user_profile up ON up.user_id = mt.user_id
                WHERE mt.revoked_at IS NULL
                ORDER BY mt.created_at DESC, mt.id DESC
                """,
            (rs, rowNum) -> {
                McpTokenWithOwner token = new McpTokenWithOwner(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getString("user_name"),
                    rs.getString("label"),
                    rs.getObject("created_at", OffsetDateTime.class)
                );
                token.setLastUsedAt(rs.getObject("last_used_at", OffsetDateTime.class));
                token.setExpiresAt(rs.getObject("expires_at", OffsetDateTime.class));
                return token;
            },
            ANONYMISED_PLACEHOLDER
        );
    }

    @Transactional
    public void adminRevoke(UUID actingUserId, UUID tokenId) {
        authorizationService.requireAdmin(actingUserId, ROOT_NODE_ID);
        revoke(requireToken(tokenId), actingUserId);
    }

    @Transactional
    public AuthenticatedToken authenticate(String rawToken) {
        com.trawhile.domain.McpToken token = mcpTokenRepository.findByTokenHash(sha256Hex(rawToken))
            .orElseThrow(() -> new BadCredentialsException("Invalid MCP token"));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (token.revokedAt() != null || (token.expiresAt() != null && token.expiresAt().isBefore(now))) {
            throw new BadCredentialsException("Invalid MCP token");
        }

        mcpTokenRepository.updateLastUsedAt(token.id(), now);
        return new AuthenticatedToken(token.userId(), token.id());
    }

    private void revoke(com.trawhile.domain.McpToken token, UUID actingUserId) {
        if (token.revokedAt() != null) {
            return;
        }

        OffsetDateTime revokedAt = OffsetDateTime.now(ZoneOffset.UTC);
        mcpTokenRepository.save(new com.trawhile.domain.McpToken(
            token.id(),
            token.userId(),
            token.tokenHash(),
            token.label(),
            token.createdAt(),
            token.lastUsedAt(),
            token.expiresAt(),
            revokedAt
        ));

        securityEventService.log(
            "MCP_TOKEN_REVOKED",
            actingUserId,
            Map.of("tokenId", token.id(), "ownerUserId", token.userId())
        );
        sseDispatcher.dispatch(
            token.userId(),
            new SseEvent(
                SseEvent.EventType.MCP_TOKEN_REVOKED,
                Map.of("tokenId", token.id(), "userId", token.userId())
            )
        );
    }

    private com.trawhile.domain.McpToken requireToken(UUID tokenId) {
        return mcpTokenRepository.findById(tokenId)
            .orElseThrow(() -> new EntityNotFoundException("MCP token", tokenId));
    }

    private com.trawhile.web.dto.McpToken toDto(com.trawhile.domain.McpToken token) {
        com.trawhile.web.dto.McpToken dto = new com.trawhile.web.dto.McpToken(
            token.id(),
            token.label(),
            token.createdAt()
        );
        dto.setLastUsedAt(token.lastUsedAt());
        dto.setExpiresAt(token.expiresAt());
        return dto;
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InputValidationException(
                "INVALID_" + fieldName.toUpperCase(),
                fieldName + " must not be blank"
            );
        }
        return value.trim();
    }

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

    public record GeneratedToken(String rawToken, com.trawhile.web.dto.McpToken mcpToken) {
    }

    public record AuthenticatedToken(UUID userId, UUID tokenId) {
    }
}
