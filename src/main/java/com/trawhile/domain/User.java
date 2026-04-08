package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Anchor record — never deleted. FK target for all historical data.
 * Personal data lives in UserProfile; deleting UserProfile anonymises the user.
 */
@Table("users")
public record User(
    @Id UUID id,
    boolean isActive,
    boolean isSystemAdmin,
    OffsetDateTime createdAt
) {}
