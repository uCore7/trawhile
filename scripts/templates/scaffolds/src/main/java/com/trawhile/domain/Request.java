package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table("requests")
public record Request(
    @Id UUID id,
    UUID requesterId,
    UUID nodeId,
    String template,        // system template ID or 'free_text'
    String body,
    String status,          // 'open' | 'closed'
    OffsetDateTime createdAt,
    OffsetDateTime resolvedAt,
    UUID resolvedBy
) {}
