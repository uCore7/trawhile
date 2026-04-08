package com.trawhile.service;

import com.trawhile.repository.CompanySettingsRepository;
import com.trawhile.repository.TimeEntryRepository;
import com.trawhile.sse.SseDispatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TimeEntryService {

    private final TimeEntryRepository timeEntryRepository;
    private final CompanySettingsRepository settingsRepository;
    private final AuthorizationService authorizationService;
    private final SseDispatcher sseDispatcher;

    public TimeEntryService(TimeEntryRepository timeEntryRepository,
                            CompanySettingsRepository settingsRepository,
                            AuthorizationService authorizationService,
                            SseDispatcher sseDispatcher) {
        this.timeEntryRepository = timeEntryRepository;
        this.settingsRepository = settingsRepository;
        this.authorizationService = authorizationService;
        this.sseDispatcher = sseDispatcher;
    }

    // TODO: implement F3.10–F3.13 (create, edit, delete, duplicate)
}
