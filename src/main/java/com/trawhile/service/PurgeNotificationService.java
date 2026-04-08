package com.trawhile.service;

import com.trawhile.repository.CompanySettingsRepository;
import com.trawhile.repository.PurgeJobRepository;
import com.trawhile.repository.TimeEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PurgeNotificationService {

    private final CompanySettingsRepository settingsRepository;
    private final PurgeJobRepository purgeJobRepository;
    private final TimeEntryRepository timeEntryRepository;

    public PurgeNotificationService(CompanySettingsRepository settingsRepository,
                                    PurgeJobRepository purgeJobRepository,
                                    TimeEntryRepository timeEntryRepository) {
        this.settingsRepository = settingsRepository;
        this.purgeJobRepository = purgeJobRepository;
        this.timeEntryRepository = timeEntryRepository;
    }

    // TODO: implement F8.2 — compute pre-notification (active 6 weeks before scheduled purge)
}
