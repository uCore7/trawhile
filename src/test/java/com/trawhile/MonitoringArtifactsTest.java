package com.trawhile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class MonitoringArtifactsTest {

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

    @Test
    @Tag("TE-F059.F03-01")
    void prometheusConfigFile_exists_andIsValidYaml() {
        Artifact artifact = readArtifact(
            "monitoring/prometheus.yml",
            "monitoring/prometheus-scrape-config.yml"
        );

        Object parsed = new Yaml().load(artifact.content());
        assertThat(parsed).as("YAML content from %s should parse successfully", artifact.logicalPath()).isNotNull();
    }

    @Test
    @Tag("TE-F059.F03-02")
    void alertingRulesFile_exists_andContainsRequiredAlerts() {
        Artifact artifact = readArtifact("monitoring/alerting-rules.yml");

        assertThat(artifact.content()).contains("PurgeJobStale");
        assertThat(artifact.content()).contains("DatabaseErrors");
        assertThat(artifact.content()).contains("HighErrorRate");
        assertThat(artifact.content()).contains("InstanceDown");
    }

    @Test
    @Tag("TE-F059.F03-03")
    void grafanaDashboard_exists_andIsValidJson_andReferencesAllCustomMetrics() throws Exception {
        Artifact artifact = readArtifact("monitoring/grafana-dashboard.json");

        new ObjectMapper().readTree(artifact.content());
        for (String metric : CUSTOM_METRICS) {
            assertThat(artifact.content()).contains(metric);
        }
    }

    private Artifact readArtifact(String... candidates) {
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) {
                try {
                    return new Artifact(candidate, Files.readString(path));
                } catch (IOException e) {
                    throw new AssertionError("Failed to read " + candidate, e);
                }
            }

            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(candidate)) {
                if (inputStream != null) {
                    try {
                        return new Artifact(
                            candidate,
                            new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                        );
                    } catch (IOException e) {
                        throw new AssertionError("Failed to read classpath resource " + candidate, e);
                    }
                }
            } catch (IOException e) {
                throw new AssertionError("Failed to close stream for " + candidate, e);
            }
        }

        fail("Expected monitoring artifact at one of: %s", Arrays.toString(candidates));
        throw new IllegalStateException("unreachable");
    }

    private record Artifact(String logicalPath, String content) {
    }
}
