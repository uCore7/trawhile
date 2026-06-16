package com.trawhile.adapter.outbound.persistence;

import com.trawhile.port.outbound.persistence.NodeAuthorizationPersistencePort;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

@Component
public class NodeAuthorizationJooqAdapter implements NodeAuthorizationPersistencePort {

    private final DSLContext dsl;

    public NodeAuthorizationJooqAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void deleteAllForUser(UUID userId) {
        dsl.deleteFrom(DSL.table("node_authorizations"))
                .where(DSL.field("user_id", UUID.class).eq(userId))
                .execute();
    }
}
