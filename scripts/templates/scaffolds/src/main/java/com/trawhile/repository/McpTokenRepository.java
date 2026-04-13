package com.trawhile.repository;

import com.trawhile.domain.McpToken;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface McpTokenRepository extends ListCrudRepository<McpToken, UUID> {

    /** All active (non-revoked) tokens for a user. SR-F054.F01. */
    List<McpToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    /** All active tokens across all users. SR-F056.F01. */
    List<McpToken> findAllByRevokedAtIsNull();

    /** Lookup by token hash for authentication. SR-F053.F02. */
    Optional<McpToken> findByTokenHash(String tokenHash);

    /** Update last_used_at after a successful MCP request. SR-F053.F02. */
    @Modifying
    @Query("UPDATE mcp_tokens SET last_used_at = :lastUsedAt WHERE id = :id")
    void updateLastUsedAt(UUID id, OffsetDateTime lastUsedAt);

    /** Soft-delete (revoke) a token. */
    @Modifying
    @Query("UPDATE mcp_tokens SET revoked_at = NOW() WHERE id = :id AND revoked_at IS NULL")
    void revokeById(UUID id);

    /** Revoke all tokens for a user (used during anonymization and scrubbing). SR-F070.F01. */
    @Modifying
    @Query("UPDATE mcp_tokens SET revoked_at = NOW() WHERE user_id = :userId AND revoked_at IS NULL")
    void revokeAllByUserId(UUID userId);
}
