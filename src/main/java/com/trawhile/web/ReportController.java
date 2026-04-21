package com.trawhile.web;

import com.trawhile.service.ReportExportService;
import com.trawhile.service.ReportService;
import com.trawhile.web.api.ReportsApi;
import com.trawhile.web.dto.MemberSummary;
import com.trawhile.web.dto.Report;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * /api/v1/reports — time report with filters, summary/detail, CSV export, member summaries.
 *
 * Endpoints:
 *   GET /reports          — SR-F036.F01/SR-F036.F02  getReport (mode=summary|detailed)
 *   GET /reports/export   — SR-F038.F01              exportReport (CSV)
 *   GET /reports/members  — SR-F052.F01              getMemberSummaries (per-member aggregated totals)
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController implements ReportsApi {

    private final ReportService reportService;
    private final ReportExportService reportExportService;

    public ReportController(ReportService reportService, ReportExportService reportExportService) {
        this.reportService = reportService;
        this.reportExportService = reportExportService;
    }

    @Override
    public ResponseEntity<Report> getReport(String mode,
                                            LocalDate from,
                                            LocalDate to,
                                            UUID userId,
                                            UUID nodeId) {
        return ResponseEntity.ok(reportService.getReport(currentUserId(), mode, from, to, userId, nodeId));
    }

    @Override
    public ResponseEntity<org.springframework.core.io.Resource> exportReport(String mode,
                                                                             LocalDate from,
                                                                             LocalDate to,
                                                                             UUID userId,
                                                                             UUID nodeId) {
        byte[] csv = reportExportService.exportCsv(currentUserId(), mode, from, to, userId, nodeId);
        ByteArrayResource resource = new ByteArrayResource(csv);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + mode + ".csv\"")
            .contentType(new MediaType("text", "csv"))
            .contentLength(csv.length)
            .body(resource);
    }

    @Override
    public ResponseEntity<List<MemberSummary>> getMemberSummaries(String interval,
                                                                  LocalDate from,
                                                                  LocalDate to,
                                                                  UUID nodeId,
                                                                  Boolean hasDataQualityIssues) {
        return ResponseEntity.ok(
            reportService.getMemberSummaries(
                currentUserId(),
                interval,
                from,
                to,
                nodeId,
                hasDataQualityIssues
            )
        );
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
