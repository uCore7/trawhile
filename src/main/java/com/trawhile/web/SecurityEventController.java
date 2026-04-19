package com.trawhile.web;

import com.trawhile.service.SecurityEventService;
import com.trawhile.web.api.SecurityEventsApi;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/security-events — view and filter security event log (System Admin only). */
@RestController
@RequestMapping("/api/v1")
public class SecurityEventController implements SecurityEventsApi {

    private final SecurityEventService securityEventService;

    public SecurityEventController(SecurityEventService securityEventService) {
        this.securityEventService = securityEventService;
    }

    // TODO: implement SR-F049.F02 (list and filter security event log)
}
