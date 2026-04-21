package com.trawhile.service;

import com.trawhile.web.dto.MemberSummaryBucket;
import com.trawhile.web.dto.NodePathEntry;
import com.trawhile.web.dto.Report;
import com.trawhile.web.dto.ReportDetailEntry;
import com.trawhile.web.dto.ReportSummaryEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ReportExportService {

    private final ReportService reportService;

    public ReportExportService(ReportService reportService) {
        this.reportService = reportService;
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(UUID actingUserId,
                            String mode,
                            LocalDate from,
                            LocalDate to,
                            UUID userId,
                            UUID nodeId) {
        Report report = reportService.getReport(actingUserId, mode, from, to, userId, nodeId);
        StringBuilder csv = new StringBuilder();

        if (report.getMode() == Report.ModeEnum.SUMMARY) {
            csv.append("nodeId,nodePath,totalSeconds\n");
            List<ReportSummaryEntry> summaryEntries = report.getSummary() == null ? List.of() : report.getSummary();
            for (ReportSummaryEntry entry : summaryEntries) {
                csv.append(csvValue(entry.getNodeId())).append(',')
                    .append(csvValue(nodePath(entry.getNodePath()))).append(',')
                    .append(csvValue(entry.getTotalSeconds())).append('\n');
            }
        } else {
            csv.append("id,userId,userName,nodeId,nodePath,startedAt,endedAt,timezone,description,overlapping,hasGapBefore\n");
            List<ReportDetailEntry> detailEntries = report.getDetailed() == null ? List.of() : report.getDetailed();
            for (ReportDetailEntry entry : detailEntries) {
                csv.append(csvValue(entry.getId())).append(',')
                    .append(csvValue(entry.getUserId())).append(',')
                    .append(csvValue(entry.getUserName())).append(',')
                    .append(csvValue(entry.getNodeId())).append(',')
                    .append(csvValue(nodePath(entry.getNodePath()))).append(',')
                    .append(csvValue(entry.getStartedAt())).append(',')
                    .append(csvValue(entry.getEndedAt())).append(',')
                    .append(csvValue(entry.getTimezone())).append(',')
                    .append(csvValue(entry.getDescription())).append(',')
                    .append(csvValue(entry.getOverlapping())).append(',')
                    .append(csvValue(entry.getHasGapBefore())).append('\n');
            }
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String nodePath(List<NodePathEntry> pathEntries) {
        return pathEntries == null ? "" : pathEntries.stream()
            .map(NodePathEntry::getName)
            .reduce((left, right) -> left + " / " + right)
            .orElse("");
    }

    @SuppressWarnings("unused")
    private String bucket(MemberSummaryBucket bucket) {
        return bucket.getPeriodStart() + " - " + bucket.getPeriodEnd();
    }

    private String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
