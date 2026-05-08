package com.trawhile.repository.read;

import com.trawhile.jooq.tables.Nodes;
import com.trawhile.jooq.tables.QuickAccess;
import com.trawhile.jooq.tables.TimeRecords;
import com.trawhile.jooq.tables.UserProfile;
import com.trawhile.repository.authz.AuthorizationFunctions;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.trawhile.jooq.tables.Nodes.NODES;
import static com.trawhile.jooq.tables.QuickAccess.QUICK_ACCESS;
import static com.trawhile.jooq.tables.TimeRecords.TIME_RECORDS;
import static com.trawhile.jooq.tables.UserProfile.USER_PROFILE;

@Repository
public class JooqTrackingReadQueries implements TrackingReadQueries {

    private final DSLContext dsl;
    private final AuthorizationFunctions authorizationFunctions;

    public JooqTrackingReadQueries(DSLContext dsl, AuthorizationFunctions authorizationFunctions) {
        this.dsl = dsl;
        this.authorizationFunctions = authorizationFunctions;
    }

    @Override
    public Optional<TrackingStatusRow> findOwnTrackingStatus(UUID actingUserId) {
        TimeRecords record = TIME_RECORDS.as("record");
        return dsl.select(
                record.ID,
                record.USER_ID,
                record.NODE_ID,
                record.STARTED_AT,
                record.TIMEZONE
            )
            .from(record)
            .where(record.USER_ID.eq(actingUserId))
            .and(record.ENDED_AT.isNull())
            .fetchOptional(this::mapTrackingStatusRow);
    }

    @Override
    public List<TrackingHistoryRow> findOwnTrackingHistory(UUID actingUserId, int limit, int offset) {
        return selectOwnTrackingHistory(actingUserId)
            .limit(Math.max(limit, 0))
            .offset(Math.max(offset, 0))
            .fetch(this::mapTrackingHistoryRow);
    }

    @Override
    public List<TrackingHistoryRow> findOwnTrackingHistoryRecords(UUID actingUserId) {
        return selectOwnTrackingHistory(actingUserId).fetch(this::mapTrackingHistoryRow);
    }

    @Override
    public List<QuickAccessRow> findOwnQuickAccess(UUID actingUserId) {
        QuickAccess quickAccess = QUICK_ACCESS.as("quick_access");
        UserProfile profile = USER_PROFILE.as("profile");
        Nodes node = NODES.as("node");
        Nodes activeChild = NODES.as("active_child");
        Table<Record1<UUID>> visibleNodes = authorizationFunctions.visibleNodes(actingUserId);
        Field<UUID> visibleNodeId = authorizationFunctions.visibleNodeId(visibleNodes);
        Field<Boolean> hasActiveChildren = DSL.exists(
            dsl.selectOne()
                .from(activeChild)
                .where(activeChild.PARENT_ID.eq(node.ID))
                .and(activeChild.IS_ACTIVE.isTrue())
        ).as("has_active_children");

        return dsl.select(
                node.ID,
                node.NAME,
                quickAccess.SORT_ORDER,
                node.IS_ACTIVE,
                hasActiveChildren,
                node.COLOR,
                node.ICON,
                node.LOGO.isNotNull().and(node.LOGO_MIME_TYPE.isNotNull()).as("has_logo")
            )
            .from(quickAccess)
            .join(profile).on(quickAccess.PROFILE_ID.eq(profile.ID))
            .join(node).on(quickAccess.NODE_ID.eq(node.ID))
            .join(visibleNodes).on(node.ID.eq(visibleNodeId))
            .where(profile.USER_ID.eq(actingUserId))
            .orderBy(quickAccess.SORT_ORDER.asc(), node.ID.asc())
            .fetch(record -> new QuickAccessRow(
                record.get(node.ID),
                record.get(node.NAME),
                record.get(quickAccess.SORT_ORDER),
                Boolean.TRUE.equals(record.get(node.IS_ACTIVE)),
                Boolean.TRUE.equals(record.get("has_active_children", Boolean.class)),
                record.get(node.COLOR),
                record.get(node.ICON),
                Boolean.TRUE.equals(record.get("has_logo", Boolean.class))
            ));
    }

    private org.jooq.SelectSeekStep2<?, ?, ?> selectOwnTrackingHistory(UUID actingUserId) {
        TimeRecords record = TIME_RECORDS.as("record");
        return dsl.select(
                record.ID,
                record.USER_ID,
                record.NODE_ID,
                record.STARTED_AT,
                record.ENDED_AT,
                record.TIMEZONE,
                record.DESCRIPTION,
                record.CREATED_AT
            )
            .from(record)
            .where(record.USER_ID.eq(actingUserId))
            .orderBy(record.STARTED_AT.desc(), record.ID.desc());
    }

    private TrackingStatusRow mapTrackingStatusRow(org.jooq.Record record) {
        return new TrackingStatusRow(
            record.get(TIME_RECORDS.ID),
            record.get(TIME_RECORDS.USER_ID),
            record.get(TIME_RECORDS.NODE_ID),
            record.get(TIME_RECORDS.STARTED_AT),
            record.get(TIME_RECORDS.TIMEZONE)
        );
    }

    private TrackingHistoryRow mapTrackingHistoryRow(org.jooq.Record record) {
        return new TrackingHistoryRow(
            record.get(TIME_RECORDS.ID),
            record.get(TIME_RECORDS.USER_ID),
            record.get(TIME_RECORDS.NODE_ID),
            record.get(TIME_RECORDS.STARTED_AT),
            record.get(TIME_RECORDS.ENDED_AT),
            record.get(TIME_RECORDS.TIMEZONE),
            record.get(TIME_RECORDS.DESCRIPTION),
            record.get(TIME_RECORDS.CREATED_AT)
        );
    }
}
