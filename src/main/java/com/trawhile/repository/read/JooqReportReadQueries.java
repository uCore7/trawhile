package com.trawhile.repository.read;

import com.trawhile.config.TrawhileConfig;
import com.trawhile.jooq.tables.TimeRecords;
import com.trawhile.jooq.tables.UserProfile;
import com.trawhile.repository.authz.AuthorizationFunctions;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.trawhile.jooq.tables.TimeRecords.TIME_RECORDS;
import static com.trawhile.jooq.tables.UserProfile.USER_PROFILE;

@Repository
public class JooqReportReadQueries implements ReportReadQueries {

    private final DSLContext dsl;
    private final AuthorizationFunctions authorizationFunctions;
    private final String companyTimezone;

    public JooqReportReadQueries(DSLContext dsl,
                                 AuthorizationFunctions authorizationFunctions,
                                 TrawhileConfig trawhileConfig) {
        this.dsl = dsl;
        this.authorizationFunctions = authorizationFunctions;
        this.companyTimezone = trawhileConfig.getTimezone();
    }

    @Override
    public List<DetailedRecordRow> findOwnDetailedRecords(UUID actingUserId, UUID nodeId, LocalDate from, LocalDate to) {
        return findOwnDetailedRecordsInternal(actingUserId, nodeId, from, to);
    }

    public List<DetailedRecordRow> findOwnDetailedRecords(UUID actingUserId, LocalDate from, LocalDate to, UUID nodeId) {
        return findOwnDetailedRecordsInternal(actingUserId, nodeId, from, to);
    }

    @Override
    public List<DetailedRecordRow> findVisibleDetailedRecords(UUID actingUserId,
                                                              LocalDate from,
                                                              LocalDate to,
                                                              UUID userId,
                                                              UUID nodeId) {
        return findDetailedRecords(actingUserId, from, to, userId, nodeId);
    }

    @Override
    public List<SummaryTotalRow> findVisibleSummaryTotals(UUID actingUserId,
                                                          LocalDate from,
                                                          LocalDate to,
                                                          UUID userId,
                                                          UUID nodeId) {
        TimeRecords record = TIME_RECORDS.as("record");
        Table<Record1<UUID>> visibleNodes = authorizationFunctions.visibleNodes(actingUserId);
        Field<UUID> visibleNodeId = authorizationFunctions.visibleNodeId(visibleNodes);
        Field<Long> durationSeconds = durationSeconds(record);

        if (nodeId == null) {
            return dsl.select(record.NODE_ID, DSL.sum(durationSeconds).as("total_seconds"))
                .from(record)
                .join(visibleNodes).on(record.NODE_ID.eq(visibleNodeId))
                .where(detailConditions(record, from, to, userId))
                .groupBy(record.NODE_ID)
                .fetch(this::mapSummaryRow);
        }

        Table<Record1<UUID>> subtreeNodes = ReadQueryScopes.subtreeNodesTable();
        Field<UUID> subtreeNodeId = ReadQueryScopes.subtreeNodeId(subtreeNodes);
        return dsl.withRecursive(ReadQueryScopes.subtreeNodes(nodeId))
            .select(record.NODE_ID, DSL.sum(durationSeconds).as("total_seconds"))
            .from(record)
            .join(visibleNodes).on(record.NODE_ID.eq(visibleNodeId))
            .join(subtreeNodes).on(record.NODE_ID.eq(subtreeNodeId))
            .where(detailConditions(record, from, to, userId))
            .groupBy(record.NODE_ID)
            .fetch(this::mapSummaryRow);
    }

    @Override
    public List<MemberDaySummaryRow> findVisibleMemberSummaries(UUID actingUserId,
                                                                UUID nodeId,
                                                                LocalDate from,
                                                                LocalDate to,
                                                                String interval,
                                                                Boolean hasDataQualityIssues) {
        return findDailyMemberSummaries(actingUserId, null, nodeId, from, to);
    }

    public List<MemberDaySummaryRow> findVisibleMemberSummaries(UUID actingUserId,
                                                                LocalDate from,
                                                                LocalDate to,
                                                                UUID nodeId,
                                                                String interval,
                                                                Boolean hasDataQualityIssues) {
        return findVisibleMemberSummaries(actingUserId, nodeId, from, to, interval, hasDataQualityIssues);
    }

