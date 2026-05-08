package com.trawhile.repository.read;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface McpReadQueries {

    List<VisibleNodeRow> findVisibleNodeTree(UUID actingUserId);

    List<ReportReadQueries.DetailedRecordRow> findOwnDetailedRecords(UUID actingUserId,
                                                                     UUID nodeId,
                                                                     LocalDate from,
                                                                     LocalDate to);

    List<DailyTotalRow> findVisibleDailyTotalsForOtherUser(UUID actingUserId,
                                                           UUID targetUserId,
                                                           UUID nodeId,
                                                           LocalDate from,
                                                           LocalDate to);

    record VisibleNodeRow(
        UUID id,
        UUID parentId,
        String name,
        String description,
        boolean isActive,
        int sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime deactivatedAt,
        String color,
        String icon,
        boolean hasLogo
    ) {
    }

    record DailyTotalRow(
        UUID userId,
        String userName,
        LocalDate periodStart,
        LocalDate periodEnd,
        long totalSeconds
    ) {
    }
}
