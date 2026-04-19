package com.trawhile.repository;

import com.trawhile.domain.AuthLevel;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All recursive CTE authorization queries (Q1–Q4 from schema.sql).
 * Uses NamedParameterJdbcTemplate for full SQL control. Parameters bind values safely;
 * no dynamic identifiers are constructed from user input.
 */
@Repository
public class AuthorizationQueries {

    public record UserAuthorizationPathRow(UUID nodeId, String authorization, String nodePathJson) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public AuthorizationQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Q1: all node IDs visible to a user (grant on N = effective on N and all descendants).
     */
    public List<UUID> visibleNodeIds(UUID userId) {
        String sql = """
            WITH RECURSIVE visible AS (
              SELECT n.id FROM nodes n
              JOIN node_authorizations na ON na.node_id = n.id
              WHERE na.user_id = :userId
              UNION ALL
              SELECT n.id FROM nodes n
              JOIN visible v ON n.parent_id = v.id
            )
            SELECT id FROM visible
            """;
        return jdbc.queryForList(sql, Map.of("userId", userId), UUID.class);
    }

    /**
     * Q2: effective (highest) authorization a user has on a specific node (walks upward).
     * Returns null if the user has no authorization anywhere in the ancestor chain.
     */
    public AuthLevel effectiveAuthorization(UUID userId, UUID nodeId) {
        String sql = """
            WITH RECURSIVE ancestors AS (
              SELECT id, parent_id FROM nodes WHERE id = :nodeId
              UNION ALL
              SELECT n.id, n.parent_id FROM nodes n JOIN ancestors a ON n.id = a.parent_id
            )
            SELECT MAX(na.auth_level)::text
            FROM ancestors a
            JOIN node_authorizations na ON na.node_id = a.id
            WHERE na.user_id = :userId
            """;
        String result = jdbc.queryForObject(sql,
            Map.of("userId", userId, "nodeId", nodeId),
            String.class);
        return result != null ? AuthLevel.valueOf(result.toUpperCase()) : null;
    }

    /**
     * Q3: guard — returns true if the user has at least the required level on nodeId
     * or any of its ancestors.
     */
    public boolean hasAuthorization(UUID userId, UUID nodeId, AuthLevel required) {
        String sql = """
            WITH RECURSIVE ancestors AS (
              SELECT id, parent_id FROM nodes WHERE id = :nodeId
              UNION ALL
              SELECT n.id, n.parent_id FROM nodes n
              JOIN ancestors a ON n.id = a.parent_id
            )
            SELECT EXISTS (
              SELECT 1 FROM ancestors a
              JOIN node_authorizations na ON na.node_id = a.id
              WHERE na.user_id = :userId
              AND   na.auth_level >= CAST(:required AS auth_level)
            )
            """;
        return Boolean.TRUE.equals(jdbc.queryForObject(sql,
            Map.of("userId", userId, "nodeId", nodeId, "required", required.name().toLowerCase()),
            Boolean.class));
    }

    /**
     * Q4: all user IDs that have at least the required level on nodeId (via ancestor chain).
     */
    public List<UUID> usersWithAuthorization(UUID nodeId, AuthLevel required) {
        String sql = """
            WITH RECURSIVE ancestors AS (
              SELECT id, parent_id FROM nodes WHERE id = :nodeId
              UNION ALL
              SELECT n.id, n.parent_id FROM nodes n
              JOIN ancestors a ON n.id = a.parent_id
            )
            SELECT na.user_id
            FROM ancestors a
            JOIN node_authorizations na ON na.node_id = a.id
            GROUP BY na.user_id
            HAVING MAX(na.auth_level) >= CAST(:required AS auth_level)
            """;
        return jdbc.queryForList(sql,
            Map.of("nodeId", nodeId, "required", required.name().toLowerCase()),
            UUID.class);
    }

    /** Returns true if any System Admin exists (used for bootstrap detection). */
    public boolean systemAdminExists() {
        String sql = """
            SELECT EXISTS (
              SELECT 1 FROM node_authorizations na
              JOIN nodes n ON na.node_id = n.id
              WHERE n.parent_id IS NULL
              AND   na.auth_level = 'admin'
            )
            """;
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Map.of(), Boolean.class));
    }

    /** Full granted-node paths for SR-F009.F01 / SR-F046.F01, computed in SQL via recursive CTE. */
    public List<UserAuthorizationPathRow> userAuthorizationsWithPaths(UUID userId) {
        String sql = """
            WITH RECURSIVE node_paths AS (
              SELECT n.id,
                     n.parent_id,
                     ARRAY[n.id] AS path_ids,
                     ARRAY[n.name] AS path_names
              FROM nodes n
              WHERE n.parent_id IS NULL
              UNION ALL
              SELECT c.id,
                     c.parent_id,
                     np.path_ids || c.id,
                     np.path_names || c.name
              FROM nodes c
              JOIN node_paths np ON c.parent_id = np.id
            )
            SELECT na.node_id,
                   na.auth_level::text AS authorization,
                   jsonb_agg(
                     jsonb_build_object('id', p.node_id, 'name', p.node_name)
                     ORDER BY p.ordinality
                   )::text AS node_path
            FROM node_authorizations na
            JOIN node_paths np ON np.id = na.node_id
            CROSS JOIN LATERAL unnest(np.path_ids, np.path_names)
                WITH ORDINALITY AS p(node_id, node_name, ordinality)
            WHERE na.user_id = :userId
            GROUP BY na.node_id, na.auth_level, np.path_names
            ORDER BY array_to_string(np.path_names, ' / ')
            """;
        return jdbc.query(sql, Map.of("userId", userId), (rs, rowNum) ->
            new UserAuthorizationPathRow(
                rs.getObject("node_id", UUID.class),
                rs.getString("authorization"),
                rs.getString("node_path")
            )
        );
    }
}
