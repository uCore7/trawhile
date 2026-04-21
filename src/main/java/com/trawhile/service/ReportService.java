package com.trawhile.service;

import com.trawhile.config.TrawhileConfig;
import com.trawhile.domain.TimeRecord;
import com.trawhile.domain.UserProfile;
import com.trawhile.exception.BusinessRuleViolationException;
import com.trawhile.repository.AuthorizationQueries;
import com.trawhile.repository.NodeRepository;
import com.trawhile.repository.TimeRecordRepository;
import com.trawhile.repository.UserProfileRepository;
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
    private final ZoneId companyZone;

    @Autowired
    public ReportService(TimeRecordRepository timeRecordRepository,
                         AuthorizationQueries authorizationQueries,
                         NodeRepository nodeRepository,
                         UserProfileRepository userProfileRepository,
                         AuthorizationService authorizationService,
                         TrawhileConfig trawhileConfig) {
        this(
            timeRecordRepository,
            authorizationQueries,
            nodeRepository,
            userProfileRepository,
            authorizationService,
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
            ZoneId.of("Europe/Berlin")
        );
    }

    private ReportService(TimeRecordRepository timeRecordRepository,
                          AuthorizationQueries authorizationQueries,
                          NodeRepository nodeRepository,
                          UserProfileRepository userProfileRepository,
                          AuthorizationService authorizationService,
                          ZoneId companyZone) {
        this.timeRecordRepository = timeRecordRepository;
        this.authorizationQueries = authorizationQueries;
        this.nodeRepository = nodeRepository;
        this.userProfileRepository = userProfileRepository;
        this.authorizationService = authorizationService;
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
        FilteredContext context = loadFilteredContext(actingUserId, from, to, userId, nodeId);

        Report report = new Report(reportMode);
        if (reportMode == Report.ModeEnum.SUMMARY) {
            report.setSummary(buildSummary(context.records(), context.nodesById()));
            report.setDetailed(null);
        } else {
            report.setDetailed(buildDetailed(context.records(), context.nodesById(), context.userNamesById()));
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
        FilteredContext context = loadFilteredContext(actingUserId, dateRange.from(), dateRange.to(), null, nodeId);
        List<BucketWindow> buckets = buildBuckets(intervalKind, dateRange);
        Map<UUID, List<TimeRecord>> recordsByUser = context.records().stream()
            .collect(Collectors.groupingBy(TimeRecord::userId));

        List<MemberSummary> summaries = new ArrayList<>();
        for (Map.Entry<UUID, List<TimeRecord>> entry : recordsByUser.entrySet()) {
            List<TimeRecord> userRecords = entry.getValue().stream()
                .sorted(Comparator.comparing(TimeRecord::startedAt).thenComparing(TimeRecord::id))
                .toList();
            Map<UUID, RecordFlags> flagsByRecordId = computeFlags(userRecords);
            List<MemberSummaryBucket> bucketDtos = buildMemberBuckets(
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
            summary.setUserName(context.userNamesById().get(entry.getKey()));
            summary.setBuckets(bucketDtos);
            summaries.add(summary);
        }

        summaries.sort(Comparator
            .comparing((MemberSummary summary) -> summary.getUserName() == null ? "" : summary.getUserName().toLowerCase(Locale.ROOT))
            .thenComparing(MemberSummary::getUserId));
        return summaries;
    }

    private List<ReportSummaryEntry> buildSummary(List<TimeRecord> records, Map<UUID, com.trawhile.domain.Node> nodesById) {
        Map<UUID, Long> totalsByNode = new LinkedHashMap<>();
        for (TimeRecord record : records) {
            totalsByNode.merge(record.nodeId(), durationSeconds(record), Long::sum);
        }

        return totalsByNode.entrySet().stream()
            .sorted(Comparator.comparing(entry -> nodePathLabel(entry.getKey(), nodesById)))
            .map(entry -> {
                ReportSummaryEntry summaryEntry = new ReportSummaryEntry();
                summaryEntry.setNodeId(entry.getKey());
                summaryEntry.setNodePath(nodePath(entry.getKey(), nodesById));
                summaryEntry.setTotalSeconds(Math.toIntExact(entry.getValue()));
                return summaryEntry;
            })
            .toList();
    }

    private List<ReportDetailEntry> buildDetailed(List<TimeRecord> records,
                                                  Map<UUID, com.trawhile.domain.Node> nodesById,
                                                  Map<UUID, String> userNamesById) {
        List<TimeRecord> sortedRecords = records.stream()
            .sorted(Comparator.comparing(TimeRecord::userId).thenComparing(TimeRecord::startedAt).thenComparing(TimeRecord::id))
            .toList();
        Map<UUID, List<TimeRecord>> recordsByUser = sortedRecords.stream()
            .collect(Collectors.groupingBy(TimeRecord::userId, LinkedHashMap::new, Collectors.toList()));

        Map<UUID, RecordFlags> flagsByRecordId = new HashMap<>();
        for (List<TimeRecord> userRecords : recordsByUser.values()) {
            flagsByRecordId.putAll(computeFlags(userRecords));
        }

        List<ReportDetailEntry> entries = new ArrayList<>();
        for (TimeRecord record : sortedRecords) {
            RecordFlags flags = flagsByRecordId.getOrDefault(record.id(), RecordFlags.NONE);
            ReportDetailEntry detailEntry = new ReportDetailEntry();
            detailEntry.setId(record.id());
            detailEntry.setUserId(record.userId());
            detailEntry.setUserName(userNamesById.get(record.userId()));
            detailEntry.setNodeId(record.nodeId());
            detailEntry.setNodePath(nodePath(record.nodeId(), nodesById));
            detailEntry.setStartedAt(toCompanyOffset(record.startedAt()));
            detailEntry.setEndedAt(record.endedAt() == null ? null : toCompanyOffset(record.endedAt()));
            detailEntry.setTimezone(record.timezone());
            detailEntry.setDescription(record.description());
            detailEntry.setOverlapping(flags.overlapping());
            detailEntry.setHasGapBefore(flags.hasGapBefore());
            entries.add(detailEntry);
        }
        return entries;
    }

    private List<MemberSummaryBucket> buildMemberBuckets(List<BucketWindow> buckets,
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

    private boolean overlaps(TimeRecord left, TimeRecord right) {
        OffsetDateTime leftEnd = effectiveEndedAt(left);
        OffsetDateTime rightEnd = effectiveEndedAt(right);
        return left.startedAt().isBefore(rightEnd) && right.startedAt().isBefore(leftEnd);
    }

    private FilteredContext loadFilteredContext(UUID actingUserId,
                                                LocalDate from,
                                                LocalDate to,
                                                UUID userId,
                                                UUID nodeId) {
        validateDateRange(from, to);
        requireViewIfNeeded(actingUserId, nodeId);

        Set<UUID> visibleNodeIds = new HashSet<>(authorizationQueries.visibleNodeIds(actingUserId));
        Map<UUID, com.trawhile.domain.Node> nodesById = loadNodesById();
        Set<UUID> allowedNodeIds = resolveAllowedNodeIds(visibleNodeIds, nodeId, nodesById);
        Map<UUID, String> userNamesById = loadUserNamesById();

        List<TimeRecord> records = timeRecordRepository.findAll().stream()
            .filter(record -> allowedNodeIds.contains(record.nodeId()))
            .filter(record -> userId == null || userId.equals(record.userId()))
            .filter(record -> isWithinDateRange(record, from, to))
            .sorted(Comparator.comparing(TimeRecord::startedAt).thenComparing(TimeRecord::id))
            .toList();

        return new FilteredContext(records, nodesById, userNamesById);
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

    private Map<UUID, com.trawhile.domain.Node> loadNodesById() {
        if (nodeRepository == null) {
            return Map.of();
        }
        return nodeRepository.findAll().stream()
            .collect(Collectors.toMap(com.trawhile.domain.Node::id, node -> node));
    }

    private Map<UUID, String> loadUserNamesById() {
        if (userProfileRepository == null) {
            return Map.of();
        }
        return userProfileRepository.findAll().stream()
            .collect(Collectors.toMap(UserProfile::userId, UserProfile::name));
    }

    private Set<UUID> resolveAllowedNodeIds(Set<UUID> visibleNodeIds,
                                            UUID nodeId,
                                            Map<UUID, com.trawhile.domain.Node> nodesById) {
        if (nodeId == null) {
            return visibleNodeIds;
        }
        if (nodesById.isEmpty()) {
            return visibleNodeIds.contains(nodeId) ? Set.of(nodeId) : Set.of();
        }

        Set<UUID> subtreeNodeIds = new HashSet<>();
        LinkedList<UUID> queue = new LinkedList<>();
        queue.add(nodeId);
        while (!queue.isEmpty()) {
            UUID currentNodeId = queue.removeFirst();
            if (!subtreeNodeIds.add(currentNodeId)) {
                continue;
            }
            nodesById.values().stream()
                .filter(node -> currentNodeId.equals(node.parentId()))
                .map(com.trawhile.domain.Node::id)
                .forEach(queue::addLast);
        }
        subtreeNodeIds.retainAll(visibleNodeIds);
        return subtreeNodeIds;
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

    private List<NodePathEntry> nodePath(UUID nodeId, Map<UUID, com.trawhile.domain.Node> nodesById) {
        if (nodesById.isEmpty()) {
            return List.of(new NodePathEntry(nodeId, nodeId.toString()));
        }

        LinkedList<NodePathEntry> path = new LinkedList<>();
        com.trawhile.domain.Node current = nodesById.get(nodeId);
        if (current == null) {
            return List.of(new NodePathEntry(nodeId, nodeId.toString()));
        }

        while (current != null) {
            path.addFirst(new NodePathEntry(current.id(), current.name()));
            current = current.parentId() == null ? null : nodesById.get(current.parentId());
        }
        return List.copyOf(path);
    }

    private String nodePathLabel(UUID nodeId, Map<UUID, com.trawhile.domain.Node> nodesById) {
        return nodePath(nodeId, nodesById).stream()
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
            LocalDate bucketEnd = switch (intervalKind) {
                case MONTH_TO_DATE, YEAR_TO_DATE -> naturalEnd.isAfter(dateRange.to()) ? dateRange.to() : naturalEnd;
                default -> naturalEnd.isAfter(dateRange.to()) ? dateRange.to() : naturalEnd;
            };

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

    private record FilteredContext(List<TimeRecord> records,
                                   Map<UUID, com.trawhile.domain.Node> nodesById,
                                   Map<UUID, String> userNamesById) {
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
