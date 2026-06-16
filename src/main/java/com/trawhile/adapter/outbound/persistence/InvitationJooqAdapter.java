package com.trawhile.adapter.outbound.persistence;

import com.trawhile.port.outbound.persistence.InvitationPersistencePort;
import java.time.Instant;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * jOOQ-backed implementation of {@link InvitationPersistencePort}.
 *
 * <p>Uses lowercase, unquoted field and table names so PostgreSQL resolves them
 * case-insensitively. The generated jOOQ types use uppercase names from the
 * H2-based codegen and would render as quoted identifiers that PostgreSQL
 * treats as case-sensitive, causing mismatch with the lowercase schema names.</p>
 */
@Component
public class InvitationJooqAdapter implements InvitationPersistencePort {

    private final DSLContext dsl;

    public InvitationJooqAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean existsNonExpiredPendingInvitationByEmail(String email) {
        Integer count = dsl
                .selectCount()
                .from(DSL.table("pending_invitations"))
                .where(DSL.field("email", String.class).eq(email))
                .and(DSL.field("expires_at", Instant.class)
                        .greaterThan(DSL.field("NOW()", Instant.class)))
                .fetchOne(0, Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean existsNonAnonymisedUserByEmail(String email) {
        Integer count = dsl
                .selectCount()
                .from(DSL.table("users"))
                .where(DSL.field("email", String.class).eq(email))
                .and(DSL.field("anonymised_at").isNull())
                .fetchOne(0, Integer.class);
        return count != null && count > 0;
    }

    @Override
    public CreatedInvitationRow createPendingUserAndInvitation(String email, UUID invitedBy) {
        Field<UUID> idField = DSL.field("id", UUID.class);
        Field<String> displayNameField = DSL.field("display_name", String.class);
        Field<String> emailField = DSL.field("email", String.class);
        Field<UUID> userIdField = DSL.field("user_id", UUID.class);
        Field<UUID> invitedByField = DSL.field("invited_by", UUID.class);
        Field<Instant> invitedAtField = DSL.field("invited_at", Instant.class);
        Field<Instant> expiresAtField = DSL.field("expires_at", Instant.class);

        Record userRecord = dsl
                .insertInto(DSL.table("users"))
                .set(displayNameField, email)
                .set(emailField, email)
                .returning(idField)
                .fetchOne();

        if (userRecord == null) {
            throw new IllegalStateException("Failed to create pending user");
        }
        UUID userId = userRecord.get(idField);

        Record invitationRecord = dsl
                .insertInto(DSL.table("pending_invitations"))
                .set(userIdField, userId)
                .set(emailField, email)
                .set(invitedByField, invitedBy)
                .returning(idField, userIdField, emailField, invitedAtField, expiresAtField)
                .fetchOne();

        if (invitationRecord == null) {
            throw new IllegalStateException("Failed to create pending invitation");
        }

        return new CreatedInvitationRow(
                invitationRecord.get(idField),
                invitationRecord.get(userIdField),
                invitationRecord.get(emailField),
                invitationRecord.get(invitedAtField),
                invitationRecord.get(expiresAtField));
    }
}
