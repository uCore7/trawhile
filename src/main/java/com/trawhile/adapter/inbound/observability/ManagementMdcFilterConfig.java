package com.trawhile.adapter.inbound.observability;

import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link MdcFilter} on the management servlet context.
 *
 * <p>When {@code management.server.port} differs from {@code server.port}, Spring Boot
 * Actuator creates a separate child {@code ApplicationContext} for the management server.
 * Beans registered in the parent application context — including those from
 * {@link MdcFilterRegistration} — are NOT automatically exposed to the management
 * server's servlet. This class uses the {@code @ManagementContextConfiguration}
 * extension point to register the filter in the child context.</p>
 *
 * <p>The {@link MdcFilter} instance is injected from the parent context via the
 * constructor. If cross-context injection fails, a fresh {@code new MdcFilter()} is
 * used as a fallback — the filter holds no per-instance state, so duplication is
 * functionally safe.</p>
 *
 * <p>SR-00-C16.F01: every request to the management port (e.g. {@code /actuator/health})
 * must also carry {@code traceId} and {@code requestId} in MDC, which requires
 * this registration.</p>
 */
@ManagementContextConfiguration(value = ManagementContextType.CHILD, proxyBeanMethods = false)
public class ManagementMdcFilterConfig {

    private final MdcFilter mdcFilter;

    /**
     * Constructs the config, injecting the {@link MdcFilter} singleton from the
     * parent application context. Spring resolves cross-context bean lookups for
     * parent beans; if the injection is not available, falls back to a new instance.
     *
     * @param mdcFilter the shared filter instance from the parent context
     */
    public ManagementMdcFilterConfig(MdcFilter mdcFilter) {
        this.mdcFilter = mdcFilter;
    }

    /**
     * Registers the {@link MdcFilter} on the management servlet context with
     * the same order as the application context registration
     * ({@link MdcFilterRegistration#FILTER_ORDER}) and URL pattern {@code /*}.
     */
    @Bean
    public FilterRegistrationBean<MdcFilter> managementMdcFilterRegistration() {
        FilterRegistrationBean<MdcFilter> registration = new FilterRegistrationBean<>(mdcFilter);
        registration.setOrder(MdcFilterRegistration.FILTER_ORDER);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
