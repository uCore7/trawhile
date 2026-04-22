package com.trawhile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import static org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE;
import static org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;

@SpringBootTest(
    classes = {TrackerApplication.class, BaseIT.TestOAuth2Configuration.class},
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "spring.main.allow-bean-definition-overriding=true"
)
public abstract class BaseIT {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();

    // Container is started once via static initializer and kept running for the entire JVM
    // lifetime. We intentionally bypass the @Testcontainers/@Container JUnit lifecycle so
    // that TC 2.x does not stop the container between test classes (which would invalidate
    // the cached Spring context's datasource URL).
    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:18");
        postgres.start();
    }

    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("BOOTSTRAP_ADMIN_EMAIL", () -> "bootstrap@example.com");
    }

    @BeforeEach
    void resetDatabase() {
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
        jdbc.execute("""
            TRUNCATE purge_jobs, mcp_tokens, quick_access, time_records, requests,
                security_events, node_authorizations, user_oauth_providers, user_profile,
                pending_invitations, users, nodes
            RESTART IDENTITY CASCADE
            """);
        jdbc.update(
            "INSERT INTO nodes (id, name, is_active, sort_order) VALUES (?, ?, ?, ?)",
            TestFixtures.ROOT_NODE_ID,
            "root",
            true,
            0
        );
        jdbc.batchUpdate(
            "INSERT INTO purge_jobs (job_type, status) VALUES (?, ?)",
            java.util.List.of(
                new Object[] {"activity", "idle"},
                new Object[] {"node", "idle"}
            )
        );
    }

    protected HttpResponse<String> httpGet(int port, String path) throws Exception {
        return httpGet(port, path, Map.of());
    }

    protected HttpResponse<String> httpGet(int port, String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .GET()
            .timeout(Duration.ofSeconds(5));
        headers.forEach(request::header);
        return HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOAuth2Configuration {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(googleRegistration());
        }

        private ClientRegistration googleRegistration() {
            return ClientRegistration.withRegistrationId("google")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .clientAuthenticationMethod(CLIENT_SECRET_BASIC)
                .authorizationGrantType(AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
        }
    }
}
