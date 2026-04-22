package com.trawhile.config;

import com.trawhile.monitoring.MonitoringMetrics;
import com.trawhile.service.SecurityEventService;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Applies per-IP rate limiting before the Spring Security filter chain. */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Bucket> buckets;
    private final Supplier<Bucket> bucketFactory;
    private final SecurityEventService securityEventService;
    private final MonitoringMetrics monitoringMetrics;

    public RateLimitFilter(ConcurrentHashMap<String, Bucket> buckets,
                           Supplier<Bucket> bucketFactory,
                           SecurityEventService securityEventService,
                           MonitoringMetrics monitoringMetrics) {
        this.buckets = buckets;
        this.bucketFactory = bucketFactory;
        this.securityEventService = securityEventService;
        this.monitoringMetrics = monitoringMetrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> bucketFactory.get());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
            monitoringMetrics.recordRateLimitRejection(normalizeEndpoint(request));
            securityEventService.log(
                "RATE_LIMIT_BREACH",
                null,
                java.util.Map.of("endpoint", normalizeEndpoint(request)),
                ip
            );
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !(path.startsWith("/api/") || path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/"));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Take only the first IP (leftmost = original client; Caddy sets this)
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String normalizeEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || path.isBlank() ? "unknown" : path;
    }
}
