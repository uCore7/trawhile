package com.trawhile.config;

import com.trawhile.monitoring.MonitoringMetrics;
import com.trawhile.security.OidcLoginSuccessHandler;
import com.trawhile.security.TrawhileOidcUserService;
import com.trawhile.service.SecurityEventService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TrawhileOidcUserService oidcUserService;
    private final OidcLoginSuccessHandler loginSuccessHandler;
    private final SecurityEventService securityEventService;
    private final MonitoringMetrics monitoringMetrics;

    public SecurityConfig(TrawhileOidcUserService oidcUserService,
                          OidcLoginSuccessHandler loginSuccessHandler,
                          SecurityEventService securityEventService,
                          MonitoringMetrics monitoringMetrics) {
        this.oidcUserService = oidcUserService;
        this.loginSuccessHandler = loginSuccessHandler;
        this.securityEventService = securityEventService;
        this.monitoringMetrics = monitoringMetrics;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));

        http
            .sessionManagement(s -> s
                .sessionCreationPolicy(SessionCreationPolicy.ALWAYS))

            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/mcp"))

            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers(
                    PathPatternRequestMatcher.pathPattern("/actuator/health"),
                    PathPatternRequestMatcher.pathPattern("/actuator/prometheus")
                ).permitAll()
                .requestMatchers("/api/v1/about").permitAll()
                .requestMatchers("/api/v1/auth/providers").permitAll()
                .requestMatchers("/api/v1/auth/gdpr-notice").permitAll()
                .requestMatchers("/mcp").permitAll()
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/assets/**",
                    "/*.js",
                    "/*.css",
                    "/*.ico",
                    "/prerendered-routes.json",
                    "/3rdpartylicenses.txt",
                    "/openapi.yaml",
                    "/sbom.json"
                ).permitAll()
                .anyRequest().authenticated())

            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(u -> u
                    .oidcUserService(oidcUserService))  // Google + Apple (OIDC)
                .successHandler(loginSuccessHandler)
                .failureHandler((request, response, exception) -> {
                    String errorCode = exception instanceof OAuth2AuthenticationException oauthEx
                        ? oauthEx.getError().getErrorCode()
                        : "error";
                    String provider = oauth2ProviderFromCallback(request.getRequestURI());
                    monitoringMetrics.recordOauth2LoginFailure(provider);
                    securityEventService.log(
                        "OAUTH_LOGIN_FAILURE",
                        null,
                        java.util.Map.of(
                            "provider", provider,
                            "errorCode", errorCode
                        ),
                        request.getRemoteAddr()
                    );
                    response.sendRedirect("/login?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8));
                }))

            .logout(logout -> logout
                .logoutUrl("/api/v1/auth/logout")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))

            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

            .headers(headers -> headers
                .frameOptions(f -> f.deny())
                .contentTypeOptions(c -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'"))
                .referrerPolicy(r -> r
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));

        return http.build();
    }

    private String oauth2ProviderFromCallback(String requestUri) {
        String prefix = "/login/oauth2/code/";
        if (requestUri != null && requestUri.startsWith(prefix) && requestUri.length() > prefix.length()) {
            return requestUri.substring(prefix.length());
        }
        return "unknown";
    }

    /**
     * Forces deferred CSRF token creation so Spring Security writes the XSRF-TOKEN cookie
     * on normal GET responses for the SPA.
     */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
