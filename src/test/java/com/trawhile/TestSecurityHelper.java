package com.trawhile;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

public final class TestSecurityHelper {

    private TestSecurityHelper() {
    }

    public static RequestPostProcessor authenticatedAs(UUID userId) {
        return oidcLogin().oidcUser(oidcUser(userId));
    }

    /**
     * Authorization is derived from database rows, not Spring Security authorities.
     * This is an alias for readability in tests that seed admin access on the root node.
     */
    public static RequestPostProcessor adminUser(UUID userId) {
        return authenticatedAs(userId);
    }

    private static OidcUser oidcUser(UUID userId) {
        Instant issuedAt = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
            "test-id-token",
            issuedAt,
            issuedAt.plusSeconds(300),
            Map.of(IdTokenClaimNames.SUB, userId.toString())
        );
        return new DefaultOidcUser(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            idToken,
            IdTokenClaimNames.SUB
        );
    }
}
