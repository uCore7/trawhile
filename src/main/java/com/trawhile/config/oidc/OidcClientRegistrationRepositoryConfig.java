package com.trawhile.config.oidc;

import java.util.Map;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientPropertiesMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration(proxyBeanMethods = false)
public class OidcClientRegistrationRepositoryConfig {

    @Bean
    @ConditionalOnMissingBean(ClientRegistrationRepository.class)
    public ClientRegistrationRepository clientRegistrationRepository(
            Environment environment
    ) {
        OAuth2ClientProperties properties = Binder.get(environment)
                .bind("spring.security.oauth2.client", OAuth2ClientProperties.class)
                .orElse(new OAuth2ClientProperties());

        properties.getRegistration().entrySet()
                .removeIf(entry -> isBlank(entry.getValue().getClientId()));

        Map<String, ClientRegistration> registrations =
                new OAuth2ClientPropertiesMapper(properties).asClientRegistrations();

        SupportedOidcProviders.validateAtLeastOneConfigured(environment);
        return new InMemoryClientRegistrationRepository(registrations);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
