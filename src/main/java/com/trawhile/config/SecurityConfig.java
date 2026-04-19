package com.trawhile.config;

import com.trawhile.security.OidcLoginSuccessHandler;
import com.trawhile.security.TrawhileOidcUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TrawhileOidcUserService oidcUserService;
    private final OidcLoginSuccessHandler loginSuccessHandler;

    public SecurityConfig(TrawhileOidcUserService oidcUserService,
                          OidcLoginSuccessHandler loginSuccessHandler) {
        this.oidcUserService = oidcUserService;
        this.loginSuccessHandler = loginSuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();

        http
            .sessionManagement(s -> s
                .sessionCreationPolicy(SessionCreationPolicy.ALWAYS))

            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/api/v1/about").permitAll()
                .requestMatchers("/api/v1/auth/providers").permitAll()
                .requestMatchers("/api/v1/auth/gdpr-notice").permitAll()
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
                    response.sendRedirect("/login?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8));
                }))

            .logout(logout -> logout
                .logoutUrl("/api/v1/auth/logout")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))

            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

            .headers(headers -> headers
                .frameOptions(f -> f.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'"))
                .referrerPolicy(r -> r
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));

        return http.build();
    }
}
