package com.trawhile.web;

import com.trawhile.service.RequestService;
import com.trawhile.web.api.RequestsApi;
import com.trawhile.web.dto.CreateRequestRequest;
import com.trawhile.web.dto.RequestRecord;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** /api/v1/nodes/{nodeId}/requests — submit, view, and close requests. */
@RestController
@RequestMapping("/api/v1")
public class RequestController implements RequestsApi {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @Override
    public ResponseEntity<RequestRecord> closeRequest(UUID nodeId, UUID requestId) {
        return ResponseEntity.ok(requestService.closeRequest(currentUserId(), nodeId, requestId));
    }

    @Override
    public ResponseEntity<RequestRecord> createRequest(UUID nodeId, CreateRequestRequest createRequestRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(requestService.submitRequest(currentUserId(), nodeId, createRequestRequest));
    }

    @Override
    public ResponseEntity<List<RequestRecord>> listNodeRequests(UUID nodeId) {
        return ResponseEntity.ok(requestService.listRequests(currentUserId(), nodeId));
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
