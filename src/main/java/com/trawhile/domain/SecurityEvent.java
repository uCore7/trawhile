package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Security event log entry. Retained for exactly 90 days. */
@Table("security_events")
public record SecurityEvent(
    @Id UUID id,
    UUID userId,            // null for pre-auth events
    String eventType,
    String details,         // JSONB stored as String; parse as needed
    String ipAddress,
    OffsetDateTime occurredAt
) {}
