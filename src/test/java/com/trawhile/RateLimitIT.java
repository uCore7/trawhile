package com.trawhile;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitIT extends BaseIT {

    @Autowired
    private ConcurrentHashMap<String, Bucket> rateLimitBuckets;

    @LocalServerPort
    private int port;

    @BeforeEach
    void resetRateLimitBuckets() {
        rateLimitBuckets.clear();
    }

    @Test
    @Tag("TE-C011.C01-01")
    void requestsWithinLimit_return200_thenExceedingLimit_returns429() throws Exception {
        String clientIp = "198.51.100.10";

        for (int requestNumber = 0; requestNumber < 20; requestNumber++) {
            assertThat(fetchProviders(clientIp).statusCode()).isEqualTo(200);
        }

        assertThat(fetchProviders(clientIp).statusCode()).isEqualTo(429);
    }

    @Test
    @Tag("TE-C011.C01-02")
    void rateLimitBreach_insertsSecurityEventRow() throws Exception {
        String clientIp = "198.51.100.11";
        int before = jdbc.queryForObject(
            "SELECT COUNT(*) FROM security_events WHERE event_type = 'RATE_LIMIT_BREACH'",
            Integer.class
        );

        for (int requestNumber = 0; requestNumber < 20; requestNumber++) {
            assertThat(fetchProviders(clientIp).statusCode()).isEqualTo(200);
        }

        assertThat(fetchProviders(clientIp).statusCode()).isEqualTo(429);

        int after = jdbc.queryForObject(
            "SELECT COUNT(*) FROM security_events WHERE event_type = 'RATE_LIMIT_BREACH'",
            Integer.class
        );
        assertThat(after).isEqualTo(before + 1);
    }

    private HttpResponse<String> fetchProviders(String clientIp) throws Exception {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/providers"))
            .header("X-Forwarded-For", clientIp)
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
