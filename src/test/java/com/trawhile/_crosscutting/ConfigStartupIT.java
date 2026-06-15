package com.trawhile._crosscutting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trawhile.BaseIT;
import com.trawhile.TrawhileApplication;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Integration coverage for the OIDC provider configuration boundary:
 * SR-00-C02.F01 startup validation and SR-00-C02.F02 provider discovery.
 */
@TestPropertySource(properties = {
    "spring.session.store-type=simple",
    "spring.data.redis.host=",
    "spring.data.redis.port=0",
    "spring.security.oauth2.client.registration.google.client-id=configured-google-client",
    "spring.security.oauth2.client.registration.google.client-secret=configured-google-secret"
})
class ConfigStartupIT extends BaseIT {

    private static final List<String> SUPPORTED_PROVIDERS =
            List.of("google", "apple", "microsoft", "keycloak");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate restTemplate;

    @BeforeEach
    void buildRestTemplate() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(
                    org.springframework.http.client.ClientHttpResponse response)
                    throws java.io.IOException {
                return false;
            }
        });
    }

    @Test
    @Tag("TE-00-C02.F01-01")
    void startupWithoutSupportedOidcClientId_failsWithDescriptiveConfigurationError() {

        Throwable startupFailure = catchThrowable(() -> {
            try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(
                    TrawhileApplication.class)
                    .web(WebApplicationType.SERVLET)
                    .run(startupFailureProbeArguments().toArray(String[]::new))) {
                // A successful context is the failure condition asserted below.
            }
        });

        assertThat(startupFailure)
                .as("Startup with blank client-id values for every supported OIDC "
                    + "provider must fail because SR-00-C02.F01 requires at least "
                    + "one configured provider; a successful context would violate "
                    + "the production startup invariant.")
                .isNotNull();

        String failureMessages = causeMessages(startupFailure);

        assertThat(failureMessages)
                .as("Startup failure must identify the OIDC client-id property family, "
                    + "not merely report an unrelated infrastructure failure.")
                .contains("spring.security.oauth2.client.registration.")
                .contains("client-id");

        assertThat(failureMessages)
                .as("Startup failure must name all supported OIDC registration IDs so "
                    + "operators know which provider keys are valid.")
                .contains("google")
                .contains("apple")
                .contains("microsoft")
                .contains("keycloak");

        assertThat(failureMessages)
                .as("The thrown startup failure must be caused by missing OIDC provider "
                    + "configuration, not by database, Redis, Flyway, jOOQ, Tomcat "
                    + "port binding, or an unrelated missing bean.")
                .containsIgnoringCase("oidc")
                .contains("client-id")
                .doesNotContainIgnoringCase("connection refused")
                .doesNotContainIgnoringCase("flyway")
                .doesNotContainIgnoringCase("jooq")
                .doesNotContainIgnoringCase("tomcat")
                .doesNotContainIgnoringCase("redis")
                .doesNotContainIgnoringCase("no qualifying bean");
    }

    @Test
    @Tag("TE-00-C02.F02-01")
    void authProvidersReturnsOnlyConfiguredProviders_withoutAuthentication() throws Exception {
        AtomicReference<ConfigurableApplicationContext> context = new AtomicReference<>();

        try {
            Throwable startupFailure = catchThrowable(() -> context.set(
                    startConfiguredProviderDiscoveryContext()));

            assertThat(startupFailure)
                    .as("Startup with one non-empty supported OIDC client-id and one "
                        + "blank supported client-id must succeed; blank supported "
                        + "registrations such as keycloak are unconfigured providers "
                        + "to exclude from GET /auth/providers, not fatal OAuth2 "
                        + "client registrations.")
                    .isNull();

            Integer runtimePort = context.get().getEnvironment()
                    .getProperty("local.server.port", Integer.class);
            assertThat(runtimePort)
                    .as("Random-port provider discovery context must publish its local server port.")
                    .isNotNull();

            ResponseEntity<String> response = restTemplate.getForEntity(
                    "http://localhost:" + runtimePort + "/auth/providers",
                    String.class);

            assertThat(response.getStatusCode().value())
                    .as("GET /auth/providers must succeed without credentials because "
                        + "spec/openapi.yaml declares security: [] for this endpoint; "
                        + "HTTP 401 or 403 would make the login page unable to discover "
                        + "configured OIDC providers.")
                    .isEqualTo(200);

            MediaType contentType = response.getHeaders().getContentType();
            assertThat(contentType)
                    .as("GET /auth/providers must return a JSON response body.")
                    .isNotNull();
            assertThat(contentType.isCompatibleWith(MediaType.APPLICATION_JSON))
                    .as("GET /auth/providers Content-Type must be compatible with application/json.")
                    .isTrue();

            String body = response.getBody() == null ? "" : response.getBody();
            JsonNode root = objectMapper.readTree(body);
            assertThat(root.isArray())
                    .as("GET /auth/providers response body must parse as a JSON array.")
                    .isTrue();

            List<String> providers = objectMapper.readValue(
                    body,
                    new TypeReference<>() {}
            );

            assertThat(providers)
                    .as("Provider discovery must include the supported registration ID whose "
                        + "client-id was non-empty at startup.")
                    .contains("google");
            assertThat(providers)
                    .as("Provider discovery must exclude supported registration IDs that were "
                        + "blank or absent at startup.")
                    .doesNotContain("keycloak", "apple", "microsoft");
        } finally {
            if (context.get() != null) {
                context.get().close();
            }
        }
    }

    private static List<String> startupFailureProbeArguments() {
        List<String> arguments = new ArrayList<>();
        arguments.add("--server.port=0");
        arguments.add("--management.server.port=0");
        arguments.add("--spring.session.store-type=simple");
        arguments.add("--spring.data.redis.host=");
        arguments.add("--spring.data.redis.port=0");
        arguments.add("--spring.datasource.url=" + POSTGRES.getJdbcUrl());
        arguments.add("--spring.datasource.username=" + POSTGRES.getUsername());
        arguments.add("--spring.datasource.password=" + POSTGRES.getPassword());
        for (String provider : SUPPORTED_PROVIDERS) {
            arguments.add("--spring.security.oauth2.client.registration."
                    + provider + ".client-id=");
        }
        return arguments;
    }

    private static ConfigurableApplicationContext startConfiguredProviderDiscoveryContext() {
        return new SpringApplicationBuilder(TrawhileApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(configuredProviderDiscoveryArguments().toArray(String[]::new));
    }

    private static List<String> configuredProviderDiscoveryArguments() {
        List<String> arguments = new ArrayList<>();
        arguments.add("--server.port=0");
        arguments.add("--management.server.port=0");
        arguments.add("--spring.session.store-type=simple");
        arguments.add("--spring.data.redis.host=");
        arguments.add("--spring.data.redis.port=0");
        arguments.add("--spring.datasource.url=" + POSTGRES.getJdbcUrl());
        arguments.add("--spring.datasource.username=" + POSTGRES.getUsername());
        arguments.add("--spring.datasource.password=" + POSTGRES.getPassword());
        arguments.add("--spring.security.oauth2.client.registration.google.client-id="
                + "configured-google-client");
        arguments.add("--spring.security.oauth2.client.registration.google.client-secret="
                + "configured-google-secret");
        arguments.add("--spring.security.oauth2.client.registration.keycloak.client-id=");
        return arguments;
    }

    private static String causeMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            messages.append(current.getClass().getName());
            if (current.getMessage() != null) {
                messages.append(": ").append(current.getMessage());
            }
            messages.append('\n');
            current = current.getCause();
        }
        return messages.toString();
    }
}
