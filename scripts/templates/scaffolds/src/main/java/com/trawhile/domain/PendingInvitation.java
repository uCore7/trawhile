package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pending invitation. A users row is created at invite time and referenced here.
 * Deleted when the invited user completes GDPR acknowledgement (first login).
 * Auto-purged after 90 days; purge also deletes the linked users row (no time records or requests exist yet).
 */
@Table("pending_invitations")
public record PendingInvitation(
    @Id UUID id,
    UUID userId,
    String email,
    UUID invitedBy,
    OffsetDateTime invitedAt,
    OffsetDateTime expiresAt
) {}
