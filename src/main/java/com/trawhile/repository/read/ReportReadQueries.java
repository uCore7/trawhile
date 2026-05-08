package com.trawhile.repository.read;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ReportReadQueries {

    List<DetailedRecordRow> findOwnDetailedRecords(UUID actingUserId, UUID nodeId, LocalDate from, LocalDate to);

    List<DetailedRecordRow> findVisibleDetailedRecords(UUID actingUserId,
                                                       LocalDate from,
                                                       LocalDate to,
                                                       UUID userId,
                                                       UUID nodeId);

    List<SummaryTotalRow> findVisibleSummaryTotals(UUID actingUserId,
                                                   LocalDate from,
                                                   LocalDate to,
                                                   UUID userId,
                                                   UUID nodeId);

    List<MemberDaySummaryRow> findVisibleMemberSummaries(UUID actingUserId,
                                                         UUID nodeId,
                                                         LocalDate from,
                                                         LocalDate to,
                                                         String interval,
                                                         Boolean hasDataQualityIssues);

    List<MemberDaySummaryRow> findVisibleDailyTotalsForUser(UUID actingUserId,
                                                            UUID targetUserId,
                                                            UUID nodeId,
                                                            LocalDate from,
                                                            LocalDate to);

    record DetailedRecordRow(
        UUID id,
        UUID userId,
        String userName,
        UUID nodeId,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        String timezone,
        String description,
        OffsetDateTime createdAt
    ) {
    }

    record SummaryTotalRow(
        UUID nodeId,
        long totalSeconds
    ) {
    }

    record MemberDaySummaryRow(
        UUID userId,
        String userName,
        LocalDate periodStart,
        LocalDate periodEnd,
        long totalSeconds,
        boolean hasDataQualityIssues
    ) {
    }
}
