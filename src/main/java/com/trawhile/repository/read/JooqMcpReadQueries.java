package com.trawhile.repository.read;

import com.trawhile.jooq.tables.Nodes;
import com.trawhile.repository.authz.AuthorizationFunctions;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.trawhile.jooq.tables.Nodes.NODES;

@Repository
public class JooqMcpReadQueries implements McpReadQueries {

    private final DSLContext dsl;
    private final AuthorizationFunctions authorizationFunctions;
    private final ReportReadQueries reportReadQueries;

    public JooqMcpReadQueries(DSLContext dsl,
                              AuthorizationFunctions authorizationFunctions,
                              ReportReadQueries reportReadQueries) {
        this.dsl = dsl;
        this.authorizationFunctions = authorizationFunctions;
        this.reportReadQueries = reportReadQueries;
    }

    @Override
    public List<VisibleNodeRow> findVisibleNodeTree(UUID actingUserId) {
        Nodes node = NODES.as("node");
        Table<Record1<UUID>> visibleNodes = authorizationFunctions.visibleNodes(actingUserId);
        Field<UUID> visibleNodeId = authorizationFunctions.visibleNodeId(visibleNodes);

        return dsl.select(
                node.ID,
                node.PARENT_ID,
                node.NAME,
                node.DESCRIPTION,
                node.IS_ACTIVE,
                node.SORT_ORDER,
                node.CREATED_AT,
                node.DEACTIVATED_AT,
                node.COLOR,
                node.ICON,
                node.LOGO.isNotNull().and(node.LOGO_MIME_TYPE.isNotNull()).as("has_logo")
            )
            .from(node)
            .join(visibleNodes).on(node.ID.eq(visibleNodeId))
            .orderBy(node.SORT_ORDER.asc(), node.ID.asc())
            .fetch(record -> new VisibleNodeRow(
                record.get(node.ID),
                record.get(node.PARENT_ID),
                record.get(node.NAME),
                record.get(node.DESCRIPTION),
                Boolean.TRUE.equals(record.get(node.IS_ACTIVE)),
                record.get(node.SORT_ORDER),
                record.get(node.CREATED_AT),
                record.get(node.DEACTIVATED_AT),
                record.get(node.COLOR),
                record.get(node.ICON),
                Boolean.TRUE.equals(record.get("has_logo", Boolean.class))
            ))
            .stream()
            .sorted(Comparator.comparingInt(VisibleNodeRow::sortOrder).thenComparing(VisibleNodeRow::id))
            .toList();
    }

    @Override
    public List<ReportReadQueries.DetailedRecordRow> findOwnDetailedRecords(UUID actingUserId,
                                                                            UUID nodeId,
                                                                            LocalDate from,
                                                                            LocalDate to) {
        return reportReadQueries.findOwnDetailedRecords(actingUserId, nodeId, from, to);
    }

    @Override
    public List<DailyTotalRow> findVisibleDailyTotalsForOtherUser(UUID actingUserId,
                                                                  UUID targetUserId,
                                                                  UUID nodeId,
                                                                  LocalDate from,
                                                                  LocalDate to) {
        return reportReadQueries.findVisibleDailyTotalsForUser(actingUserId, targetUserId, nodeId, from, to)
            .stream()
            .map(row -> new DailyTotalRow(
                row.userId(),
                row.userName(),
                row.periodStart(),
                row.periodEnd(),
                row.totalSeconds()
            ))
            .toList();
    }
}
