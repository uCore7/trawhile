package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/** Personal quick-access list entry. Cascade-deleted with UserProfile on anonymization. */
@Table("quick_access")
public record QuickAccess(
    @Id UUID id,
    UUID profileId,
    UUID nodeId,
    int sortOrder
) {}
