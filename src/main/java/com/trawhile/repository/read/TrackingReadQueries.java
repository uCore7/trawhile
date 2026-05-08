package com.trawhile.repository.read;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackingReadQueries {

    Optional<TrackingStatusRow> findOwnTrackingStatus(UUID actingUserId);

    List<TrackingHistoryRow> findOwnTrackingHistory(UUID actingUserId, int limit, int offset);

    List<TrackingHistoryRow> findOwnTrackingHistoryRecords(UUID actingUserId);

    List<QuickAccessRow> findOwnQuickAccess(UUID actingUserId);

    record TrackingStatusRow(
        UUID recordId,
        UUID userId,
        UUID nodeId,
        OffsetDateTime startedAt,
        String timezone
    ) {
    }

    record TrackingHistoryRow(
        UUID id,
        UUID userId,
        UUID nodeId,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        String timezone,
        String description,
        OffsetDateTime createdAt
    ) {
    }

    record QuickAccessRow(
        UUID nodeId,
        String nodeName,
        int sortOrder,
        boolean active,
        boolean hasActiveChildren,
        String color,
        String icon,
        boolean hasLogo
    ) {
    }
}
