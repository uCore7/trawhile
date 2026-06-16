package com.trawhile.adapter.outbound.persistence;

import com.trawhile.port.outbound.persistence.RootAdminLookupPort;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

@Component
public class RootAdminJooqAdapter implements RootAdminLookupPort {

    private static final UUID ROOT_NODE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final DSLContext dsl;

    public RootAdminJooqAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean anyAdminExists() {
        Integer count = dsl
                .selectCount()
                .from(DSL.table("node_authorizations"))
                .where(DSL.field("node_id", UUID.class).eq(ROOT_NODE_ID))
                .and(DSL.field("auth_level::text", String.class).eq("admin"))
                .fetchOne(0, Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void grantAdminOnRoot(UUID userId) {
        dsl.insertInto(DSL.table("node_authorizations"))
                .set(DSL.field("node_id", UUID.class), ROOT_NODE_ID)
                .set(DSL.field("user_id", UUID.class), userId)
                .set(DSL.field("auth_level", String.class),
                        DSL.field("'admin'::auth_level", String.class))
                .execute();
    }
}
