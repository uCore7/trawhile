package com.trawhile._crosscutting;

import static org.assertj.core.api.Assertions.assertThat;

import com.trawhile.BaseIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;

/**
 * Integration tests for SR-01-F10.F01: the Prometheus management endpoint is
 * exposed on the dedicated Spring Boot management port (distinct from the
 * public 8080), serves a Prometheus exposition payload, and includes the
 * standard Spring Boot Actuator metric families.
 *
 * <p>Traceability: TE-01-F10.F01-01 → SR-01-F10.F01 → UR-01-F10</p>
 */
class MetricsIT extends BaseIT {

    @LocalManagementPort
    private int managementPort;

    /**
     * TE-01-F10.F01-01: GET /actuator/prometheus on the management port returns
     * HTTP 200 with a text/plain Prometheus exposition payload that contains at
     * least one standard Spring Boot metric series.
     */
    @Test
    @Tag("TE-01-F10.F01-01")
    void prometheusEndpointReachable_andExposesStandardMetricSeries() {
        // Use a no-op error handler so 4xx/5xx responses are returned as
        // ResponseEntity rather than thrown as exceptions, enabling AssertJ
        // assertions on the status code with a clear failure message.
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response)
                    throws java.io.IOException {
                return false;
            }
        });

        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/prometheus",
            String.class
        );

        assertThat(response.getStatusCode().value())
            .as("HTTP status for /actuator/prometheus")
            .isEqualTo(200);

        assertThat(response.getHeaders().getContentType())
            .as("Content-Type header")
            .isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
            .as("Content-Type must start with text/plain (Prometheus exposition format)")
            .startsWith("text/plain");

        String body = response.getBody();
        assertThat(body)
            .as("Response body must contain at least one standard Spring Boot metric series")
            .satisfiesAnyOf(
                b -> assertThat(b).contains("jvm_memory_used_bytes"),
                b -> assertThat(b).contains("process_cpu_usage"),
                b -> assertThat(b).contains("http_server_requests_seconds_count")
            );
    }
}
