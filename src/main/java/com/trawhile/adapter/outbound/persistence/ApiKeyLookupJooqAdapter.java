package com.trawhile.adapter.outbound.persistence;

import com.trawhile.port.outbound.persistence.ApiKeyLookupPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * jOOQ-backed implementation of {@link ApiKeyLookupPort} for SR-00-C08 auth-mode
 * classification.
 *
 * <p>Queries the {@code api_keys} table for a row whose {@code key_hash} matches the
 * supplied SHA-256 hex digest and which is both unrevoked and unexpired. Returns
 * an {@link ApiKeyLookupPort.ApiKeyPrincipal} when found; {@link Optional#empty()}
 * otherwise.</p>
 *
 * <p>No raw {@code JdbcTemplate} is used; persistence is jOOQ-backed only per
 * AGENTS.md anti-patterns and SR-08-F03.C02.</p>
 *
 * <p>Note: The jOOQ code generator uses an H2-based DDL database which uppercases
 * all identifiers. At runtime against PostgreSQL the generated uppercase names
 * would be rendered as quoted identifiers ({@code "API_KEYS"}) which do not
 * match the lowercase table names created by the Flyway migration. This adapter
 * uses {@link DSL#table(String)} and {@link DSL#field(String)} with lowercase
 * names to produce unquoted SQL identifiers that PostgreSQL resolves correctly
 * via its case-folding rule.</p>
 */
@Component
public class ApiKeyLookupJooqAdapter implements ApiKeyLookupPort {

    private final DSLContext dsl;

    public ApiKeyLookupJooqAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * {@inheritDoc}
     *
     * <p>SQL shape:
     * <pre>
     * SELECT id, user_id, scope_node_id, scope_level::text
     * FROM api_keys
     * WHERE key_hash = :hash
     *   AND revoked_at IS NULL
     *   AND expires_at &gt; NOW()
     * </pre>
     * </p>
     */
    @Override
    public Optional<ApiKeyPrincipal> findValidByHash(String sha256HexHash) {
        // Use lowercase, unquoted field/table names so PostgreSQL resolves them
        // case-insensitively. The generated jOOQ types use uppercase names from
        // the H2-based codegen and would be rendered as quoted "API_KEYS" which
        // PostgreSQL treats as case-sensitive.
        org.jooq.Field<UUID> idField = DSL.field("id", UUID.class);
        org.jooq.Field<UUID> userIdField = DSL.field("user_id", UUID.class);
        org.jooq.Field<UUID> scopeNodeIdField = DSL.field("scope_node_id", UUID.class);
        org.jooq.Field<String> scopeLevelField = DSL.field("scope_level::text", String.class);
        org.jooq.Field<String> keyHashField = DSL.field("key_hash", String.class);
        org.jooq.Field<Instant> expiresAtField = DSL.field("expires_at", Instant.class);
        org.jooq.Field<Instant> revokedAtField = DSL.field("revoked_at", Instant.class);

        var record = dsl
                .select(idField, userIdField, scopeNodeIdField, scopeLevelField)
                .from(DSL.table("api_keys"))
                .where(keyHashField.eq(sha256HexHash))
                .and(revokedAtField.isNull())
                .and(expiresAtField.greaterThan(DSL.field("NOW()", Instant.class)))
                .fetchOne();

        if (record == null) {
            return Optional.empty();
        }

        return Optional.of(new ApiKeyPrincipal(
                record.get(idField),
                record.get(userIdField),
                record.get(scopeNodeIdField),
                record.get(scopeLevelField)));
    }
}
