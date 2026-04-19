package com.trawhile.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OIDC provider settings. Providers with a blank client-id are treated as disabled.
 * This keeps optional providers out of Spring Security's registration repository
 * while still allowing startup validation to enforce "at least one provider".
 */
@ConfigurationProperties(prefix = "trawhile.oidc")
public class OidcClientProperties {

    private Provider google = new Provider();
    private Provider apple = new Provider();
    private MicrosoftProvider microsoft = new MicrosoftProvider();
    private Provider keycloak = new Provider();

    public Provider getGoogle() {
        return google;
    }

    public void setGoogle(Provider google) {
        this.google = google;
    }

    public Provider getApple() {
        return apple;
    }

    public void setApple(Provider apple) {
        this.apple = apple;
    }

    public MicrosoftProvider getMicrosoft() {
        return microsoft;
    }

    public void setMicrosoft(MicrosoftProvider microsoft) {
        this.microsoft = microsoft;
    }

    public Provider getKeycloak() {
        return keycloak;
    }

    public void setKeycloak(Provider keycloak) {
        this.keycloak = keycloak;
    }

    public static class Provider {
        private String clientId = "";
        private String clientSecret = "";
        private String issuerUri = "";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }
    }

    public static class MicrosoftProvider extends Provider {
        private String tenantId = "common";

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }
    }
}
