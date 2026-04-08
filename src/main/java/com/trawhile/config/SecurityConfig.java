package com.trawhile.config;

import com.trawhile.security.OAuth2LoginSuccessHandler;
import com.trawhile.security.OAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final OAuth2UserService oAuth2UserService;
    private final OAuth2LoginSuccessHandler loginSuccessHandler;

    public SecurityConfig(OAuth2UserService oAuth2UserService,
                          OAuth2LoginSuccessHandler loginSuccessHandler) {
        this.oAuth2UserService = oAuth2UserService;
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
                .requestMatchers("/index.html", "/", "/assets/**", "/*.js", "/*.css", "/*.ico").permitAll()
                .anyRequest().authenticated())

            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(u -> u.userService(oAuth2UserService))
                .successHandler(loginSuccessHandler))

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
                    .policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self'"))
                .referrerPolicy(r -> r
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));

        return http.build();
    }
}
