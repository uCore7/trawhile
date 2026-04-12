package com.trawhile.web;

import com.trawhile.service.TrackingService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/quick-access — personal quick-access list (add, remove, reorder). */
@RestController
@RequestMapping("/api/v1/quick-access")
public class QuickAccessController {

    private final TrackingService trackingService;

    public QuickAccessController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    // TODO: implement SR-F027.F01 (list quick access), SR-F030.F01 (add, remove, reorder)
}