    public List<MemberDaySummaryRow> findVisibleMemberSummaries(UUID actingUserId,
                                                                UUID nodeId,
                                                                LocalDate from,
                                                                LocalDate to,
                                                                String interval) {
        return findVisibleMemberSummaries(actingUserId, nodeId, from, to, interval, null);
    }

    public List<MemberDaySummaryRow> findVisibleMemberSummaries(UUID actingUserId,
                                                                LocalDate from,
                                                                LocalDate to,
                                                                UUID nodeId,
                                                                String interval) {
        return findVisibleMemberSummaries(actingUserId, nodeId, from, to, interval, null);
    }

    @Override
    public List<MemberDaySummaryRow> findVisibleDailyTotalsForUser(UUID actingUserId,
                                                                   UUID targetUserId,
                                                                   UUID nodeId,
                                                                   LocalDate from,
                                                                   LocalDate to) {
        return findDailyMemberSummaries(actingUserId, targetUserId, nodeId, from, to);
    }

    private List<DetailedRecordRow> findDetailedRecords(UUID actingUserId,
                                                        LocalDate from,
                                                        LocalDate to,
                                                        UUID userId,
                                                        UUID nodeId) {
        TimeRecords record = TIME_RECORDS.as("record");
        UserProfile profile = USER_PROFILE.as("profile");
        Table<Record1<UUID>> visibleNodes = authorizationFunctions.visibleNodes(actingUserId);
        Field<UUID> visibleNodeId = authorizationFunctions.visibleNodeId(visibleNodes);

        if (nodeId == null) {
            return dsl.select(
                    record.ID,
                    record.USER_ID,
                    profile.NAME.as("user_name"),
                    record.NODE_ID,
                    record.STARTED_AT,
                    record.ENDED_AT,
                    record.TIMEZONE,
                    record.DESCRIPTION,
                    record.CREATED_AT
                )
                .from(record)
                .join(visibleNodes).on(record.NODE_ID.eq(visibleNodeId))
                .leftJoin(profile).on(record.USER_ID.eq(profile.USER_ID))
                .where(detailConditions(record, from, to, userId))
                .orderBy(record.STARTED_AT.asc(), record.ID.asc())
                .fetch(this::mapDetailedRow);
        }

        Table<Record1<UUID>> subtreeNodes = ReadQueryScopes.subtreeNodesTable();
        Field<UUID> subtreeNodeId = ReadQueryScopes.subtreeNodeId(subtreeNodes);
        return dsl.withRecursive(ReadQueryScopes.subtreeNodes(nodeId))
            .select(
                record.ID,
                record.USER_ID,
                profile.NAME.as("user_name"),
                record.NODE_ID,
                record.STARTED_AT,
                record.ENDED_AT,
                record.TIMEZONE,
                record.DESCRIPTION,
                record.CREATED_AT
            )
            .from(record)
            .join(visibleNodes).on(record.NODE_ID.eq(visibleNodeId))
            .join(subtreeNodes).on(record.NODE_ID.eq(subtreeNodeId))
            .leftJoin(profile).on(record.USER_ID.eq(profile.USER_ID))
            .where(detailConditions(record, from, to, userId))
            .orderBy(record.STARTED_AT.asc(), record.ID.asc())
            .fetch(this::mapDetailedRow);
    }

