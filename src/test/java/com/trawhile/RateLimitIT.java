package com.trawhile;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitIT extends BaseIT {

    @Autowired
    private ConcurrentHashMap<String, Bucket> rateLimitBuckets;

    @BeforeEach
    void resetRateLimitBuckets() {
        rateLimitBuckets.clear();
    }

    @Test
    @Tag("TE-C011.C01-01")
    void requestsWithinLimit_return200_thenExceedingLimit_returns429() throws Exception {
        String clientIp = "198.51.100.10";

        for (int requestNumber = 0; requestNumber < 20; requestNumber++) {
            mvc.perform(get("/api/v1/auth/providers")
                            .header("X-Forwarded-For", clientIp))
                .andExpect(status().isOk());
        }

        mvc.perform(get("/api/v1/auth/providers")
                        .header("X-Forwarded-For", clientIp))
            .andExpect(status().isTooManyRequests());
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
            mvc.perform(get("/api/v1/auth/providers")
                            .header("X-Forwarded-For", clientIp))
                .andExpect(status().isOk());
        }

        mvc.perform(get("/api/v1/auth/providers")
                        .header("X-Forwarded-For", clientIp))
            .andExpect(status().isTooManyRequests());

        int after = jdbc.queryForObject(
            "SELECT COUNT(*) FROM security_events WHERE event_type = 'RATE_LIMIT_BREACH'",
            Integer.class
        );
        assertThat(after).isEqualTo(before + 1);
    }
}
