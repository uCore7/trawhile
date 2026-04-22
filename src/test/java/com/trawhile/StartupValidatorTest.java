package com.trawhile;

import com.trawhile.config.StartupValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE;
import static org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC;

class StartupValidatorTest {

    @Test
    @Tag("TE-F065.F01-01")
    void atLeastOneProviderConfigured_runCompletesWithoutThrowing() {
        StartupValidator validator = new StartupValidator();
        ReflectionTestUtils.setField(
            validator,
            "clientRegistrationRepository",
            new InMemoryClientRegistrationRepository(googleRegistration())
        );

        assertThatNoException()
            .isThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    @Tag("TE-F065.F01-01")
    void zeroProvidersConfigured_runThrowsIllegalStateException() {
        StartupValidator validator = new StartupValidator();
        ClientRegistrationRepository emptyRepository = new ClientRegistrationRepository() {
            @Override
            public ClientRegistration findByRegistrationId(String registrationId) {
                return null;
            }
        };
        ReflectionTestUtils.setField(validator, "clientRegistrationRepository", emptyRepository);

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Tag("TE-F065.F01-04")
    void startupValidation_errorMessageIdentifiesInvalidPropertyOrConstraint() {
        StartupValidator validator = new StartupValidator();
        ClientRegistrationRepository emptyRepository = new ClientRegistrationRepository() {
            @Override
            public ClientRegistration findByRegistrationId(String registrationId) {
                return null;
            }
        };
        ReflectionTestUtils.setField(validator, "clientRegistrationRepository", emptyRepository);

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Startup validation failed (SR-F065.F01)")
            .hasMessageContaining("at least one OIDC provider must be configured")
            .hasMessageContaining("GOOGLE_CLIENT_ID");
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
