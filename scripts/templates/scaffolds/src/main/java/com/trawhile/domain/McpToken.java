package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * MCP access token. Only the SHA-256 hex hash of the raw token is stored; the raw value is
 * shown once at generation time and never retrievable afterwards.
 * Revocation: soft-delete via revokedAt.
 */
@Table("mcp_tokens")
public record McpToken(
    @Id UUID id,
    UUID userId,
    String tokenHash,       // SHA-256 hex of the raw token; UNIQUE
    String label,           // human-readable name chosen by the user
    OffsetDateTime createdAt,
    OffsetDateTime lastUsedAt,  // nullable; updated on each authenticated MCP request
    OffsetDateTime expiresAt,   // nullable — null means non-expiring
    OffsetDateTime revokedAt    // nullable — null means active
) {}
