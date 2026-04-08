package com.trawhile.web;

import com.trawhile.service.SecurityEventService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/security-events — view and filter security event log (System Admin only). */
@RestController
@RequestMapping("/api/v1/security-events")
public class SecurityEventController {

    private final SecurityEventService securityEventService;

    public SecurityEventController(SecurityEventService securityEventService) {
        this.securityEventService = securityEventService;
    }

    // TODO: implement F7.2–F7.3
}
