package com.trawhile.web;

import com.trawhile.service.ReportExportService;
import com.trawhile.service.ReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/v1/reports — time report with filters, summary/detail, CSV export, member summaries.
 *
 * Endpoints:
 *   GET /reports          — SR-037/SR-038 getReport (mode=summary|detailed)
 *   GET /reports/export   — SR-039        exportReport (CSV)
 *   GET /reports/members  — SR-063        getMemberSummaries (per-member aggregated totals)
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

    // TODO: implement F4.1–F4.4 and SR-063 (member summaries with hasDataQualityIssues flag)
    // Member summaries: never expose individual time entry details; daily is the finest granularity
}
