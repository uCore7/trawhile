package com.trawhile.config.oidc;

import java.util.List;
import org.springframework.core.env.Environment;

/**
 * Single source of truth for supported OIDC provider registration IDs.
 */
public final class SupportedOidcProviders {

    private static final String CLIENT_ID_PROPERTY_PREFIX =
            "spring.security.oauth2.client.registration.";

    private static final String CLIENT_ID_PROPERTY_SUFFIX = ".client-id";

    private static final List<String> REGISTRATION_IDS =
            List.of("google", "apple", "microsoft", "keycloak");

    private SupportedOidcProviders() {
    }

    public static List<String> registrationIds() {
        return REGISTRATION_IDS;
    }

    public static String clientIdProperty(String registrationId) {
        return CLIENT_ID_PROPERTY_PREFIX + registrationId + CLIENT_ID_PROPERTY_SUFFIX;
    }

    public static List<String> configuredRegistrationIds(Environment environment) {
        return REGISTRATION_IDS.stream()
                .filter(registrationId -> hasConfiguredClientId(environment, registrationId))
                .toList();
    }

    public static void validateAtLeastOneConfigured(Environment environment) {
        if (!configuredRegistrationIds(environment).isEmpty()) {
            return;
        }

        String properties = REGISTRATION_IDS.stream()
                .map(SupportedOidcProviders::clientIdProperty)
                .reduce((left, right) -> left + ", " + right)
                .orElse(CLIENT_ID_PROPERTY_PREFIX + "<provider>" + CLIENT_ID_PROPERTY_SUFFIX);

        throw new IllegalStateException(
                "OIDC provider configuration is invalid: at least one supported "
                + "provider client-id must be non-empty. Configure one of "
                + properties
                + ". Supported OIDC provider registration IDs: "
                + String.join(", ", REGISTRATION_IDS)
                + ".");
    }

    private static boolean hasConfiguredClientId(
            Environment environment,
            String registrationId
    ) {
        String value = environment.getProperty(clientIdProperty(registrationId));
        return value != null && !value.trim().isEmpty();
    }
}
