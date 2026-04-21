package com.trawhile.web;

import com.trawhile.service.SecurityEventService;
import com.trawhile.web.api.SecurityEventsApi;
import com.trawhile.web.dto.ListSecurityEvents200Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/** /api/v1/security-events — view and filter security event log (System Admin only). */
@RestController
@RequestMapping("/api/v1")
public class SecurityEventController implements SecurityEventsApi {

    private final SecurityEventService securityEventService;

    public SecurityEventController(SecurityEventService securityEventService) {
        this.securityEventService = securityEventService;
    }

    @Override
    public ResponseEntity<ListSecurityEvents200Response> listSecurityEvents(String eventType,
                                                                            UUID userId,
                                                                            OffsetDateTime from,
                                                                            OffsetDateTime to,
                                                                            Integer limit,
                                                                            Integer offset) {
        return ResponseEntity.ok(securityEventService.listEvents(
            currentUserId(),
            new SecurityEventService.EventFilters(
                eventType,
                userId,
                from,
                to,
                limit != null ? limit : 50,
                offset != null ? offset : 0
            )
        ));
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
