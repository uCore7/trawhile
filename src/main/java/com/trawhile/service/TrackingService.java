package com.trawhile.service;

import com.trawhile.repository.NodeRepository;
import com.trawhile.repository.TimeEntryRepository;
import com.trawhile.sse.SseDispatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TrackingService {

    private final TimeEntryRepository timeEntryRepository;
    private final NodeRepository nodeRepository;
    private final AuthorizationService authorizationService;
    private final SseDispatcher sseDispatcher;

    public TrackingService(TimeEntryRepository timeEntryRepository,
                           NodeRepository nodeRepository,
                           AuthorizationService authorizationService,
                           SseDispatcher sseDispatcher) {
        this.timeEntryRepository = timeEntryRepository;
        this.nodeRepository = nodeRepository;
        this.authorizationService = authorizationService;
        this.sseDispatcher = sseDispatcher;
    }

    // TODO: implement F3.3–F3.6 (start, switch, stop tracking)
}