    private List<DetailedRecordRow> findOwnDetailedRecordsInternal(UUID actingUserId,
                                                                   UUID nodeId,
                                                                   LocalDate from,
                                                                   LocalDate to) {
        TimeRecords record = TIME_RECORDS.as("record");
        UserProfile profile = USER_PROFILE.as("profile");

        if (nodeId == null) {
            return dsl.select(
                    record.ID,
                    record.USER_ID,
                    profile.NAME.as("user_name"),
                    record.NODE_ID,
                    record.STARTED_AT,
                    record.ENDED_AT,
                    record.TIMEZONE,
                    record.DESCRIPTION,
                    record.CREATED_AT
                )
                .from(record)
                .leftJoin(profile).on(record.USER_ID.eq(profile.USER_ID))
                .where(detailConditions(record, from, to, actingUserId))
                .orderBy(record.STARTED_AT.asc(), record.ID.asc())
                .fetch(this::mapDetailedRow);
        }

        Table<Record1<UUID>> subtreeNodes = ReadQueryScopes.subtreeNodesTable();
        Field<UUID> subtreeNodeId = ReadQueryScopes.subtreeNodeId(subtreeNodes);
        return dsl.withRecursive(ReadQueryScopes.subtreeNodes(nodeId))
            .select(
                record.ID,
                record.USER_ID,
                profile.NAME.as("user_name"),
                record.NODE_ID,
                record.STARTED_AT,
                record.ENDED_AT,
                record.TIMEZONE,
                record.DESCRIPTION,
                record.CREATED_AT
            )
            .from(record)
            .join(subtreeNodes).on(record.NODE_ID.eq(subtreeNodeId))
            .leftJoin(profile).on(record.USER_ID.eq(profile.USER_ID))
            .where(detailConditions(record, from, to, actingUserId))
            .orderBy(record.STARTED_AT.asc(), record.ID.asc())
            .fetch(this::mapDetailedRow);
    }

    private Condition detailConditions(TimeRecords record, LocalDate from, LocalDate to, UUID userId) {
        Condition condition = DSL.trueCondition();
        if (userId != null) {
            condition = condition.and(record.USER_ID.eq(userId));
        }
        if (from != null) {
            condition = condition.and(localStartedDate(record).ge(from));
        }
        if (to != null) {
            condition = condition.and(localStartedDate(record).le(to));
        }
        return condition;
    }

    private Field<LocalDate> localStartedDate(TimeRecords record) {
        return DSL.field(
            "(({0} AT TIME ZONE {1})::date)",
            LocalDate.class,
            record.STARTED_AT,
            DSL.val(companyTimezone)
        );
    }

    private Field<Long> durationSeconds(TimeRecords record) {
        return DSL.field(
            "CAST(EXTRACT(EPOCH FROM COALESCE({0}, CURRENT_TIMESTAMP) - {1}) AS bigint)",
            Long.class,
            record.ENDED_AT,
            record.STARTED_AT
        );
    }

    private DetailedRecordRow mapDetailedRow(Record record) {
        return new DetailedRecordRow(
            record.get(TIME_RECORDS.ID),
            record.get(TIME_RECORDS.USER_ID),
            record.get("user_name", String.class),
            record.get(TIME_RECORDS.NODE_ID),
            record.get(TIME_RECORDS.STARTED_AT),
            record.get(TIME_RECORDS.ENDED_AT),
            record.get(TIME_RECORDS.TIMEZONE),
            record.get(TIME_RECORDS.DESCRIPTION),
            record.get(TIME_RECORDS.CREATED_AT)
        );
    }

    private SummaryTotalRow mapSummaryRow(Record record) {
        Long totalSeconds = record.get("total_seconds", Long.class);
        return new SummaryTotalRow(
            record.get(TIME_RECORDS.NODE_ID),
            totalSeconds == null ? 0L : totalSeconds
        );
    }

    private List<MemberDaySummaryRow> findDailyMemberSummaries(UUID actingUserId,
                                                               UUID targetUserId,
                                                               UUID nodeId,
                                                               LocalDate from,
                                                               LocalDate to) {
        String sql = dailyMemberSummarySql(nodeId != null, targetUserId != null, from != null, to != null);
        List<Object> parameters = new ArrayList<>();
        if (nodeId != null) {
            parameters.add(nodeId);
        }
        parameters.add(actingUserId);
        if (targetUserId != null) {
            parameters.add(targetUserId);
        }
        if (from != null) {
            parameters.add(companyTimezone);
            parameters.add(from);
        }
        if (to != null) {
            parameters.add(companyTimezone);
            parameters.add(to);
        }
        parameters.add(companyTimezone);
        parameters.add(companyTimezone);
        parameters.add(companyTimezone);
        parameters.add(companyTimezone);

        return dsl.resultQuery(sql, parameters.toArray())
            .fetch(record -> new MemberDaySummaryRow(
                record.get("user_id", UUID.class),
                record.get("user_name", String.class),
                record.get("period_start", LocalDate.class),
                record.get("period_end", LocalDate.class),
                record.get("total_seconds", Long.class),
                Boolean.TRUE.equals(record.get("has_data_quality_issues", Boolean.class))
            ));
    }

