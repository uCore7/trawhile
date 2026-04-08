package com.trawhile.web;

import com.trawhile.service.TrackingService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/tracking — start, switch, stop tracking; current status. */
@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    // TODO: implement F3.1, F3.3–F3.6
}
