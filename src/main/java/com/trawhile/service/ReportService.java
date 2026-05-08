package com.trawhile.service;

import com.trawhile.config.TrawhileConfig;
import com.trawhile.domain.TimeRecord;
import com.trawhile.exception.BusinessRuleViolationException;
import com.trawhile.repository.AuthorizationQueries;
import com.trawhile.repository.NodeRepository;
import com.trawhile.repository.TimeRecordRepository;
import com.trawhile.repository.UserProfileRepository;
import com.trawhile.repository.read.ReportReadQueries;
import com.trawhile.web.dto.MemberSummary;
import com.trawhile.web.dto.MemberSummaryBucket;
import com.trawhile.web.dto.NodePathEntry;
import com.trawhile.web.dto.Report;
import com.trawhile.web.dto.ReportDetailEntry;
import com.trawhile.web.dto.ReportSummaryEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final EnumSet<IntervalKind> RANGE_REQUIRED_INTERVALS = EnumSet.of(
        IntervalKind.DAY,
        IntervalKind.WEEK,
        IntervalKind.MONTH,
        IntervalKind.YEAR
    );

    private final TimeRecordRepository timeRecordRepository;
    private final AuthorizationQueries authorizationQueries;
    private final NodeRepository nodeRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuthorizationService authorizationService;
    private final ReportReadQueries reportReadQueries;
    private final ZoneId companyZone;

    @Autowired
    public ReportService(TimeRecordRepository timeRecordRepository,
                         AuthorizationQueries authorizationQueries,
                         NodeRepository nodeRepository,
                         UserProfileRepository userProfileRepository,
                         AuthorizationService authorizationService,
                         ReportReadQueries reportReadQueries,
                         TrawhileConfig trawhileConfig) {
        this(
            timeRecordRepository,
            authorizationQueries,
            nodeRepository,
            userProfileRepository,
            authorizationService,
            reportReadQueries,
            ZoneId.of(trawhileConfig.getTimezone())
        );
    }

    public ReportService(TimeRecordRepository timeRecordRepository,
                         AuthorizationQueries authorizationQueries) {
        this(
            timeRecordRepository,
            authorizationQueries,
            null,
            null,
            null,
            null,
            ZoneId.of("Europe/Berlin")
        );
    }

    private ReportService(TimeRecordRepository timeRecordRepository,
                          AuthorizationQueries authorizationQueries,
                          NodeRepository nodeRepository,
                          UserProfileRepository userProfileRepository,
                          AuthorizationService authorizationService,
                          ReportReadQueries reportReadQueries,
                          ZoneId companyZone) {
        this.timeRecordRepository = timeRecordRepository;
        this.authorizationQueries = authorizationQueries;
        this.nodeRepository = nodeRepository;
        this.userProfileRepository = userProfileRepository;
        this.authorizationService = authorizationService;
        this.reportReadQueries = reportReadQueries;
        this.companyZone = companyZone;
    }

    @Transactional(readOnly = true)
    public Report getReport(UUID actingUserId,
                            String mode,
                            LocalDate from,
                            LocalDate to,
                            UUID userId,
                            UUID nodeId) {
        Report.ModeEnum reportMode = Report.ModeEnum.fromValue(mode.toLowerCase(Locale.ROOT));
        validateDateRange(from, to);
        requireViewIfNeeded(actingUserId, nodeId);

        if (reportReadQueries == null) {
            return legacyReport(actingUserId, reportMode, from, to, userId, nodeId);
        }

        Report report = new Report(reportMode);
        if (reportMode == Report.ModeEnum.SUMMARY) {
            report.setSummary(buildSummary(
                reportReadQueries.findVisibleSummaryTotals(actingUserId, from, to, userId, nodeId)
            ));
            report.setDetailed(null);
        } else {
            report.setDetailed(buildDetailed(
                reportReadQueries.findVisibleDetailedRecords(actingUserId, from, to, userId, nodeId)
            ));
            report.setSummary(null);
        }
        return report;
    }

    @Transactional(readOnly = true)
    public List<MemberSummary> getMemberSummaries(UUID actingUserId,
                                                  String interval,
                                                  LocalDate from,
                                                  LocalDate to,
                                                  UUID nodeId,
                                                  Boolean hasDataQualityIssues) {
        IntervalKind intervalKind = IntervalKind.fromValue(interval);
        DateRange dateRange = resolveMemberSummaryRange(intervalKind, from, to);
        requireViewIfNeeded(actingUserId, nodeId);

        if (reportReadQueries == null) {
            return legacyMemberSummaries(actingUserId, intervalKind, dateRange, nodeId, hasDataQualityIssues);
        }

        List<ReportReadQueries.MemberDaySummaryRow> rows = reportReadQueries.findVisibleMemberSummaries(
            actingUserId,
            nodeId,
            dateRange.from(),
            dateRange.to(),
            interval,
            hasDataQualityIssues
        );
        return buildMemberSummaries(rows, intervalKind, dateRange, hasDataQualityIssues);
    }

    private Report legacyReport(UUID actingUserId,
                                Report.ModeEnum reportMode,
                                LocalDate from,
                                LocalDate to,
                                UUID userId,
                                UUID nodeId) {
        List<TimeRecord> records = timeRecordRepository.findAll().stream()
            .filter(record -> visibleNodeIds(actingUserId, nodeId).contains(record.nodeId()))
            .filter(record -> userId == null || userId.equals(record.userId()))
            .filter(record -> isWithinDateRange(record, from, to))
            .toList();

        Report report = new Report(reportMode);
        if (reportMode == Report.ModeEnum.SUMMARY) {
            Map<UUID, Long> totalsByNode = new LinkedHashMap<>();
            for (TimeRecord record : records) {
                totalsByNode.merge(record.nodeId(), durationSeconds(record), Long::sum);
            }
            report.setSummary(totalsByNode.entrySet().stream()
                .sorted(Comparator.comparing(entry -> nodePathLabel(entry.getKey())))
                .map(entry -> {
                    ReportSummaryEntry summary = new ReportSummaryEntry();
                    summary.setNodeId(entry.getKey());
                    summary.setNodePath(nodePath(entry.getKey()));
                    summary.setTotalSeconds(Math.toIntExact(entry.getValue()));
                    return summary;
                })
                .toList());
            report.setDetailed(null);
        } else {
            List<ReportReadQueries.DetailedRecordRow> rows = records.stream()
                .map(record -> new ReportReadQueries.DetailedRecordRow(
                    record.id(),
                    record.userId(),
                    displayName(record.userId()),
                    record.nodeId(),
                    record.startedAt(),
                    record.endedAt(),
                    record.timezone(),
                    record.description(),
                    record.createdAt()
                ))
                .toList();
            report.setDetailed(buildDetailed(rows));
            report.setSummary(null);
        }
        return report;
    }

    private List<MemberSummary> legacyMemberSummaries(UUID actingUserId,
                                                      IntervalKind intervalKind,
                                                      DateRange dateRange,
                                                      UUID nodeId,
                                                      Boolean hasDataQualityIssues) {
        List<TimeRecord> records = timeRecordRepository.findAll().stream()
            .filter(record -> visibleNodeIds(actingUserId, nodeId).contains(record.nodeId()))
            .filter(record -> isWithinDateRange(record, dateRange.from(), dateRange.to()))
            .sorted(Comparator.comparing(TimeRecord::userId).thenComparing(TimeRecord::startedAt).thenComparing(TimeRecord::id))
            .toList();
        List<BucketWindow> buckets = buildBuckets(intervalKind, dateRange);
        Map<UUID, List<TimeRecord>> recordsByUser = records.stream()
            .collect(Collectors.groupingBy(TimeRecord::userId));

        List<MemberSummary> summaries = new ArrayList<>();
        for (Map.Entry<UUID, List<TimeRecord>> entry : recordsByUser.entrySet()) {
            List<TimeRecord> userRecords = entry.getValue().stream()
                .sorted(Comparator.comparing(TimeRecord::startedAt).thenComparing(TimeRecord::id))
                .toList();
            Map<UUID, RecordFlags> flagsByRecordId = computeFlags(userRecords);
            List<MemberSummaryBucket> bucketDtos = buildLegacyMemberBuckets(
                buckets,
                userRecords,
                flagsByRecordId,
                hasDataQualityIssues
            );
            if (bucketDtos.isEmpty()) {
                continue;
            }

            MemberSummary summary = new MemberSummary();
            summary.setUserId(entry.getKey());
            summary.setUserName(displayName(entry.getKey()));
            summary.setBuckets(bucketDtos);
            summaries.add(summary);
        }
        summaries.sort(Comparator
            .comparing((MemberSummary summary) -> summary.getUserName() == null ? "" : summary.getUserName().toLowerCase(Locale.ROOT))
            .thenComparing(MemberSummary::getUserId));
        return summaries;
    }

    private List<ReportSummaryEntry> buildSummary(List<ReportReadQueries.SummaryTotalRow> totals) {
        return totals.stream()
            .sorted(Comparator.comparing(total -> nodePathLabel(total.nodeId())))
            .map(total -> {
                ReportSummaryEntry entry = new ReportSummaryEntry();
                entry.setNodeId(total.nodeId());
                entry.setNodePath(nodePath(total.nodeId()));
                entry.setTotalSeconds(Math.toIntExact(total.totalSeconds()));
                return entry;
            })
            .toList();
    }

    private List<ReportDetailEntry> buildDetailed(List<ReportReadQueries.DetailedRecordRow> rows) {
        List<ReportReadQueries.DetailedRecordRow> sortedRows = rows.stream()
            .sorted(Comparator.comparing(ReportReadQueries.DetailedRecordRow::userId)
                .thenComparing(ReportReadQueries.DetailedRecordRow::startedAt)
                .thenComparing(ReportReadQueries.DetailedRecordRow::id))
            .toList();
        Map<UUID, List<ReportReadQueries.DetailedRecordRow>> rowsByUser = sortedRows.stream()
            .collect(Collectors.groupingBy(
                ReportReadQueries.DetailedRecordRow::userId,
                LinkedHashMap::new,
                Collectors.toList()
            ));

        Map<UUID, RecordFlags> flagsByRecordId = new HashMap<>();
        for (List<ReportReadQueries.DetailedRecordRow> userRows : rowsByUser.values()) {
            flagsByRecordId.putAll(computeDetailedFlags(userRows));
        }

        List<ReportDetailEntry> entries = new ArrayList<>();
        for (ReportReadQueries.DetailedRecordRow row : sortedRows) {
            RecordFlags flags = flagsByRecordId.getOrDefault(row.id(), RecordFlags.NONE);
            ReportDetailEntry entry = new ReportDetailEntry();
            entry.setId(row.id());
            entry.setUserId(row.userId());
            entry.setUserName(row.userName());
            entry.setNodeId(row.nodeId());
            entry.setNodePath(nodePath(row.nodeId()));
            entry.setStartedAt(toCompanyOffset(row.startedAt()));
            entry.setEndedAt(row.endedAt() == null ? null : toCompanyOffset(row.endedAt()));
            entry.setTimezone(row.timezone());
            entry.setDescription(row.description());
            entry.setOverlapping(flags.overlapping());
            entry.setHasGapBefore(flags.hasGapBefore());
            entries.add(entry);
        }
        return entries;
    }

    private List<MemberSummary> buildMemberSummaries(List<ReportReadQueries.MemberDaySummaryRow> rows,
                                                     IntervalKind intervalKind,
                                                     DateRange dateRange,
                                                     Boolean hasDataQualityIssues) {
        List<BucketWindow> buckets = buildBuckets(intervalKind, dateRange);
        Map<UUID, List<ReportReadQueries.MemberDaySummaryRow>> rowsByUser = rows.stream()
            .collect(Collectors.groupingBy(
                ReportReadQueries.MemberDaySummaryRow::userId,
                LinkedHashMap::new,
                Collectors.toList()
            ));

        List<MemberSummary> summaries = new ArrayList<>();
        for (Map.Entry<UUID, List<ReportReadQueries.MemberDaySummaryRow>> entry : rowsByUser.entrySet()) {
            List<MemberSummaryBucket> bucketDtos = new ArrayList<>();
            for (BucketWindow bucket : buckets) {
                long totalSeconds = 0L;
                boolean hasIssues = false;

                for (ReportReadQueries.MemberDaySummaryRow row : entry.getValue()) {
                    if (row.periodStart().isBefore(bucket.startDate()) || row.periodStart().isAfter(bucket.endDate())) {
                        continue;
                    }
                    totalSeconds += row.totalSeconds();
                    hasIssues = hasIssues || row.hasDataQualityIssues();
                }

                if (totalSeconds == 0L && !hasIssues) {
                    continue;
                }
                if (hasDataQualityIssues != null && hasIssues != hasDataQualityIssues) {
                    continue;
                }

                MemberSummaryBucket bucketDto = new MemberSummaryBucket();
                bucketDto.setPeriodStart(bucket.startDate());
                bucketDto.setPeriodEnd(bucket.endDate());
                bucketDto.setTotalSeconds(Math.toIntExact(totalSeconds));
                bucketDto.setHasDataQualityIssues(hasIssues);
                bucketDtos.add(bucketDto);
            }

            if (bucketDtos.isEmpty()) {
                continue;
            }

            MemberSummary summary = new MemberSummary();
            summary.setUserId(entry.getKey());
            summary.setUserName(entry.getValue().getFirst().userName());
            summary.setBuckets(bucketDtos);
            summaries.add(summary);
        }

        summaries.sort(Comparator
            .comparing((MemberSummary summary) -> summary.getUserName() == null ? "" : summary.getUserName().toLowerCase(Locale.ROOT))
            .thenComparing(MemberSummary::getUserId));
        return summaries;
    }

    private List<MemberSummaryBucket> buildLegacyMemberBuckets(List<BucketWindow> buckets,
                                                               List<TimeRecord> userRecords,
                                                               Map<UUID, RecordFlags> flagsByRecordId,
                                                               Boolean hasDataQualityIssuesFilter) {
        List<MemberSummaryBucket> results = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        for (BucketWindow bucket : buckets) {
            long totalSeconds = 0L;
            boolean hasIssues = false;

            for (TimeRecord record : userRecords) {
                long overlapSeconds = overlapSeconds(record, bucket, now);
                if (overlapSeconds > 0) {
                    totalSeconds += overlapSeconds;
                }
                if (recordIntersectsBucket(record, bucket, now)) {
                    RecordFlags flags = flagsByRecordId.getOrDefault(record.id(), RecordFlags.NONE);
                    hasIssues = hasIssues || flags.overlapping() || flags.hasGapBefore();
                }
            }

            if (totalSeconds == 0L && !hasIssues) {
                continue;
            }
            if (hasDataQualityIssuesFilter != null && hasIssues != hasDataQualityIssuesFilter) {
                continue;
            }

            MemberSummaryBucket bucketDto = new MemberSummaryBucket();
            bucketDto.setPeriodStart(bucket.startDate());
            bucketDto.setPeriodEnd(bucket.endDate());
            bucketDto.setTotalSeconds(Math.toIntExact(totalSeconds));
            bucketDto.setHasDataQualityIssues(hasIssues);
            results.add(bucketDto);
        }

        return results;
    }

    private Map<UUID, RecordFlags> computeDetailedFlags(List<ReportReadQueries.DetailedRecordRow> rows) {
        Map<UUID, RecordFlags> flagsByRecordId = new HashMap<>();
        for (ReportReadQueries.DetailedRecordRow row : rows) {
            flagsByRecordId.put(row.id(), RecordFlags.NONE);
        }

        for (int index = 0; index < rows.size(); index++) {
            ReportReadQueries.DetailedRecordRow current = rows.get(index);
            if (index > 0) {
                ReportReadQueries.DetailedRecordRow previous = rows.get(index - 1);
                if (previous.endedAt() != null && previous.endedAt().isBefore(current.startedAt())) {
                    flagsByRecordId.put(current.id(), flagsByRecordId.get(current.id()).withGapBefore());
                }
            }
            for (int nextIndex = index + 1; nextIndex < rows.size(); nextIndex++) {
                ReportReadQueries.DetailedRecordRow next = rows.get(nextIndex);
                if (overlaps(current, next)) {
                    flagsByRecordId.put(current.id(), flagsByRecordId.get(current.id()).withOverlapping());
                    flagsByRecordId.put(next.id(), flagsByRecordId.get(next.id()).withOverlapping());
                }
            }
        }

        return flagsByRecordId;
    }

    private Map<UUID, RecordFlags> computeFlags(List<TimeRecord> userRecords) {
        List<TimeRecord> sorted = userRecords.stream()
            .sorted(Comparator.comparing(TimeRecord::startedAt).thenComparing(TimeRecord::id))
            .toList();
        Map<UUID, RecordFlags> flagsByRecordId = new HashMap<>();
        for (TimeRecord record : sorted) {
            flagsByRecordId.put(record.id(), RecordFlags.NONE);
        }

        for (int index = 0; index < sorted.size(); index++) {
            TimeRecord current = sorted.get(index);
            if (index > 0) {
                TimeRecord previous = sorted.get(index - 1);
                if (previous.endedAt() != null && previous.endedAt().isBefore(current.startedAt())) {
                    flagsByRecordId.put(current.id(), flagsByRecordId.get(current.id()).withGapBefore());
                }
            }
            for (int nextIndex = index + 1; nextIndex < sorted.size(); nextIndex++) {
                TimeRecord next = sorted.get(nextIndex);
                if (overlaps(current, next)) {
                    flagsByRecordId.put(current.id(), flagsByRecordId.get(current.id()).withOverlapping());
                    flagsByRecordId.put(next.id(), flagsByRecordId.get(next.id()).withOverlapping());
                }
            }
        }

        return flagsByRecordId;
    }

    private boolean overlaps(ReportReadQueries.DetailedRecordRow left, ReportReadQueries.DetailedRecordRow right) {
        OffsetDateTime leftEnd = left.endedAt() != null ? left.endedAt() : OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime rightEnd = right.endedAt() != null ? right.endedAt() : OffsetDateTime.now(ZoneOffset.UTC);
        return left.startedAt().isBefore(rightEnd) && right.startedAt().isBefore(leftEnd);
    }

    private boolean overlaps(TimeRecord left, TimeRecord right) {
        OffsetDateTime leftEnd = effectiveEndedAt(left);
        OffsetDateTime rightEnd = effectiveEndedAt(right);
        return left.startedAt().isBefore(rightEnd) && right.startedAt().isBefore(leftEnd);
    }

    private Set<UUID> visibleNodeIds(UUID actingUserId, UUID nodeId) {
        Set<UUID> visibleNodeIds = new HashSet<>(authorizationQueries.visibleNodeIds(actingUserId));
        if (nodeId == null || nodeRepository == null) {
            return visibleNodeIds;
        }

        Set<UUID> subtreeNodeIds = new HashSet<>();
        LinkedList<UUID> queue = new LinkedList<>();
        queue.add(nodeId);
        while (!queue.isEmpty()) {
            UUID currentNodeId = queue.removeFirst();
            if (!subtreeNodeIds.add(currentNodeId)) {
                continue;
            }
            nodeRepository.findByParentIdOrderBySortOrder(currentNodeId).stream()
                .map(com.trawhile.domain.Node::id)
                .forEach(queue::addLast);
        }
        subtreeNodeIds.retainAll(visibleNodeIds);
        return subtreeNodeIds;
    }

    private void requireViewIfNeeded(UUID actingUserId, UUID nodeId) {
        if (nodeId == null) {
            return;
        }
        if (authorizationService != null) {
            authorizationService.requireView(actingUserId, nodeId);
            return;
        }
        if (!authorizationQueries.hasAuthorization(actingUserId, nodeId, com.trawhile.domain.AuthLevel.VIEW)) {
            throw new org.springframework.security.access.AccessDeniedException("Insufficient authorization on node " + nodeId);
        }
    }

    private String displayName(UUID userId) {
        if (userProfileRepository == null) {
            return null;
        }
        return userProfileRepository.findByUserId(userId)
            .map(com.trawhile.domain.UserProfile::name)
            .orElse(null);
    }

    private boolean isWithinDateRange(TimeRecord record, LocalDate from, LocalDate to) {
        LocalDate localDate = record.startedAt().atZoneSameInstant(companyZone).toLocalDate();
        if (from != null && localDate.isBefore(from)) {
            return false;
        }
        return to == null || !localDate.isAfter(to);
    }

    private OffsetDateTime toCompanyOffset(OffsetDateTime timestamp) {
        return timestamp.atZoneSameInstant(companyZone).toOffsetDateTime();
    }

    private List<NodePathEntry> nodePath(UUID nodeId) {
        if (nodeRepository == null) {
            return List.of(new NodePathEntry(nodeId, nodeId.toString()));
        }
        LinkedList<NodePathEntry> path = new LinkedList<>();
        com.trawhile.domain.Node current = nodeRepository.findById(nodeId).orElse(null);
        if (current == null) {
            return List.of(new NodePathEntry(nodeId, nodeId.toString()));
        }
        while (current != null) {
            path.addFirst(new NodePathEntry(current.id(), current.name()));
            current = current.parentId() == null ? null : nodeRepository.findById(current.parentId()).orElse(null);
        }
        return List.copyOf(path);
    }

    private String nodePathLabel(UUID nodeId) {
        return nodePath(nodeId).stream()
            .map(NodePathEntry::getName)
            .collect(Collectors.joining(" / "));
    }

    private long durationSeconds(TimeRecord record) {
        return Duration.between(record.startedAt(), effectiveEndedAt(record)).toSeconds();
    }

    private OffsetDateTime effectiveEndedAt(TimeRecord record) {
        return record.endedAt() != null ? record.endedAt() : OffsetDateTime.now(ZoneOffset.UTC);
    }

    private long overlapSeconds(TimeRecord record, BucketWindow bucket, OffsetDateTime now) {
        ZonedDateTime recordStart = record.startedAt().atZoneSameInstant(companyZone);
        ZonedDateTime recordEnd = (record.endedAt() != null ? record.endedAt() : now).atZoneSameInstant(companyZone);
        ZonedDateTime overlapStart = recordStart.isAfter(bucket.startDateTime()) ? recordStart : bucket.startDateTime();
        ZonedDateTime overlapEnd = recordEnd.isBefore(bucket.endExclusiveDateTime()) ? recordEnd : bucket.endExclusiveDateTime();
        if (!overlapStart.isBefore(overlapEnd)) {
            return 0L;
        }
        return Duration.between(overlapStart, overlapEnd).toSeconds();
    }

    private boolean recordIntersectsBucket(TimeRecord record, BucketWindow bucket, OffsetDateTime now) {
        return overlapSeconds(record, bucket, now) > 0;
    }

    private DateRange resolveMemberSummaryRange(IntervalKind intervalKind, LocalDate from, LocalDate to) {
        if (RANGE_REQUIRED_INTERVALS.contains(intervalKind)) {
            if (from == null || to == null) {
                throw new BusinessRuleViolationException("INVALID_REPORT_RANGE", "from and to are required for interval " + intervalKind.apiValue);
            }
            validateDateRange(from, to);
            return new DateRange(from, to);
        }

        LocalDate effectiveTo = to != null ? to : LocalDate.now(companyZone);
        LocalDate effectiveFrom = from != null ? from : effectiveTo;
        validateDateRange(effectiveFrom, effectiveTo);
        return new DateRange(effectiveFrom, effectiveTo);
    }

    private List<BucketWindow> buildBuckets(IntervalKind intervalKind, DateRange dateRange) {
        List<BucketWindow> buckets = new ArrayList<>();
        LocalDate cursor = dateRange.from();
        while (!cursor.isAfter(dateRange.to())) {
            LocalDate bucketStart = switch (intervalKind) {
                case DAY -> cursor;
                case WEEK -> cursor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case MONTH, MONTH_TO_DATE -> cursor.withDayOfMonth(1);
                case YEAR, YEAR_TO_DATE -> cursor.withDayOfYear(1);
            };
            LocalDate naturalEnd = switch (intervalKind) {
                case DAY -> bucketStart;
                case WEEK -> bucketStart.plusDays(6);
                case MONTH, MONTH_TO_DATE -> bucketStart.withDayOfMonth(bucketStart.lengthOfMonth());
                case YEAR, YEAR_TO_DATE -> bucketStart.withDayOfYear(bucketStart.lengthOfYear());
            };
            LocalDate bucketEnd = naturalEnd.isAfter(dateRange.to()) ? dateRange.to() : naturalEnd;

            buckets.add(new BucketWindow(
                bucketStart,
                bucketEnd,
                bucketStart.atStartOfDay(companyZone),
                bucketEnd.plusDays(1).atStartOfDay(companyZone)
            ));

            cursor = naturalEnd.plusDays(1);
        }
        return buckets;
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessRuleViolationException("INVALID_REPORT_RANGE", "to must not be before from");
        }
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }

    private record BucketWindow(LocalDate startDate,
                                LocalDate endDate,
                                ZonedDateTime startDateTime,
                                ZonedDateTime endExclusiveDateTime) {
    }

    private record RecordFlags(boolean overlapping, boolean hasGapBefore) {
        private static final RecordFlags NONE = new RecordFlags(false, false);

        private RecordFlags withOverlapping() {
            return overlapping ? this : new RecordFlags(true, hasGapBefore);
        }

        private RecordFlags withGapBefore() {
            return hasGapBefore ? this : new RecordFlags(overlapping, true);
        }
    }

    private enum IntervalKind {
        DAY("day"),
        WEEK("week"),
        MONTH("month"),
        YEAR("year"),
        YEAR_TO_DATE("year_to_date"),
        MONTH_TO_DATE("month_to_date");

        private final String apiValue;

        IntervalKind(String apiValue) {
            this.apiValue = apiValue;
        }

        private static IntervalKind fromValue(String value) {
            return java.util.Arrays.stream(values())
                .filter(intervalKind -> intervalKind.apiValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unexpected interval '" + value + "'"));
        }
    }
}
