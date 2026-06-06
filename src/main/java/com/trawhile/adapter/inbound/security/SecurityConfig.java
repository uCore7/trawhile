package com.trawhile.adapter.inbound.security;

import com.trawhile.port.outbound.persistence.ApiKeyLookupPort;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Spring Security baseline composition.
 *
 * <p>Two authentication modes coexist (UR-00-C08):</p>
 * <ul>
 *   <li>OIDC-authenticated browser session (cookie-based, Spring Session in Redis).</li>
 *   <li>API key, presented as {@code Authorization: Bearer ...}.</li>
 * </ul>
 *
 * <p>CSRF (SR-00-C21.F01): the cookie-based session paths are CSRF-protected;
 * API-key-authenticated requests, which carry the {@code Authorization} header,
 * bypass the CSRF filter via the request matcher configured below.</p>
 *
 * <p>OIDC login is wired only when at least one {@code ClientRegistrationRepository}
 * is present in the context. The runtime "no OIDC providers configured" failure
 * is the responsibility of the SR-01-F12.F01 startup validator, which produces
 * a descriptive error naming the missing configuration property; this class
 * does not fail at bean instantiation time so that tests (and any future
 * deployment shape that uses a different auth flow) can load the context
 * without supplying stub OIDC clients.</p>
 *
 * <p>Endpoint auth-mode classification (SR-00-C08.F01 / SR-00-C08.F02):
 * <ul>
 *   <li><strong>Session-only</strong> ({@code /api/account/me/**},
 *       {@code /api/admin/**}): API-key bearer requests are rejected with
 *       HTTP 403.</li>
 *   <li><strong>MCP transport</strong> ({@code /api/mcp}): only API-key
 *       bearer authentication is accepted; OIDC-session-cookie requests are
 *       rejected with HTTP 401 before MCP dispatch.</li>
 *   <li><strong>Mixed-mode</strong> (all other {@code /api/*} paths): either
 *       authentication mode is accepted.</li>
 * </ul>
 * </p>
 */
@Configuration
public class SecurityConfig {

    /**
     * Matches requests whose {@code Authorization} header begins with {@code Bearer }.
     * Such requests carry an API key and bypass CSRF filtering (SR-00-C21.F01).
     */
    private static final RequestMatcher API_KEY_REQUEST = request -> {
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
    };

    /**
     * Matches session-only paths: {@code /api/account/me/**} and
     * {@code /api/admin/**}. Only OIDC-session auth is permitted on these
     * paths (SR-00-C08.F01).
     */
    private static final RequestMatcher SESSION_ONLY_PATHS = request -> {
        String path = request.getServletPath();
        return path.startsWith("/api/account/me")
                || path.startsWith("/api/admin/");
    };

    /**
     * Matches the MCP transport path {@code /api/mcp}. Only API-key bearer
     * auth is permitted here (SR-00-C08.F02).
     */
    private static final RequestMatcher MCP_PATH = request ->
            "/api/mcp".equals(request.getServletPath());

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        ApiKeyLookupPort apiKeyLookupPort,
        ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository
    ) throws Exception {

        ApiKeyAuthenticationFilter apiKeyFilter =
                new ApiKeyAuthenticationFilter(apiKeyLookupPort);

        http
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/login/**", "/oauth2/**").permitAll()
                // Management actuator endpoints: health is public (spec); prometheus is
                // served on the management port (not the public port) and accessible
                // without auth because the management port is network-restricted (UR-00-C12).
                .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                // /error is the Spring MVC error dispatcher path; permit it so that
                // unauthenticated-handler-not-found dispatches return 404 (not 403).
                .requestMatchers("/error").permitAll()
                // Session-only paths: reject API-key bearers with 403 (SR-00-C08.F01)
                .requestMatchers(SESSION_ONLY_PATHS).access((authentication, context) -> {
                    var auth = authentication.get();
                    if (auth instanceof ApiKeyAuthentication) {
                        return new AuthorizationDecision(false);
                    }
                    return new AuthorizationDecision(auth != null && auth.isAuthenticated());
                })
                // MCP path: accept only API-key bearer; session-authenticated users
                // are denied and the custom AccessDeniedHandler returns 401 (SR-00-C08.F02)
                .requestMatchers(MCP_PATH).access((authentication, context) -> {
                    var auth = authentication.get();
                    if (auth instanceof ApiKeyAuthentication) {
                        return new AuthorizationDecision(true);
                    }
                    return new AuthorizationDecision(false);
                })
                .anyRequest().authenticated())
            .csrf(csrf -> csrf
                // API-key-bearer requests bypass CSRF (SR-00-C21.F01): the
                // Authorization header is a CSRF-proof mechanism.
                .ignoringRequestMatchers(API_KEY_REQUEST)
                // The MCP transport is API-key-only; CSRF is disabled here so
                // that the auth-mode classification AccessDeniedHandler (which
                // returns 401 for session-cookie requests) fires AFTER the CSRF
                // filter rather than having CSRF return 403 first.
                .ignoringRequestMatchers(MCP_PATH)
            )
            // Custom AccessDeniedHandler: for the MCP path, a session-authenticated
            // user is denied with 401 (SR-00-C08.F02 requires 401, not 403, because
            // the rejection is about transport classification, not access control).
            // For all other paths the default 403 applies.
            .exceptionHandling(ex -> ex
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    if (MCP_PATH.matches(request)) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    } else {
                        new AccessDeniedHandlerImpl().handle(
                                request, response, accessDeniedException);
                    }
                })
                .authenticationEntryPoint((request, response, authException) -> {
                    // SR-00-C22.F01: generic 401 with no body content that could fingerprint
                    // the deployment. The full RFC 7807 Problem body shape lands with the
                    // SR-00-C18 brief; for now an empty body satisfies the no-disclosure rule.
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                })
            );

        if (clientRegistrationRepository.getIfAvailable() != null) {
            http.oauth2Login(login -> {
                // Success / failure handling is wired by the Auth flow adapter
                // (architecture §5.2.3) which classifies the first-callback outcome
                // per SR-01-F13.F01.
            });
        }

        return http.build();
    }
}
