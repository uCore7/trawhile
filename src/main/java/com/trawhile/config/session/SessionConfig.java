package com.trawhile.config.session;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring Session configuration.
 *
 * <p>In production, Spring Session is backed by Redis (SR-01-F14.F01,
 * {@code spring.session.store-type=redis} in {@code application.yml}). The
 * Redis-backed session store is auto-configured by
 * {@code spring-session-data-redis} when a {@code RedisConnectionFactory} bean
 * is present.</p>
 *
 * <p>In test contexts, the test overrides {@code spring.session.store-type=simple}
 * (via {@code @TestPropertySource}) to avoid requiring a live Redis connection.
 * Spring Boot 4.x removed automatic {@code MapSessionRepository} registration
 * for the {@code simple} store type; this configuration provides the bean
 * explicitly when {@code spring.session.store-type=simple} is active.</p>
 *
 * <p>The {@link ConditionalOnMissingBean} guard ensures this bean does not
 * conflict with the Redis-backed {@code SessionRepository} in production.</p>
 */
@Configuration
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "simple")
@EnableSpringHttpSession
public class SessionConfig {

    /**
     * In-memory session repository used in test contexts
     * ({@code spring.session.store-type=simple}).
     *
     * <p>The default session timeout of 30 minutes is sufficient for test
     * purposes; it is not configurable here because this bean is only active
     * in test contexts.</p>
     */
    @Bean
    @ConditionalOnMissingBean(SessionRepository.class)
    public SessionRepository<?> mapSessionRepository() {
        MapSessionRepository repository =
                new MapSessionRepository(new ConcurrentHashMap<>());
        repository.setDefaultMaxInactiveInterval(Duration.ofMinutes(30));
        return repository;
    }
}
