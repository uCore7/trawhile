package com.trawhile.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * bucket4j rate limiting configuration.
 * A per-IP bucket is created on demand and stored in a ConcurrentHashMap.
 * Limits: 20 requests/second burst, 200 requests/minute sustained.
 */
@Configuration
public class RateLimitConfig {

    /** Per-IP bucket cache. Used by RateLimitFilter. */
    @Bean
    public ConcurrentHashMap<String, Bucket> rateLimitBuckets() {
        return new ConcurrentHashMap<>();
    }

    /** Bucket factory: called once per new IP. */
    @Bean
    public java.util.function.Supplier<Bucket> bucketFactory() {
        return () -> Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(20)
                .refillGreedy(20, Duration.ofSeconds(1))
                .build())
            .addLimit(Bandwidth.builder()
                .capacity(200)
                .refillGreedy(200, Duration.ofMinutes(1))
                .build())
            .build();
    }
}
