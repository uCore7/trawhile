package com.trawhile.web;

import com.trawhile.service.TrackingService;
import com.trawhile.web.api.QuickAccessApi;
import com.trawhile.web.dto.AddQuickAccessRequest;
import com.trawhile.web.dto.QuickAccessEntry;
import com.trawhile.web.dto.ReorderQuickAccessRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** /api/v1/quick-access — personal quick-access list (add, remove, reorder). */
@RestController
@RequestMapping("/api/v1")
public class QuickAccessController implements QuickAccessApi {

    private final TrackingService trackingService;

    public QuickAccessController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @Override
    public ResponseEntity<Void> addQuickAccess(AddQuickAccessRequest addQuickAccessRequest) {
        trackingService.addQuickAccess(currentUserId(), addQuickAccessRequest.getNodeId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<QuickAccessEntry>> getQuickAccess() {
        return ResponseEntity.ok(trackingService.getQuickAccess(currentUserId()));
    }

    @Override
    public ResponseEntity<Void> removeQuickAccess(UUID nodeId) {
        trackingService.removeQuickAccess(currentUserId(), nodeId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> reorderQuickAccess(ReorderQuickAccessRequest reorderQuickAccessRequest) {
        trackingService.reorderQuickAccess(currentUserId(), reorderQuickAccessRequest.getNodeIds());
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
