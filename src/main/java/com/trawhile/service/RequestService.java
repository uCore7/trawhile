package com.trawhile.service;

import com.trawhile.domain.AuthLevel;
import com.trawhile.exception.BusinessRuleViolationException;
import com.trawhile.exception.EntityNotFoundException;
import com.trawhile.repository.AuthorizationQueries;
import com.trawhile.repository.NodeRepository;
import com.trawhile.repository.RequestRepository;
import com.trawhile.repository.UserProfileRepository;
import com.trawhile.sse.SseDispatcher;
import com.trawhile.sse.SseEvent;
import com.trawhile.web.dto.CreateRequestRequest;
import com.trawhile.web.dto.RequestRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RequestService {

    private static final String ANONYMISED_PLACEHOLDER = "Anonymised user";

    private final RequestRepository requestRepository;
    private final NodeRepository nodeRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuthorizationQueries authorizationQueries;
    private final AuthorizationService authorizationService;
    private final SseDispatcher sseDispatcher;

    public RequestService(RequestRepository requestRepository,
                          NodeRepository nodeRepository,
                          UserProfileRepository userProfileRepository,
                          AuthorizationQueries authorizationQueries,
                          AuthorizationService authorizationService,
                          SseDispatcher sseDispatcher) {
        this.requestRepository = requestRepository;
        this.nodeRepository = nodeRepository;
        this.userProfileRepository = userProfileRepository;
        this.authorizationQueries = authorizationQueries;
        this.authorizationService = authorizationService;
        this.sseDispatcher = sseDispatcher;
    }

    @Transactional
    public RequestRecord submitRequest(UUID actingUserId, UUID nodeId, CreateRequestRequest request) {
        requireNode(nodeId);
        authorizationService.requireView(actingUserId, nodeId);
        OffsetDateTime createdAt = OffsetDateTime.now();

        com.trawhile.domain.Request created = requestRepository.save(new com.trawhile.domain.Request(
            null,
            actingUserId,
            nodeId,
            request.getTemplate().getValue(),
            request.getBody(),
            "open",
            createdAt,
            null,
            null
        ));

        RequestRecord dto = toDto(created);
        dispatchRequestEvent(nodeId, dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<RequestRecord> listRequests(UUID actingUserId, UUID nodeId) {
        requireNode(nodeId);
        authorizationService.requireView(actingUserId, nodeId);

        return requestRepository.findByNodeIdOrderByCreatedAtDesc(nodeId).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public RequestRecord closeRequest(UUID actingUserId, UUID nodeId, UUID requestId) {
        requireNode(nodeId);
        authorizationService.requireAdmin(actingUserId, nodeId);

        com.trawhile.domain.Request existing = requireRequestOnNode(requestId, nodeId);
        if ("closed".equals(existing.status())) {
            throw new BusinessRuleViolationException("REQUEST_ALREADY_CLOSED", "Request is already closed");
        }

        com.trawhile.domain.Request closed = requestRepository.save(new com.trawhile.domain.Request(
            existing.id(),
            existing.requesterId(),
            existing.nodeId(),
            existing.template(),
            existing.body(),
            "closed",
            existing.createdAt(),
            OffsetDateTime.now(),
            actingUserId
        ));

        RequestRecord dto = toDto(closed);
        dispatchRequestEvent(nodeId, dto);
        return dto;
    }

    private void dispatchRequestEvent(UUID nodeId, RequestRecord request) {
        sseDispatcher.dispatchToAll(
            authorizationQueries.usersWithAuthorization(nodeId, AuthLevel.ADMIN),
            new SseEvent(SseEvent.EventType.REQUEST_EVENT, request)
        );
    }

    private void requireNode(UUID nodeId) {
        if (!nodeRepository.existsById(nodeId)) {
            throw new EntityNotFoundException("Node", nodeId);
        }
    }

    private com.trawhile.domain.Request requireRequest(UUID requestId) {
        return requestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Request", requestId));
    }

    private com.trawhile.domain.Request requireRequestOnNode(UUID requestId, UUID nodeId) {
        com.trawhile.domain.Request request = requireRequest(requestId);
        if (!nodeId.equals(request.nodeId())) {
            throw new EntityNotFoundException("Request", requestId);
        }
        return request;
    }

    private RequestRecord toDto(com.trawhile.domain.Request request) {
        RequestRecord dto = new RequestRecord(
            request.id(),
            request.requesterId(),
            displayName(request.requesterId()),
            request.nodeId(),
            RequestRecord.TemplateEnum.fromValue(request.template()),
            RequestRecord.StatusEnum.fromValue(request.status()),
            request.createdAt()
        );
        dto.setBody(request.body());
        dto.setResolvedAt(request.resolvedAt());
        dto.setResolvedBy(request.resolvedBy());
        dto.setResolvedByName(request.resolvedBy() == null ? null : displayName(request.resolvedBy()));
        return dto;
    }

    private String displayName(UUID userId) {
        if (userId == null) {
            return ANONYMISED_PLACEHOLDER;
        }
        return userProfileRepository.findByUserId(userId)
            .map(com.trawhile.domain.UserProfile::name)
            .orElse(ANONYMISED_PLACEHOLDER);
    }
}
