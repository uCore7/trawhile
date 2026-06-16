package com.trawhile.adapter.outbound.persistence;

import com.trawhile.port.outbound.persistence.AdminAuthorizationPort;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * jOOQ-backed implementation of {@link AdminAuthorizationPort}.
 *
 * <p>Checks whether a user holds an effective {@code admin} grant on the root
 * node (the singleton node with {@code parent_id IS NULL}). Since the root has
 * no ancestors, a direct grant on it is the only effective admin grant on the
 * root itself.</p>
 *
 * <p>Uses lowercase, unquoted field and table names for the same reason as
 * {@link ApiKeyLookupJooqAdapter}.</p>
 */
@Component
public class AuthorizationJooqAdapter implements AdminAuthorizationPort {

    private final DSLContext dsl;

    public AuthorizationJooqAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean hasAdminOnRootNode(UUID userId) {
        Integer count = dsl
                .selectCount()
                .from(DSL.table("node_authorizations"))
                .where(DSL.field("user_id", UUID.class).eq(userId))
                .and(DSL.field("node_id", UUID.class).eq(
                        dsl.select(DSL.field("id", UUID.class))
                                .from(DSL.table("nodes"))
                                .where(DSL.field("parent_id").isNull())
                                .limit(1)))
                .and(DSL.field("auth_level::text", String.class).eq("admin"))
                .fetchOne(0, Integer.class);
        return count != null && count > 0;
    }
}
