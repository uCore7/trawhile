package com.trawhile.web;

import com.trawhile.service.ReportExportService;
import com.trawhile.service.ReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/v1/reports — time report with filters, summary/detail, CSV export, member summaries.
 *
 * Endpoints:
 *   GET /reports          — SR-F036.F01/SR-F036.F02  getReport (mode=summary|detailed)
 *   GET /reports/export   — SR-F038.F01              exportReport (CSV)
 *   GET /reports/members  — SR-F052.F01              getMemberSummaries (per-member aggregated totals)
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportExportService reportExportService;

    public ReportController(ReportService reportService, ReportExportService reportExportService) {
        this.reportService = reportService;
        this.reportExportService = reportExportService;
    }

    // TODO: implement SR-F036.F01 (report), SR-F036.F02 (overlap/gap), SR-F038.F01 (CSV export), SR-F052.F01 (member summaries with hasDataQualityIssues)
    // Member summaries: never expose individual time entry details; daily is the finest granularity
}
