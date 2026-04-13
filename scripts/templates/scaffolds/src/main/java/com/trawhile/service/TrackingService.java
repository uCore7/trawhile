package com.trawhile.service;

import com.trawhile.repository.NodeRepository;
import com.trawhile.repository.TimeRecordRepository;
import com.trawhile.sse.SseDispatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TrackingService {

    private final TimeRecordRepository timeRecordRepository;
    private final NodeRepository nodeRepository;
    private final AuthorizationService authorizationService;
    private final SseDispatcher sseDispatcher;

    public TrackingService(TimeRecordRepository timeRecordRepository,
                           NodeRepository nodeRepository,
                           AuthorizationService authorizationService,
                           SseDispatcher sseDispatcher) {
        this.timeRecordRepository = timeRecordRepository;
        this.nodeRepository = nodeRepository;
        this.authorizationService = authorizationService;
        this.sseDispatcher = sseDispatcher;
    }

    // TODO: implement SR-F026.F01 (start), SR-F028.F01 (switch), SR-F029.F01 (stop)
}
