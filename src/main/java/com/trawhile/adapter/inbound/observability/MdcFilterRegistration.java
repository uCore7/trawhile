package com.trawhile.adapter.inbound.observability;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link MdcFilter} on the application servlet context.
 *
 * <p>Filter ordering rationale (lower integer = runs first in the Tomcat chain):
 * <ol>
 *   <li>{@code ServerHttpObservationFilter} at {@link Ordered#HIGHEST_PRECEDENCE} + 1 —
 *       starts the Micrometer observation and opens the Brave scope, which puts
 *       {@code traceId} into SLF4J MDC via {@code MDCScopeDecorator}.</li>
 *   <li>{@code SessionRepositoryFilter} at {@link Ordered#HIGHEST_PRECEDENCE} + 50 —
 *       wraps the {@link jakarta.servlet.http.HttpServletRequest} so that
 *       {@link jakarta.servlet.http.HttpServletRequest#getSession(boolean)} returns
 *       the Spring Session rather than the raw Tomcat session.</li>
 *   <li>{@code MdcFilter} at {@link Ordered#HIGHEST_PRECEDENCE} + 51 — runs <em>after</em>
 *       the Spring Session filter so that the session is available when we call
 *       {@code request.getSession(false)}, and <em>inside</em> the Brave scope so that
 *       the Brave span log events emitted by downstream sub-observations (e.g.
 *       Spring Security authorization) carry all four MDC keys simultaneously.</li>
 *   <li>Spring Security {@code FilterChainProxy} at {@code -100} — runs after
 *       {@code MdcFilter}; by the time any security filter logs or sub-observations
 *       finish, {@code requestId}, {@code sessionId}, and {@code actorId} are already
 *       set.</li>
 * </ol>
 * </p>
 *
 * <p>Note: {@link MdcFilter} must also be registered on the management servlet
 * context (see {@link ManagementMdcFilterConfig}) because Spring Boot builds a
 * separate child {@code ApplicationContext} for the management server when
 * {@code management.server.port} differs from {@code server.port}.</p>
 */
@Configuration
public class MdcFilterRegistration {

    /**
     * Order used for both the application and management servlet context registrations.
     * Must be greater than {@code SessionRepositoryFilter.DEFAULT_ORDER}
     * ({@link Ordered#HIGHEST_PRECEDENCE} + 50) so that Spring Session has already
     * wrapped the request, and less than Spring Security's
     * {@code FilterChainProxy} order ({@code -100}) so that the MDC is populated
     * before any security sub-observation fires.
     */
    static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 51;

    /**
     * Exposes {@link MdcFilter} as a singleton bean so it can be shared between
     * the application servlet context and the management servlet context.
     */
    @Bean
    public MdcFilter mdcFilter() {
        return new MdcFilter();
    }

    /**
     * Registers the {@link MdcFilter} singleton on the application servlet context
     * with {@link #FILTER_ORDER} and URL pattern {@code /*}.
     */
    @Bean
    public FilterRegistrationBean<MdcFilter> mdcFilterRegistrationBean(MdcFilter mdcFilter) {
        FilterRegistrationBean<MdcFilter> registration = new FilterRegistrationBean<>(mdcFilter);
        registration.setOrder(FILTER_ORDER);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
