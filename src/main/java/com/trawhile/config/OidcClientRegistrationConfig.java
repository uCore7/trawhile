package com.trawhile.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

import java.util.LinkedHashMap;

@Configuration
@EnableConfigurationProperties(OidcClientProperties.class)
public class OidcClientRegistrationConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(OidcClientProperties properties) {
        var registrations = new LinkedHashMap<String, ClientRegistration>();

        addGoogleRegistration(registrations, properties.getGoogle());
        addAppleRegistration(registrations, properties.getApple());
        addMicrosoftRegistration(registrations, properties.getMicrosoft());
        addKeycloakRegistration(registrations, properties.getKeycloak());

        return new InMemoryClientRegistrationRepository(registrations);
    }

    @Bean
    public OAuth2AuthorizedClientService authorizedClientService(ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    private void addGoogleRegistration(LinkedHashMap<String, ClientRegistration> registrations,
                                       OidcClientProperties.Provider provider) {
        if (!isEnabled(provider)) {
            return;
        }

        registrations.put("google", ClientRegistration.withRegistrationId("google")
            .clientId(provider.getClientId())
            .clientSecret(provider.getClientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://oauth2.googleapis.com/token")
            .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .issuerUri("https://accounts.google.com")
            .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .clientName("Google")
            .build());
    }

    private void addAppleRegistration(LinkedHashMap<String, ClientRegistration> registrations,
                                      OidcClientProperties.Provider provider) {
        if (!isEnabled(provider)) {
            return;
        }

        registrations.put("apple", ClientRegistration.withRegistrationId("apple")
            .clientId(provider.getClientId())
            .clientSecret(provider.getClientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "name", "email")
            .authorizationUri("https://appleid.apple.com/auth/authorize")
            .tokenUri("https://appleid.apple.com/auth/token")
            .jwkSetUri("https://appleid.apple.com/auth/keys")
            .issuerUri("https://appleid.apple.com")
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .clientName("Apple")
            .build());
    }

    private void addMicrosoftRegistration(LinkedHashMap<String, ClientRegistration> registrations,
                                          OidcClientProperties.MicrosoftProvider provider) {
        if (!isEnabled(provider)) {
            return;
        }

        String tenantId = hasText(provider.getTenantId()) ? provider.getTenantId().trim() : "common";
        String baseUri = "https://login.microsoftonline.com/" + tenantId;

        registrations.put("microsoft", ClientRegistration.withRegistrationId("microsoft")
            .clientId(provider.getClientId())
            .clientSecret(provider.getClientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri(baseUri + "/oauth2/v2.0/authorize")
            .tokenUri(baseUri + "/oauth2/v2.0/token")
            .jwkSetUri(baseUri + "/discovery/v2.0/keys")
            .issuerUri(baseUri + "/v2.0")
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .clientName("Microsoft")
            .build());
    }

    private void addKeycloakRegistration(LinkedHashMap<String, ClientRegistration> registrations,
                                         OidcClientProperties.Provider provider) {
        if (!isEnabled(provider)) {
            return;
        }

        String issuerUri = normalizeIssuerUri(provider.getIssuerUri());

        registrations.put("keycloak", ClientRegistration.withRegistrationId("keycloak")
            .clientId(provider.getClientId())
            .clientSecret(provider.getClientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri(issuerUri + "/protocol/openid-connect/auth")
            .tokenUri(issuerUri + "/protocol/openid-connect/token")
            .jwkSetUri(issuerUri + "/protocol/openid-connect/certs")
            .userInfoUri(issuerUri + "/protocol/openid-connect/userinfo")
            .issuerUri(issuerUri)
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .clientName("Keycloak")
            .build());
    }

    private boolean isEnabled(OidcClientProperties.Provider provider) {
        return provider != null && hasText(provider.getClientId());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeIssuerUri(String issuerUri) {
        if (!hasText(issuerUri)) {
            throw new IllegalStateException(
                "Keycloak is enabled but KEYCLOAK_ISSUER_URI is blank. " +
                "Set KEYCLOAK_ISSUER_URI to the realm URL, for example https://keycloak.example.com/realms/company.");
        }

        return issuerUri.endsWith("/") ? issuerUri.substring(0, issuerUri.length() - 1) : issuerUri;
    }
}
