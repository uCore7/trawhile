package com.trawhile;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

public final class TestFixtures {

    public static final UUID ROOT_NODE_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    private TestFixtures() {
    }

    public static UUID insertUser(JdbcTemplate jdbc) {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id) VALUES (?)", userId);
        return userId;
    }

    public static UUID insertUserWithProfile(JdbcTemplate jdbc, String name) {
        UUID userId = insertUser(jdbc);
        jdbc.update(
            "INSERT INTO user_profile (id, user_id, name) VALUES (?, ?, ?)",
            UUID.randomUUID(),
            userId,
            name
        );
        return userId;
    }

    public static UUID insertNode(JdbcTemplate jdbc, UUID parentId, String name) {
        UUID nodeId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO nodes (id, parent_id, name, is_active, sort_order) VALUES (?, ?, ?, ?, ?)",
            nodeId,
            parentId,
            name,
            true,
            0
        );
        return nodeId;
    }

    public static void grantAuth(JdbcTemplate jdbc, UUID userId, UUID nodeId, String level) {
        jdbc.update(
            "INSERT INTO node_authorizations (id, node_id, user_id, auth_level) " +
                "VALUES (?, ?, ?, CAST(? AS auth_level))",
            UUID.randomUUID(),
            nodeId,
            userId,
            level.toLowerCase(Locale.ROOT)
        );
    }

    public static UUID insertPendingInvitation(JdbcTemplate jdbc, String email, UUID userId) {
        UUID invitationId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO pending_invitations (id, user_id, email) VALUES (?, ?, ?)",
            invitationId,
            userId,
            email
        );
        return invitationId;
    }

    public static UUID insertPendingInvitation(
        JdbcTemplate jdbc,
        String email,
        UUID userId,
        UUID invitedBy
    ) {
        UUID invitationId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO pending_invitations (id, user_id, email, invited_by) VALUES (?, ?, ?, ?)",
            invitationId,
            userId,
            email,
            invitedBy
        );
        return invitationId;
    }

    public static UUID insertTimeRecord(
        JdbcTemplate jdbc,
        UUID userId,
        UUID nodeId,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt
    ) {
        UUID timeRecordId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO time_records (id, user_id, node_id, started_at, ended_at, timezone) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            timeRecordId,
            userId,
            nodeId,
            startedAt,
            endedAt,
            "UTC"
        );
        return timeRecordId;
    }
}
