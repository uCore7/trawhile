package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table("nodes")
public record Node(
    @Id UUID id,
    UUID parentId,
    String name,
    String description,
    boolean isActive,
    int sortOrder,
    OffsetDateTime createdAt,
    OffsetDateTime deactivatedAt
) {}
