package com.trawhile.service;

import com.trawhile.repository.AuthorizationQueries;
import com.trawhile.repository.TimeEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final TimeEntryRepository timeEntryRepository;
    private final AuthorizationQueries authorizationQueries;

    public ReportService(TimeEntryRepository timeEntryRepository,
                         AuthorizationQueries authorizationQueries) {
        this.timeEntryRepository = timeEntryRepository;
        this.authorizationQueries = authorizationQueries;
    }

    // TODO: implement F4.1–F4.4 (report, summary/detail toggle, CSV export)
}
