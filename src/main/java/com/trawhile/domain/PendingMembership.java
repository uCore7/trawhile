package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Email-only invitation. Deleted when the invited user completes their first OAuth2 login.
 * Auto-purged by a daily scheduled task when expiresAt < NOW() (90-day GDPR storage limitation).
 */
@Table("pending_memberships")
public record PendingMembership(
    @Id UUID id,
    String email,
    UUID invitedBy,
    OffsetDateTime invitedAt,
    OffsetDateTime expiresAt    // 90 days after creation
) {}
