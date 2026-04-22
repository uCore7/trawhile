package com.trawhile;

import com.trawhile.lifecycle.ActivityPurgeJob;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsIT extends BaseIT {

    private static final String[] CUSTOM_METRICS = {
        "trawhile_purge_job_last_completed_seconds",
        "trawhile_purge_job_deleted_total",
        "trawhile_purge_job_failures_total",
        "trawhile_db_transaction_errors_total",
        "trawhile_rate_limit_rejections_total",
        "trawhile_security_events_total",
        "trawhile_oauth2_login_failures_total",
        "trawhile_sse_connections_active",
        "trawhile_tracking_sessions_active",
        "trawhile_mcp_tool_invocations_total"
    };

    @Autowired
    private ActivityPurgeJob activityPurgeJob;

    @LocalServerPort
    private int mainPort;

    @LocalManagementPort
    private int managementPort;

    @Test
    @Tag("TE-F059.F01-01")
    void prometheusEndpoint_onManagementPort_returnsJvmAndHttpMetrics() throws Exception {
        HttpResponseData managementResponse = fetch(managementPort, "/actuator/prometheus");

        assertThat(managementResponse.statusCode()).isEqualTo(200);
        assertThat(managementResponse.contentType()).startsWith("text/plain");
        assertThat(managementResponse.body()).contains("jvm_memory_used_bytes");

        HttpResponseData mainResponse = fetch(mainPort, "/actuator/prometheus");
        assertThat(mainResponse.statusCode()).isIn(401, 404);
    }

    @Test
    @Tag("TE-F059.F02-01")
    void customMetrics_allTenMetricsPresent_inPrometheusOutput() throws Exception {
        HttpResponseData response = fetch(managementPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        for (String metric : CUSTOM_METRICS) {
            assertThat(response.body()).contains(metric);
        }
    }

    @Test
    @Tag("TE-F059.F02-02")
    void purgeJobCompletionMetric_updatedAfterSuccessfulRun() throws Exception {
        activityPurgeJob.trigger();
        runActivityBatches();

        HttpResponseData response = fetch(managementPort, "/actuator/prometheus");
        Matcher matcher = Pattern.compile(
            "(?m)^trawhile_purge_job_last_completed_seconds\\{job_type=\"activity\"} ([0-9.eE+-]+)$"
        ).matcher(response.body());

        assertThat(matcher.find()).isTrue();
        assertThat(Double.parseDouble(matcher.group(1))).isGreaterThan(0.0d);
    }

    private void runActivityBatches() {
        for (int attempt = 0; attempt < 10; attempt++) {
            int[] deleted = activityPurgeJob.deleteBatch(
                jdbc.queryForObject(
                    "SELECT cutoff_date FROM purge_jobs WHERE job_type = 'activity'",
                    java.time.LocalDate.class
                )
            );
            assertThat(deleted).hasSize(2);
            if (deleted[0] == 0 && deleted[1] == 0) {
                return;
            }
        }
        throw new AssertionError("activity purge did not quiesce within 10 batch iterations");
    }

    private HttpResponseData fetch(int port, String path) throws Exception {
        HttpResponse<String> response = httpGet(port, path);
        return new HttpResponseData(
            response.statusCode(),
            response.headers().firstValue("Content-Type").orElse(""),
            response.body()
        );
    }

    private record HttpResponseData(int statusCode, String contentType, String body) {
    }
}
