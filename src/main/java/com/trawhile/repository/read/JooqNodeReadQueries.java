package com.trawhile.repository.read;

import com.trawhile.domain.AuthLevel;
import com.trawhile.jooq.tables.Nodes;
import com.trawhile.repository.AuthorizationQueries;
import com.trawhile.repository.authz.AuthorizationFunctions;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.trawhile.jooq.tables.Nodes.NODES;

@Repository
public class JooqNodeReadQueries implements NodeReadQueries {

    private final DSLContext dsl;
    private final AuthorizationQueries authorizationQueries;
    private final AuthorizationFunctions authorizationFunctions;

    public JooqNodeReadQueries(DSLContext dsl,
                               AuthorizationQueries authorizationQueries,
                               AuthorizationFunctions authorizationFunctions) {
        this.dsl = dsl;
        this.authorizationQueries = authorizationQueries;
        this.authorizationFunctions = authorizationFunctions;
    }

    @Override
    public Optional<NodeSummaryRow> findVisibleRootNodeSummary(UUID actingUserId) {
        Nodes node = NODES.as("node");
        Table<Record1<UUID>> visibleNodes = authorizationFunctions.visibleNodes(actingUserId);
        Field<UUID> visibleNodeId = authorizationFunctions.visibleNodeId(visibleNodes);

        return dsl.select(summaryFields(node))
            .from(node)
            .join(visibleNodes).on(node.ID.eq(visibleNodeId))
            .where(node.PARENT_ID.isNull())
            .fetchOptional(record -> mapSummaryRow(node, record, actingUserId));
    }

    @Override
    public Optional<NodeSummaryRow> findVisibleNodeSummary(UUID actingUserId, UUID nodeId) {
        Nodes node = NODES.as("node");
        Table<Record1<UUID>> visibleNodes = authorizationFunctions.visibleNodes(actingUserId);
        Field<UUID> visibleNodeId = authorizationFunctions.visibleNodeId(visibleNodes);

        return dsl.select(summaryFields(node))
            .from(node)
            .join(visibleNodes).on(node.ID.eq(visibleNodeId))
            .where(node.ID.eq(nodeId))
            .fetchOptional(record -> mapSummaryRow(node, record, actingUserId));
    }

    @Override
    public List<NodeSummaryRow> findVisibleChildren(UUID actingUserId, UUID parentId) {
        Nodes node = NODES.as("node");
        Table<Record1<UUID>> visibleNodes = authorizationFunctions.visibleNodes(actingUserId);
        Field<UUID> visibleNodeId = authorizationFunctions.visibleNodeId(visibleNodes);

        return dsl.select(summaryFields(node))
            .from(node)
            .join(visibleNodes).on(node.ID.eq(visibleNodeId))
            .where(node.PARENT_ID.eq(parentId))
            .orderBy(node.SORT_ORDER.asc(), node.ID.asc())
            .fetch(record -> mapSummaryRow(node, record, actingUserId));
    }

    @Override
    public Optional<NodeContentRow> findVisibleNodeContent(UUID actingUserId, UUID nodeId) {
        Nodes node = NODES.as("node");
        Table<Record1<UUID>> visibleNodes = authorizationFunctions.visibleNodes(actingUserId);
        Field<UUID> visibleNodeId = authorizationFunctions.visibleNodeId(visibleNodes);

        return dsl.select(node.ID, node.LOGO, node.LOGO_MIME_TYPE)
            .from(node)
            .join(visibleNodes).on(node.ID.eq(visibleNodeId))
            .where(node.ID.eq(nodeId))
            .and(node.LOGO.isNotNull())
            .and(node.LOGO_MIME_TYPE.isNotNull())
            .fetchOptional(record -> new NodeContentRow(
                record.get(node.ID),
                record.get(node.LOGO),
                record.get(node.LOGO_MIME_TYPE)
            ));
    }

    private List<Field<?>> summaryFields(Nodes node) {
        return List.of(
            node.ID,
            node.PARENT_ID,
            node.NAME,
            node.DESCRIPTION,
            node.IS_ACTIVE,
            node.SORT_ORDER,
            node.CREATED_AT,
            node.DEACTIVATED_AT,
            hasActiveChildrenField(node),
            node.COLOR,
            node.ICON,
            hasLogoField(node)
        );
    }

    private Field<Boolean> hasActiveChildrenField(Nodes node) {
        Nodes activeChild = NODES.as("active_child");
        return DSL.exists(
            dsl.selectOne()
                .from(activeChild)
                .where(activeChild.PARENT_ID.eq(node.ID))
                .and(activeChild.IS_ACTIVE.isTrue())
        ).as("has_active_children");
    }

    private Field<Boolean> hasLogoField(Nodes node) {
        return node.LOGO.isNotNull().and(node.LOGO_MIME_TYPE.isNotNull()).as("has_logo");
    }

    private NodeSummaryRow mapSummaryRow(Nodes node, Record record, UUID actingUserId) {
        return new NodeSummaryRow(
            record.get(node.ID),
            record.get(node.PARENT_ID),
            record.get(node.NAME),
            record.get(node.DESCRIPTION),
            Boolean.TRUE.equals(record.get(node.IS_ACTIVE)),
            record.get(node.SORT_ORDER),
            record.get(node.CREATED_AT),
            record.get(node.DEACTIVATED_AT),
            Boolean.TRUE.equals(record.get("has_active_children", Boolean.class)),
            authorizationQueries.effectiveAuthorization(actingUserId, record.get(node.ID)),
            record.get(node.COLOR),
            record.get(node.ICON),
            Boolean.TRUE.equals(record.get("has_logo", Boolean.class))
        );
    }
}
