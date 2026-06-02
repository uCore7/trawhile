package com.trawhile.adapter.inbound.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
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
 * <p>Endpoint scope (UR-00-C08 / SR-00-C08.F01): session-only endpoints under
 * {@code /api/account/me/**} and {@code /api/admin/**} reject API-key bearers;
 * mixed-mode endpoints accept either. The runtime classification is enforced by
 * the cluster-specific service entry checks (architecture §6.2).</p>
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/login/**", "/oauth2/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated())
            .oauth2Login(login -> {
                // Success / failure handling is wired by the Auth flow adapter
                // (architecture §5.2.3) which classifies the first-callback outcome
                // per SR-01-F13.F01.
            })
            .csrf(csrf -> csrf.ignoringRequestMatchers(API_KEY_REQUEST));
        return http.build();
    }
}