    private String dailyMemberSummarySql(boolean scopedToSubtree,
                                         boolean filteredByUser,
                                         boolean filteredFrom,
                                         boolean filteredTo) {
        String subtreeCte = scopedToSubtree
            ? """
                WITH RECURSIVE subtree_nodes(node_id) AS (
                  SELECT id FROM nodes WHERE id = ?
                  UNION ALL
                  SELECT n.id
                  FROM nodes n
                  JOIN subtree_nodes s ON n.parent_id = s.node_id
                ),
                """
            : "WITH ";
        String subtreeJoin = scopedToSubtree ? "JOIN subtree_nodes sn ON sn.node_id = tr.node_id" : "";
        String userFilter = filteredByUser ? "AND tr.user_id = ?\n" : "";
        String fromFilter = filteredFrom ? "AND ((tr.started_at AT TIME ZONE ?)::date) >= ?\n" : "";
        String toFilter = filteredTo ? "AND ((tr.started_at AT TIME ZONE ?)::date) <= ?\n" : "";

        return subtreeCte + """
                scoped_records AS (
                  SELECT tr.user_id,
                         up.name AS user_name,
                         tr.started_at,
                         tr.ended_at,
                         COALESCE(tr.ended_at, CURRENT_TIMESTAMP) AS effective_ended_at,
                         MAX(COALESCE(tr.ended_at, CURRENT_TIMESTAMP)) OVER (
                           PARTITION BY tr.user_id
                           ORDER BY tr.started_at, tr.id
                           ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
                         ) AS max_previous_end,
                         LAG(tr.ended_at) OVER (
                           PARTITION BY tr.user_id
                           ORDER BY tr.started_at, tr.id
                         ) AS previous_ended_at,
                         LEAD(tr.started_at) OVER (
                           PARTITION BY tr.user_id
                           ORDER BY tr.started_at, tr.id
                         ) AS next_started_at
                  FROM time_records tr
                  JOIN visible_nodes(CAST(? AS uuid)) vn ON vn.node_id = tr.node_id
                  LEFT JOIN user_profile up ON up.user_id = tr.user_id
                  %s
                  WHERE TRUE
                  %s
                  %s
                  %s
                ),
                exploded AS (
                  SELECT sr.user_id,
                         sr.user_name,
                         day_start::date AS period_start,
                         day_start::date AS period_end,
                         CAST(EXTRACT(EPOCH FROM LEAST(sr.effective_ended_at AT TIME ZONE ?, day_start + INTERVAL '1 day')
                                                - GREATEST(sr.started_at AT TIME ZONE ?, day_start)) AS bigint) AS overlap_seconds,
                         (
                           (sr.max_previous_end IS NOT NULL AND sr.started_at < sr.max_previous_end)
                           OR (sr.next_started_at IS NOT NULL AND sr.effective_ended_at > sr.next_started_at)
                           OR (sr.previous_ended_at IS NOT NULL AND sr.previous_ended_at < sr.started_at)
                         ) AS has_data_quality_issues
                  FROM scoped_records sr
                  JOIN LATERAL generate_series(
                    (sr.started_at AT TIME ZONE ?)::date::timestamp,
                    ((sr.effective_ended_at AT TIME ZONE ?) - INTERVAL '1 microsecond')::date::timestamp,
                    INTERVAL '1 day'
                  ) AS day_start ON TRUE
                )
                SELECT user_id,
                       user_name,
                       period_start,
                       period_end,
                       SUM(overlap_seconds) AS total_seconds,
                       BOOL_OR(has_data_quality_issues) AS has_data_quality_issues
                FROM exploded
                WHERE overlap_seconds > 0
                GROUP BY user_id, user_name, period_start, period_end
                ORDER BY LOWER(COALESCE(user_name, '')), user_id, period_start
                """.formatted(subtreeJoin, userFilter, fromFilter, toFilter);
    }
}
