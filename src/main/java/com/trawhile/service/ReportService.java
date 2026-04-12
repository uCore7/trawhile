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

    // TODO: implement SR-F036.F01 (report), SR-F036.F02 (overlap/gap annotation), SR-F052.F01 (member summaries), SR-F038.F01 (CSV export)
}
