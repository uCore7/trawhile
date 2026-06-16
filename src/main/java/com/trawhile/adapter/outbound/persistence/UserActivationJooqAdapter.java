package com.trawhile.adapter.outbound.persistence;

import com.trawhile.port.outbound.persistence.UserActivationPort;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

@Component
public class UserActivationJooqAdapter implements UserActivationPort {

    private final DSLContext dsl;

    public UserActivationJooqAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void insertActiveUser(UUID id, String displayName, String email) {
        dsl.insertInto(DSL.table("users"))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("display_name", String.class), displayName)
                .set(DSL.field("email", String.class), email)
                .execute();
    }

    @Override
    public void refreshEmail(UUID userId, String email) {
        dsl.update(DSL.table("users"))
                .set(DSL.field("email", String.class), email)
                .where(DSL.field("id", UUID.class).eq(userId))
                .execute();
    }

    @Override
    public void deleteUserById(UUID userId) {
        dsl.deleteFrom(DSL.table("users"))
                .where(DSL.field("id", UUID.class).eq(userId))
                .execute();
    }
}
