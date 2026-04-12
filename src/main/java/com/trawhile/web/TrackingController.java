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

    // TODO: implement SR-F024.F01 (current status), SR-F026.F01 (start), SR-F028.F01 (switch), SR-F029.F01 (stop)
}
