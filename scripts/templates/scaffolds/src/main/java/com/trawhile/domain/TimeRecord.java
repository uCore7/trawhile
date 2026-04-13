package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table("time_records")
public record TimeRecord(
    @Id UUID id,
    UUID userId,
    UUID nodeId,
    OffsetDateTime startedAt,
    OffsetDateTime endedAt,     // null = active record
    String timezone,            // IANA timezone captured from browser at tracking start; private (discloses coarse location)
    String description,         // optional short note by the member; nullable
    OffsetDateTime createdAt
) {}
