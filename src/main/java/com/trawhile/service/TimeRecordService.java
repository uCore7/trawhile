package com.trawhile.service;

import com.trawhile.exception.BusinessRuleViolationException;
import com.trawhile.exception.EntityNotFoundException;
import com.trawhile.config.TrawhileConfig;
import com.trawhile.repository.NodeRepository;
import com.trawhile.repository.TimeRecordRepository;
import com.trawhile.web.dto.CreateTimeRecordRequest;
import com.trawhile.web.dto.DuplicateTimeRecordRequest;
import com.trawhile.web.dto.NodePathEntry;
import com.trawhile.web.dto.UpdateTimeRecordRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Service
public class TimeRecordService {

    private final TimeRecordRepository timeRecordRepository;
    private final NodeRepository nodeRepository;
    private final TrawhileConfig config;
    private final AuthorizationService authorizationService;
    private final JdbcTemplate jdbcTemplate;

    public TimeRecordService(TimeRecordRepository timeRecordRepository,
                             NodeRepository nodeRepository,
                             TrawhileConfig config,
                             AuthorizationService authorizationService,
                             JdbcTemplate jdbcTemplate) {
        this.timeRecordRepository = timeRecordRepository;
        this.nodeRepository = nodeRepository;
        this.config = config;
        this.authorizationService = authorizationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public com.trawhile.web.dto.TimeRecord createRetroactive(UUID actingUserId, CreateTimeRecordRequest request) {
        validateTimeRange(request.getStartedAt(), request.getEndedAt());
        com.trawhile.domain.Node node = requireTrackableNodeForUser(actingUserId, request.getNodeId());

        UUID recordId = UUID.randomUUID();
        timeRecordRepository.save(new com.trawhile.domain.TimeRecord(
            recordId,
            actingUserId,
            node.id(),
            request.getStartedAt(),
            request.getEndedAt(),
            request.getTimezone(),
            request.getDescription(),
            null
        ));
        return toDto(requireOwnedRecord(actingUserId, recordId));
    }

    @Transactional
    public com.trawhile.web.dto.TimeRecord editRecord(UUID actingUserId, UUID recordId, UpdateTimeRecordRequest request) {
        com.trawhile.domain.TimeRecord existing = requireOwnedRecord(actingUserId, recordId);

        UUID nodeId = request.getNodeId() != null ? request.getNodeId() : existing.nodeId();
        OffsetDateTime startedAt = request.getStartedAt() != null ? request.getStartedAt() : existing.startedAt();
        OffsetDateTime endedAt = request.getEndedAt() != null ? request.getEndedAt() : existing.endedAt();
        String description = request.getDescription() != null ? request.getDescription() : existing.description();

        if (startedAt.isBefore(freezeCutoff())) {
            throw new BusinessRuleViolationException("FROZEN_RECORD", "Time records before the freeze cutoff cannot be changed");
        }
        if (endedAt != null) {
            validateTimeRange(startedAt, endedAt);
        }
        if (!nodeId.equals(existing.nodeId())) {
            requireTrackableNodeForUser(actingUserId, nodeId);
        }

        com.trawhile.domain.TimeRecord updated = timeRecordRepository.save(new com.trawhile.domain.TimeRecord(
            existing.id(),
            existing.userId(),
            nodeId,
            startedAt,
            endedAt,
            existing.timezone(),
            description,
            existing.createdAt()
        ));
        return toDto(updated);
    }

    @Transactional
    public void deleteRecord(UUID actingUserId, UUID recordId) {
        com.trawhile.domain.TimeRecord existing = requireOwnedRecord(actingUserId, recordId);
        if (existing.startedAt().isBefore(freezeCutoff())) {
            throw new BusinessRuleViolationException("FROZEN_RECORD", "Time records before the freeze cutoff cannot be deleted");
        }
        timeRecordRepository.delete(existing);
    }

    @Transactional
    public com.trawhile.web.dto.TimeRecord duplicateRecord(UUID actingUserId,
                                                           UUID recordId,
                                                           DuplicateTimeRecordRequest request) {
        com.trawhile.domain.TimeRecord original = requireOwnedRecord(actingUserId, recordId);
        validateTimeRange(request.getStartedAt(), request.getEndedAt());
        requireTrackableNodeForUser(actingUserId, original.nodeId());

        UUID duplicatedRecordId = UUID.randomUUID();
        timeRecordRepository.save(new com.trawhile.domain.TimeRecord(
            duplicatedRecordId,
            actingUserId,
            original.nodeId(),
            request.getStartedAt(),
            request.getEndedAt(),
            original.timezone(),
            original.description(),
            null
        ));
        return toDto(requireOwnedRecord(actingUserId, duplicatedRecordId));
    }

    private com.trawhile.domain.TimeRecord requireOwnedRecord(UUID actingUserId, UUID recordId) {
        com.trawhile.domain.TimeRecord record = timeRecordRepository.findById(recordId)
            .orElseThrow(() -> new EntityNotFoundException("TimeRecord", recordId));
        if (!record.userId().equals(actingUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("Time record does not belong to the authenticated user");
        }
        return record;
    }

    private com.trawhile.domain.Node requireTrackableNodeForUser(UUID actingUserId, UUID nodeId) {
        com.trawhile.domain.Node node = requireNode(nodeId);
        authorizationService.requireTrack(actingUserId, node.id());
        Trackability trackability = trackability(node.id());
        if (!trackability.active()) {
            throw new BusinessRuleViolationException("NODE_INACTIVE", "Cannot track time on an inactive node");
        }
        if (trackability.hasActiveChildren()) {
            throw new BusinessRuleViolationException("NODE_NOT_TRACKABLE", "Cannot track time on a node that has active children");
        }
        return node;
    }

    private com.trawhile.domain.Node requireNode(UUID nodeId) {
        return nodeRepository.findById(nodeId)
            .orElseThrow(() -> new EntityNotFoundException("Node", nodeId));
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

    private void validateTimeRange(OffsetDateTime startedAt, OffsetDateTime endedAt) {
        if (!startedAt.isBefore(endedAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startedAt must be before endedAt");
        }
    }

    private OffsetDateTime freezeCutoff() {
        return OffsetDateTime.now().minusYears(config.getFreezeOffsetYears());
    }

    private com.trawhile.web.dto.TimeRecord toDto(com.trawhile.domain.TimeRecord record) {
        com.trawhile.domain.Node node = requireNode(record.nodeId());
        com.trawhile.web.dto.TimeRecord dto = new com.trawhile.web.dto.TimeRecord(
            record.id(),
            record.userId(),
            record.nodeId(),
            nodePath(node),
            record.startedAt(),
            record.timezone(),
            record.createdAt(),
            false,
            false
        );
        dto.setEndedAt(record.endedAt());
        dto.setDescription(record.description());
        return dto;
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

    private record Trackability(boolean active, boolean hasActiveChildren) {
    }
}
