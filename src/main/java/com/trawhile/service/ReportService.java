package com.trawhile.service;

import com.trawhile.repository.AuthorizationQueries;
import com.trawhile.repository.TimeRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final TimeRecordRepository timeRecordRepository;
    private final AuthorizationQueries authorizationQueries;

    public ReportService(TimeRecordRepository timeRecordRepository,
                         AuthorizationQueries authorizationQueries) {
        this.timeRecordRepository = timeRecordRepository;
        this.authorizationQueries = authorizationQueries;
    }

    // TODO: implement SR-F036.F01 (report), SR-F036.F02 (overlap/gap annotation), SR-F052.F01 (member summaries), SR-F038.F01 (CSV export)
}
