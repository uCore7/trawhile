package com.trawhile.web;

import com.trawhile.service.TrackingService;
import com.trawhile.web.api.TrackingApi;
import com.trawhile.web.dto.GetTrackingHistory200Response;
import com.trawhile.web.dto.StartTrackingRequest;
import com.trawhile.web.dto.TrackingStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** /api/v1/tracking — start, switch, stop tracking; current status. */
@RestController
@RequestMapping("/api/v1")
public class TrackingController implements TrackingApi {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @Override
    public ResponseEntity<GetTrackingHistory200Response> getTrackingHistory(Integer limit, Integer offset) {
        return ResponseEntity.ok(trackingService.getRecentEntries(currentUserId(), limit, offset));
    }

    @Override
    public ResponseEntity<TrackingStatus> getTrackingStatus() {
        return ResponseEntity.ok(trackingService.getStatus(currentUserId()));
    }

    @Override
    public ResponseEntity<TrackingStatus> startTracking(StartTrackingRequest startTrackingRequest) {
        return ResponseEntity.ok(trackingService.startTracking(currentUserId(), startTrackingRequest));
    }

    @Override
    public ResponseEntity<TrackingStatus> stopTracking() {
        return ResponseEntity.ok(trackingService.stopTracking(currentUserId()));
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
