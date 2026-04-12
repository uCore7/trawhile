package com.trawhile.service;

import com.trawhile.repository.AuthorizationQueries;
import com.trawhile.repository.TimeEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportExportService {

    private final TimeEntryRepository timeEntryRepository;
    private final AuthorizationQueries authorizationQueries;

    public ReportExportService(TimeEntryRepository timeEntryRepository,
                               AuthorizationQueries authorizationQueries) {
        this.timeEntryRepository = timeEntryRepository;
        this.authorizationQueries = authorizationQueries;
    }

    // TODO: implement SR-F038.F01 — exportCsv (serialise current report result set to CSV download)
}
