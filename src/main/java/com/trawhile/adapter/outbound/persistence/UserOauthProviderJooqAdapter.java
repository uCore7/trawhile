package com.trawhile.adapter.outbound.persistence;

import com.trawhile.port.outbound.persistence.UserOauthProviderLookupPort;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

@Component
public class UserOauthProviderJooqAdapter implements UserOauthProviderLookupPort {

    private final DSLContext dsl;

    public UserOauthProviderJooqAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<UUID> findUserId(String provider, String subject) {
        UUID userId = dsl
                .select(DSL.field("user_id", UUID.class))
                .from(DSL.table("user_oauth_providers"))
                .where(DSL.field("provider", String.class).eq(provider))
                .and(DSL.field("subject", String.class).eq(subject))
                .fetchOne(0, UUID.class);
        return Optional.ofNullable(userId);
    }

    @Override
    public void insert(UUID userId, String provider, String subject) {
        dsl.insertInto(DSL.table("user_oauth_providers"))
                .set(DSL.field("user_id", UUID.class), userId)
                .set(DSL.field("provider", String.class), provider)
                .set(DSL.field("subject", String.class), subject)
                .execute();
    }
}
