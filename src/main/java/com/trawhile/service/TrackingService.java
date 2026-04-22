package com.trawhile.service;

import com.trawhile.domain.QuickAccess;
import com.trawhile.exception.BusinessRuleViolationException;
import com.trawhile.exception.EntityNotFoundException;
import com.trawhile.exception.InputValidationException;
import com.trawhile.repository.QuickAccessRepository;
import com.trawhile.repository.NodeRepository;
import com.trawhile.repository.TimeRecordRepository;
import com.trawhile.repository.UserProfileRepository;
import com.trawhile.sse.SseDispatcher;
import com.trawhile.sse.SseEvent;
import com.trawhile.web.dto.GetTrackingHistory200Response;
import com.trawhile.web.dto.NodePathEntry;
import com.trawhile.web.dto.QuickAccessEntry;
import com.trawhile.web.dto.StartTrackingRequest;
import com.trawhile.web.dto.TrackingStatus;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TrackingService {

    private final TimeRecordRepository timeRecordRepository;
    private final NodeRepository nodeRepository;
    private final QuickAccessRepository quickAccessRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuthorizationService authorizationService;
    private final SseDispatcher sseDispatcher;
    private final JdbcTemplate jdbcTemplate;

    public TrackingService(TimeRecordRepository timeRecordRepository,
                           NodeRepository nodeRepository,
                           QuickAccessRepository quickAccessRepository,
                           UserProfileRepository userProfileRepository,
                           AuthorizationService authorizationService,
                           SseDispatcher sseDispatcher,
                           JdbcTemplate jdbcTemplate) {
        this.timeRecordRepository = timeRecordRepository;
        this.nodeRepository = nodeRepository;
        this.quickAccessRepository = quickAccessRepository;
        this.userProfileRepository = userProfileRepository;
        this.authorizationService = authorizationService;
        this.sseDispatcher = sseDispatcher;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public TrackingStatus getStatus(UUID actingUserId) {
        return timeRecordRepository.findByUserIdAndEndedAtIsNull(actingUserId)
            .map(this::toTrackingStatus)
            .orElseGet(this::emptyTrackingStatus);
    }

    @Transactional(readOnly = true)
    public GetTrackingHistory200Response getRecentEntries(UUID actingUserId, int limit, int offset) {
        List<com.trawhile.domain.TimeRecord> allRecords = timeRecordRepository.findByUserIdOrderByStartedAtDesc(actingUserId);
        OffsetDateTime now = OffsetDateTime.now();
        Set<UUID> overlappingIds = overlappingRecordIds(allRecords, now);
        Set<UUID> gapBeforeIds = gapBeforeRecordIds(allRecords);

        int total = allRecords.size();
        int fromIndex = Math.min(Math.max(offset, 0), total);
        int toIndex = Math.min(fromIndex + Math.max(limit, 0), total);
        List<com.trawhile.web.dto.TimeRecord> items = allRecords.subList(fromIndex, toIndex).stream()
            .map(record -> toDto(record, overlappingIds.contains(record.id()), gapBeforeIds.contains(record.id())))
            .toList();

        return new GetTrackingHistory200Response(items, total);
    }

    @Transactional
    public TrackingStatus startTracking(UUID actingUserId, StartTrackingRequest request) {
        return timeRecordRepository.findByUserIdAndEndedAtIsNull(actingUserId)
            .map(existing -> switchTracking(actingUserId, request))
            .orElseGet(() -> createTrackingRecord(actingUserId, request, null));
    }

    @Transactional
    public TrackingStatus switchTracking(UUID actingUserId, StartTrackingRequest request) {
        com.trawhile.domain.TimeRecord existing = timeRecordRepository.findByUserIdAndEndedAtIsNull(actingUserId)
            .orElseThrow(() -> new BusinessRuleViolationException("NO_ACTIVE_TRACKING", "No active tracking session to switch"));
        return createTrackingRecord(actingUserId, request, existing);
    }

    @Transactional
    public TrackingStatus stopTracking(UUID actingUserId) {
        com.trawhile.domain.TimeRecord existing = timeRecordRepository.findByUserIdAndEndedAtIsNull(actingUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active tracking session"));
        timeRecordRepository.save(new com.trawhile.domain.TimeRecord(
            existing.id(),
            existing.userId(),
            existing.nodeId(),
            existing.startedAt(),
            OffsetDateTime.now(),
            existing.timezone(),
            existing.description(),
            existing.createdAt()
        ));

        TrackingStatus status = emptyTrackingStatus();
        dispatchTrackingUpdate(actingUserId, status);
        return status;
    }

    @Transactional(readOnly = true)
    public List<QuickAccessEntry> getQuickAccess(UUID actingUserId) {
        UUID profileId = requireProfileId(actingUserId);
        return quickAccessRepository.findByProfileIdOrderBySortOrder(profileId).stream()
            .map(this::toQuickAccessEntry)
            .toList();
    }

    @Transactional
    public void addQuickAccess(UUID actingUserId, UUID nodeId) {
        requireNode(nodeId);
        UUID profileId = requireProfileId(actingUserId);
        List<QuickAccess> existingEntries = quickAccessRepository.findByProfileIdOrderBySortOrder(profileId);
        if (existingEntries.size() >= 9) {
            throw new BusinessRuleViolationException("QUICK_ACCESS_FULL", "Quick-access list already contains 9 entries");
        }
        if (quickAccessRepository.findByProfileIdAndNodeId(profileId, nodeId).isPresent()) {
            return;
        }

        quickAccessRepository.save(new QuickAccess(
            null,
            profileId,
            nodeId,
            existingEntries.size()
        ));
    }

    @Transactional
    public void removeQuickAccess(UUID actingUserId, UUID nodeId) {
        UUID profileId = requireProfileId(actingUserId);
        QuickAccess existing = quickAccessRepository.findByProfileIdAndNodeId(profileId, nodeId)
            .orElseThrow(() -> new EntityNotFoundException("Quick access entry not found for node " + nodeId));
        quickAccessRepository.delete(existing);
        normalizeQuickAccessOrder(profileId);
    }

    @Transactional
    public void reorderQuickAccess(UUID actingUserId, List<UUID> nodeIds) {
        UUID profileId = requireProfileId(actingUserId);
        List<QuickAccess> currentEntries = quickAccessRepository.findByProfileIdOrderBySortOrder(profileId);
        Set<UUID> currentIds = currentEntries.stream().map(QuickAccess::nodeId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> submittedIds = new HashSet<>(nodeIds);
        if (nodeIds.size() != currentEntries.size()
            || submittedIds.size() != nodeIds.size()
            || !submittedIds.equals(currentIds)) {
            throw new InputValidationException(
                "INVALID_QUICK_ACCESS_ORDER",
                "Submitted nodeIds must match the current quick-access entries exactly"
            );
        }

        for (int index = 0; index < nodeIds.size(); index++) {
            UUID nodeId = nodeIds.get(index);
            QuickAccess entry = currentEntries.stream()
                .filter(candidate -> candidate.nodeId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new InputValidationException("INVALID_QUICK_ACCESS_ORDER", "Unknown quick-access node: " + nodeId));
            quickAccessRepository.save(new QuickAccess(entry.id(), entry.profileId(), entry.nodeId(), index));
        }
    }

    private TrackingStatus createTrackingRecord(UUID actingUserId,
                                               StartTrackingRequest request,
                                               com.trawhile.domain.TimeRecord existing) {
        com.trawhile.domain.Node node = requireNode(request.getNodeId());
        authorizationService.requireTrack(actingUserId, node.id());
        Trackability trackability = trackability(node.id());
        if (!trackability.active()) {
            throw new BusinessRuleViolationException("NODE_INACTIVE", "Cannot track time on an inactive node");
        }
        if (trackability.hasActiveChildren()) {
            throw new BusinessRuleViolationException("NODE_NOT_TRACKABLE", "Cannot track time on a node that has active children");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (existing != null) {
            timeRecordRepository.save(new com.trawhile.domain.TimeRecord(
                existing.id(),
                existing.userId(),
                existing.nodeId(),
                existing.startedAt(),
                now,
                existing.timezone(),
                existing.description(),
                existing.createdAt()
            ));
        }

        com.trawhile.domain.TimeRecord created = timeRecordRepository.save(new com.trawhile.domain.TimeRecord(
            null,
            actingUserId,
            node.id(),
            now,
            null,
            request.getTimezone(),
            null,
            now
        ));

        TrackingStatus status = toTrackingStatus(created);
        dispatchTrackingUpdate(actingUserId, status);
        return status;
    }

    private QuickAccessEntry toQuickAccessEntry(QuickAccess entry) {
        com.trawhile.domain.Node node = requireNode(entry.nodeId());
        Trackability trackability = trackability(node.id());
        QuickAccessEntry dto = new QuickAccessEntry(
            node.id(),
            nodePath(node),
            entry.sortOrder(),
            !trackability.active() || trackability.hasActiveChildren()
        );
        dto.setColor(node.color());
        dto.setIcon(node.icon());
        dto.setLogoUrl(node.logo() == null ? null : "/api/v1/nodes/" + node.id() + "/logo");
        return dto;
    }

    private com.trawhile.web.dto.TimeRecord toDto(com.trawhile.domain.TimeRecord record,
                                                  boolean overlapping,
                                                  boolean hasGapBefore) {
        com.trawhile.domain.Node node = requireNode(record.nodeId());
        com.trawhile.web.dto.TimeRecord dto = new com.trawhile.web.dto.TimeRecord(
            record.id(),
            record.userId(),
            record.nodeId(),
            nodePath(node),
            record.startedAt(),
            record.timezone(),
            record.createdAt(),
            overlapping,
            hasGapBefore
        );
        dto.setEndedAt(record.endedAt());
        dto.setDescription(record.description());
        return dto;
    }

    private TrackingStatus toTrackingStatus(com.trawhile.domain.TimeRecord record) {
        com.trawhile.domain.Node node = requireNode(record.nodeId());
        long elapsedSeconds = Math.max(0L, Duration.between(record.startedAt(), OffsetDateTime.now()).getSeconds());
        TrackingStatus status = new TrackingStatus(true);
        status.setRecordId(record.id());
        status.setNodeId(record.nodeId());
        status.setNodePath(nodePath(node));
        status.setStartedAt(record.startedAt());
        status.setTimezone(record.timezone());
        status.setElapsedSeconds((int) Math.min(Integer.MAX_VALUE, elapsedSeconds));
        return status;
    }

    private TrackingStatus emptyTrackingStatus() {
        return new TrackingStatus(false);
    }

    private void dispatchTrackingUpdate(UUID actingUserId, TrackingStatus status) {
        sseDispatcher.dispatch(actingUserId, new SseEvent(SseEvent.EventType.TRACKING_STATUS, status));
    }

    private UUID requireProfileId(UUID actingUserId) {
        return userProfileRepository.findByUserId(actingUserId)
            .orElseThrow(() -> new EntityNotFoundException("UserProfile", actingUserId))
            .id();
    }

    private com.trawhile.domain.Node requireNode(UUID nodeId) {
        return nodeRepository.findById(nodeId)
            .orElseThrow(() -> new EntityNotFoundException("Node", nodeId));
    }

    private List<NodePathEntry> nodePath(com.trawhile.domain.Node leafNode) {
        LinkedList<NodePathEntry> path = new LinkedList<>();
        com.trawhile.domain.Node current = leafNode;
        while (current != null) {
            path.addFirst(new NodePathEntry(current.id(), current.name()));
            current = current.parentId() == null ? null : requireNode(current.parentId());
        }
        return List.copyOf(path);
    }

    private Trackability trackability(UUID nodeId) {
        return jdbcTemplate.query(
            """
                SELECT n.is_active,
                       EXISTS (
                           SELECT 1
                           FROM nodes child
                           WHERE child.parent_id = n.id
                           AND child.is_active = TRUE
                       ) AS has_active_children
                FROM nodes n
                WHERE n.id = ?
                """,
            rs -> {
                if (!rs.next()) {
                    throw new EntityNotFoundException("Node", nodeId);
                }
                return new Trackability(rs.getBoolean("is_active"), rs.getBoolean("has_active_children"));
            },
            nodeId
        );
    }

    private Set<UUID> overlappingRecordIds(List<com.trawhile.domain.TimeRecord> records, OffsetDateTime now) {
        Set<UUID> overlappingIds = new HashSet<>();
        for (int i = 0; i < records.size(); i++) {
            for (int j = i + 1; j < records.size(); j++) {
                com.trawhile.domain.TimeRecord first = records.get(i);
                com.trawhile.domain.TimeRecord second = records.get(j);
                OffsetDateTime firstEnd = first.endedAt() != null ? first.endedAt() : now;
                OffsetDateTime secondEnd = second.endedAt() != null ? second.endedAt() : now;
                if (first.startedAt().isBefore(secondEnd) && second.startedAt().isBefore(firstEnd)) {
                    overlappingIds.add(first.id());
                    overlappingIds.add(second.id());
                }
            }
        }
        return overlappingIds;
    }

    private Set<UUID> gapBeforeRecordIds(List<com.trawhile.domain.TimeRecord> records) {
        Set<UUID> gapBeforeIds = new HashSet<>();
        for (int index = 0; index < records.size() - 1; index++) {
            com.trawhile.domain.TimeRecord newer = records.get(index);
            com.trawhile.domain.TimeRecord older = records.get(index + 1);
            if (older.endedAt() != null && newer.startedAt().isAfter(older.endedAt())) {
                gapBeforeIds.add(newer.id());
            }
        }
        return gapBeforeIds;
    }

    private void normalizeQuickAccessOrder(UUID profileId) {
        List<QuickAccess> entries = quickAccessRepository.findByProfileIdOrderBySortOrder(profileId);
        List<QuickAccess> reorderedEntries = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            QuickAccess entry = entries.get(index);
            if (entry.sortOrder() != index) {
                reorderedEntries.add(new QuickAccess(entry.id(), entry.profileId(), entry.nodeId(), index));
            }
        }
        quickAccessRepository.saveAll(reorderedEntries);
    }

    private record Trackability(boolean active, boolean hasActiveChildren) {
    }
}
