package com.trawhile.adapter.outbound.persistence;

import com.trawhile.port.outbound.persistence.PendingInvitationLookupPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

@Component
public class PendingInvitationJooqAdapter implements PendingInvitationLookupPort {

    private final DSLContext dsl;

    public PendingInvitationJooqAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<UUID> findUserIdByEmail(String email) {
        UUID userId = dsl
                .select(DSL.field("user_id", UUID.class))
                .from(DSL.table("pending_invitations"))
                .where(DSL.field("email", String.class).eq(email))
                .and(DSL.field("expires_at", Instant.class)
                        .greaterThan(DSL.field("NOW()", Instant.class)))
                .fetchOne(0, UUID.class);
        return Optional.ofNullable(userId);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        dsl.deleteFrom(DSL.table("pending_invitations"))
                .where(DSL.field("user_id", UUID.class).eq(userId))
                .execute();
    }
}
