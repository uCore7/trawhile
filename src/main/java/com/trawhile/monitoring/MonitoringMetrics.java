package com.trawhile.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MonitoringMetrics {

    private final MeterRegistry meterRegistry;
    private final JdbcTemplate jdbcTemplate;
    private final Counter dbTransactionErrorsCounter;

    public MonitoringMetrics(MeterRegistry meterRegistry, JdbcTemplate jdbcTemplate) {
        this.meterRegistry = meterRegistry;
        this.jdbcTemplate = jdbcTemplate;
        this.dbTransactionErrorsCounter = meterRegistry.counter("trawhile_db_transaction_errors_total");

        // Pre-register tagged metric families so they are visible before the first event occurs.
        meterRegistry.counter("trawhile_rate_limit_rejections_total", "endpoint", "unknown");
        meterRegistry.counter("trawhile_security_events_total", "event_type", "unknown");
        meterRegistry.counter("trawhile_oauth2_login_failures_total", "provider", "unknown");
        meterRegistry.counter("trawhile_mcp_tool_invocations_total", "tool", "unknown");

        Gauge.builder("trawhile_tracking_sessions_active", this, MonitoringMetrics::activeTrackingSessions)
            .register(meterRegistry);
    }

    public void recordRateLimitRejection(String endpoint) {
        meterRegistry.counter("trawhile_rate_limit_rejections_total", "endpoint", safeTagValue(endpoint)).increment();
    }

    public void recordSecurityEvent(String eventType) {
        meterRegistry.counter("trawhile_security_events_total", "event_type", safeTagValue(eventType)).increment();
    }

    public void recordOauth2LoginFailure(String provider) {
        meterRegistry.counter("trawhile_oauth2_login_failures_total", "provider", safeTagValue(provider)).increment();
    }

    public void recordMcpToolInvocation(String tool) {
        meterRegistry.counter("trawhile_mcp_tool_invocations_total", "tool", safeTagValue(tool)).increment();
    }

    public void recordDbTransactionError() {
        dbTransactionErrorsCounter.increment();
    }

    double activeTrackingSessions() {
        Integer active = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM time_records WHERE ended_at IS NULL",
            Integer.class
        );
        return active == null ? 0.0d : active.doubleValue();
    }

    private String safeTagValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
