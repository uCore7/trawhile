package com.trawhile.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.trawhile.domain.SecurityEvent;
import com.trawhile.monitoring.MonitoringMetrics;
import com.trawhile.repository.SecurityEventRepository;
import com.trawhile.web.dto.ListSecurityEvents200Response;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SecurityEventService {

    private static final UUID ROOT_NODE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ANONYMISED_PLACEHOLDER = "Anonymised user";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    public record EventFilters(
        String eventType,
        UUID userId,
        OffsetDateTime from,
        OffsetDateTime to,
        int limit,
        int offset
    ) {
    }

    private final SecurityEventRepository securityEventRepository;
    private final AuthorizationService authorizationService;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MonitoringMetrics monitoringMetrics;

    public SecurityEventService(SecurityEventRepository securityEventRepository,
                                AuthorizationService authorizationService,
                                NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                JdbcTemplate jdbcTemplate,
                                ObjectMapper objectMapper,
                                MonitoringMetrics monitoringMetrics) {
        this.securityEventRepository = securityEventRepository;
        this.authorizationService = authorizationService;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.monitoringMetrics = monitoringMetrics;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String eventType, UUID userId, Map<String, Object> metadata) {
        saveEvent(userId, eventType, writeJson(metadata), null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String eventType, UUID userId, Map<String, Object> metadata, String ipAddress) {
        saveEvent(userId, eventType, writeJson(metadata), ipAddress);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, String eventType, String details, String ipAddress) {
        saveEvent(userId, eventType, details, ipAddress);
    }

    @Transactional(readOnly = true)
    public ListSecurityEvents200Response listEvents(UUID actingUserId, EventFilters filters) {
        authorizationService.requireAdmin(actingUserId, ROOT_NODE_ID);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("limit", filters.limit())
            .addValue("offset", filters.offset());

        StringBuilder whereClause = new StringBuilder("""
            FROM security_events se
            LEFT JOIN user_profile up ON up.user_id = se.user_id
            LEFT JOIN pending_invitations pi ON pi.user_id = se.user_id
            WHERE 1 = 1
            """);
        if (filters.eventType() != null) {
            whereClause.append(" AND se.event_type = :eventType");
            parameters.addValue("eventType", filters.eventType());
        }
        if (filters.userId() != null) {
            whereClause.append(" AND se.user_id = :userId");
            parameters.addValue("userId", filters.userId());
        }
        if (filters.from() != null) {
            whereClause.append(" AND se.occurred_at >= :from");
            parameters.addValue("from", filters.from());
        }
        if (filters.to() != null) {
            whereClause.append(" AND se.occurred_at <= :to");
            parameters.addValue("to", filters.to());
        }

        Integer total = namedParameterJdbcTemplate.queryForObject(
            "SELECT COUNT(*) " + whereClause,
            parameters,
            Integer.class
        );

        String query = """
            SELECT se.id,
                   se.user_id,
                   COALESCE(
                     up.name,
                     pi.email,
                     CASE WHEN se.user_id IS NULL THEN NULL ELSE '%s' END
                   ) AS user_name,
                   se.event_type,
                   se.details::text AS details,
                   se.ip_address,
                   se.occurred_at
            """.formatted(ANONYMISED_PLACEHOLDER)
            + whereClause
            + " ORDER BY se.occurred_at DESC, se.id DESC LIMIT :limit OFFSET :offset";

        List<com.trawhile.web.dto.SecurityEvent> items = namedParameterJdbcTemplate.query(
            query,
            parameters,
            (rs, rowNum) -> {
                com.trawhile.web.dto.SecurityEvent item =
                    new com.trawhile.web.dto.SecurityEvent(
                        rs.getObject("id", UUID.class),
                        com.trawhile.web.dto.SecurityEvent.EventTypeEnum.fromValue(rs.getString("event_type")),
                        rs.getObject("occurred_at", OffsetDateTime.class)
                    );
                item.setUserId(rs.getObject("user_id", UUID.class));
                item.setUserName(rs.getString("user_name"));
                item.setDetails(parseJsonMap(rs.getString("details")));
                item.setIpAddress(rs.getString("ip_address"));
                return item;
            }
        );

        return new ListSecurityEvents200Response(items, total == null ? 0 : total);
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "${trawhile.timezone:UTC}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteOldEvents() {
        jdbcTemplate.update(
            "DELETE FROM security_events WHERE occurred_at < NOW() - INTERVAL '90 days'"
        );
    }

    private void saveEvent(UUID userId, String eventType, String details, String ipAddress) {
        jdbcTemplate.update(
            """
                INSERT INTO security_events (user_id, event_type, details, ip_address, occurred_at)
                VALUES (?, ?, CAST(? AS jsonb), ?, ?)
                """,
            userId,
            eventType,
            details,
            ipAddress,
            OffsetDateTime.now()
        );
        monitoringMetrics.recordSecurityEvent(eventType);
    }

    private String writeJson(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to serialize security event metadata", e);
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to parse security event details", e);
        }
    }
}
