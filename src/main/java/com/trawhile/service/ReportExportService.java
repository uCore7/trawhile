package com.trawhile.service;

import com.trawhile.repository.AuthorizationQueries;
import com.trawhile.repository.TimeRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportExportService {

    private final TimeRecordRepository timeRecordRepository;
    private final AuthorizationQueries authorizationQueries;

    public ReportExportService(TimeRecordRepository timeRecordRepository,
                               AuthorizationQueries authorizationQueries) {
        this.timeRecordRepository = timeRecordRepository;
        this.authorizationQueries = authorizationQueries;
    }

    // TODO: implement SR-F038.F01 — exportCsv (serialise current report result set to CSV download)
}
