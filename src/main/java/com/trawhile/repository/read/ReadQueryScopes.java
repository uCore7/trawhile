package com.trawhile.repository.read;

import org.jooq.CommonTableExpression;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.util.UUID;

import static com.trawhile.jooq.tables.Nodes.NODES;

final class ReadQueryScopes {

    private static final String SUBTREE_NODES = "subtree_nodes";

    private ReadQueryScopes() {
    }

    static CommonTableExpression<Record1<UUID>> subtreeNodes(UUID rootNodeId) {
        Table<Record1<UUID>> subtreeNodes = subtreeNodesTable();
        Field<UUID> subtreeNodeId = subtreeNodeId(subtreeNodes);

        return DSL.name(SUBTREE_NODES).fields("node_id").as(
            DSL.select(NODES.ID.as("node_id"))
                .from(NODES)
                .where(NODES.ID.eq(rootNodeId))
                .unionAll(
                    DSL.select(NODES.ID.as("node_id"))
                        .from(NODES)
                        .join(subtreeNodes)
                        .on(NODES.PARENT_ID.eq(subtreeNodeId))
                )
        );
    }

    static Table<Record1<UUID>> subtreeNodesTable() {
        @SuppressWarnings("unchecked")
        Table<Record1<UUID>> subtreeNodes = (Table<Record1<UUID>>) (Table<?>) DSL
            .table(DSL.name(SUBTREE_NODES))
            .as(SUBTREE_NODES, "node_id");
        return subtreeNodes;
    }

    static Field<UUID> subtreeNodeId(Table<?> subtreeNodes) {
        return DSL.field(DSL.name(subtreeNodes.getUnqualifiedName().last(), "node_id"), UUID.class);
    }
}
