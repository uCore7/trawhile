package com.trawhile.service;

import com.trawhile.config.TrawhileConfig;
import com.trawhile.repository.TimeRecordRepository;
import com.trawhile.sse.SseDispatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TimeRecordService {

    private final TimeRecordRepository timeRecordRepository;
    private final TrawhileConfig config;
    private final AuthorizationService authorizationService;
    private final SseDispatcher sseDispatcher;

    public TimeRecordService(TimeRecordRepository timeRecordRepository,
                             TrawhileConfig config,
                             AuthorizationService authorizationService,
                             SseDispatcher sseDispatcher) {
        this.timeRecordRepository = timeRecordRepository;
        this.config = config;
        this.authorizationService = authorizationService;
        this.sseDispatcher = sseDispatcher;
    }

    // TODO: implement SR-F031.F01 (create retroactive), SR-F032.F01 (edit), SR-F033.F01 (delete), SR-F034.F01 (duplicate)
    // Freeze check: startedAt < Instant.now().minus(config.getFreezeOffsetYears() * 365, ChronoUnit.DAYS)
}
