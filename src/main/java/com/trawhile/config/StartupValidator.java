package com.trawhile.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.stereotype.Component;

/**
 * Validates environment-level configuration at startup (SR-F065.F01).
 * Complements TrawhileConfig's @Validated checks with constraints that
 * require access to the Spring Security OAuth2 registration context.
 */
@Component
public class StartupValidator implements ApplicationRunner {

    // Optional: Spring Boot does not create this bean if no providers are configured.
    @Autowired(required = false)
    private ClientRegistrationRepository clientRegistrationRepository;

    @Override
    public void run(ApplicationArguments args) {
        boolean hasProviders = clientRegistrationRepository instanceof InMemoryClientRegistrationRepository repo
            && repo.iterator().hasNext();

        if (!hasProviders) {
            throw new IllegalStateException(
                "Startup validation failed (SR-F065.F01): at least one OIDC provider must be configured. " +
                "Set a non-empty client-id for at least one of: " +
                "GOOGLE_CLIENT_ID, APPLE_CLIENT_ID, MICROSOFT_CLIENT_ID, KEYCLOAK_CLIENT_ID " +
                "(the legacy SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_* variables are also accepted).");
        }
    }
}
