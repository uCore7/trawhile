package com.trawhile.web;

import com.trawhile.service.PurgeNotificationService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/lifecycle — purge notification and job status (Node Admin of root / System Admin). */
@RestController
@RequestMapping("/api/v1/lifecycle")
public class LifecycleController {

    private final PurgeNotificationService purgeNotificationService;

    public LifecycleController(PurgeNotificationService purgeNotificationService) {
        this.purgeNotificationService = purgeNotificationService;
    }

    // TODO: implement F8.2 (pre-notification endpoint)
}
