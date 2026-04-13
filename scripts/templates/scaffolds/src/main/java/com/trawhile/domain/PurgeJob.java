package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Singleton rows (seeded by Flyway): jobType = 'activity' and jobType = 'node'.
 * On startup: any row with status = 'active' is resumed using stored cutoffDate.
 */
@Table("purge_jobs")
public record PurgeJob(
    @Id UUID id,
    String jobType,             // 'activity' | 'node'
    String status,              // 'idle' | 'active'
    LocalDate cutoffDate,       // set at job start; stable across restarts
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    String deletedCounts,       // JSONB tally; updated after each batch commit
    OffsetDateTime lastUpdatedAt
) {}
