package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Email-only invitation. Deleted when the invited user completes their first OAuth2 login. */
@Table("pending_memberships")
public record PendingMembership(
    @Id UUID id,
    String email,
    UUID invitedBy,
    OffsetDateTime invitedAt
) {}
