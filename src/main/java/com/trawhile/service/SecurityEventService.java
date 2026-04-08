package com.trawhile.service;

import com.trawhile.domain.SecurityEvent;
import com.trawhile.repository.SecurityEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Transactional
public class SecurityEventService {

    private final SecurityEventRepository securityEventRepository;

    public SecurityEventService(SecurityEventRepository securityEventRepository) {
        this.securityEventRepository = securityEventRepository;
    }

    public void log(UUID userId, String eventType, String details, String ipAddress) {
        securityEventRepository.save(new SecurityEvent(
            null, userId, eventType, details, ipAddress, OffsetDateTime.now()
        ));
    }

    // TODO: implement F7.2–F7.3 (view and filter security event log)
    // TODO: implement 90-day retention purge (scheduled daily)
}
