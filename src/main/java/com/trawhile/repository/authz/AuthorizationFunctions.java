package com.trawhile.repository.authz;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthorizationFunctions {

    public Table<Record1<UUID>> visibleNodes(UUID actingUserId) {
        @SuppressWarnings("unchecked")
        Table<Record1<UUID>> visibleNodes = (Table<Record1<UUID>>) (Table<?>) DSL
            .table("visible_nodes({0})", DSL.val(actingUserId))
            // Keep this wrapper string-based for now. OSS DDLDatabase codegen does not parse CREATE FUNCTION,
            // so visible_nodes(...) remains a runtime PostgreSQL function outside the generated jOOQ model.
            .as("visible_nodes", "node_id");
        return visibleNodes;
    }

    public Field<UUID> visibleNodeId(Table<?> visibleNodes) {
        return DSL.field(DSL.name(visibleNodes.getUnqualifiedName().last(), "node_id"), UUID.class);
    }
}
