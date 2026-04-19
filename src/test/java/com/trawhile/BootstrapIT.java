package com.trawhile;

import com.trawhile.security.TrawhileOidcUserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TE-F001.F01-01, TE-F001.F01-02
 *
 * Tests the bootstrap first-login path: when BOOTSTRAP_ADMIN_EMAIL is set and no admin exists
 * on the root node, the first matching OIDC login must store PENDING_GDPR session data and
 * make no DB writes yet. A non-matching email must not proceed and must signal the not_invited error.
 */
class BootstrapIT extends BaseIT {

    @Autowired
    private TrawhileOidcUserService oidcUserService;

    // -----------------------------------------------------------------------
    // Helper: build an OidcUserRequest that avoids a real network call.
    // Using a client registration without userInfoUri causes OidcUserService
    // to skip the user-info endpoint and build the OidcUser from the ID token alone.
    // -----------------------------------------------------------------------

    private static ClientRegistration noNetworkRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .scope("openid", "email", "profile")
                .build();
    }

    private static OidcUserRequest buildRequest(String email, String subject) {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "test-token",
                now,
                now.plusSeconds(300),
                Map.of(
                        IdTokenClaimNames.SUB, subject,
                        IdTokenClaimNames.ISS, "https://accounts.google.com",
                        IdTokenClaimNames.IAT, now,
                        IdTokenClaimNames.EXP, now.plusSeconds(300),
                        IdTokenClaimNames.AUD, List.of("test-client-id"),
                        "email", email,
                        "name", "Test User"
                )
        );
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "test-access-token",
                now, now.plusSeconds(300));
        return new OidcUserRequest(noNetworkRegistration(), accessToken, idToken, Map.of());
    }

    /**
     * Calls loadUser() inside a mock request context and returns the HTTP session.
     * Swallows OAuth2AuthenticationException so callers can inspect session state
     * from the partial execution before the exception.
     */
    private MockHttpSession invokeLoadUser(OidcUserRequest request) {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        httpRequest.setSession(session);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(httpRequest, new MockHttpServletResponse()));
        try {
            oidcUserService.loadUser(request);
        } catch (OAuth2AuthenticationException ignored) {
            // exception means not_invited path; session state is still inspectable
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
        return session;
    }

    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-F001.F01-01")
    void bootstrapAdmin_firstLogin_grantsRootAdminAndRedirectsToGdprNotice() {
        // Precondition: no admin exists on root node (fresh DB from @BeforeEach)
        int adminCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM node_authorizations WHERE node_id = ? AND auth_level = 'admin'",
                Integer.class, TestFixtures.ROOT_NODE_ID);
        assertThat(adminCount).isZero();

        OidcUserRequest request = buildRequest("bootstrap@example.com", "bootstrap-sub-001");
        MockHttpSession session = invokeLoadUser(request);

        // PENDING_GDPR must be set in the session — no DB writes yet
        assertThat(session.getAttribute("PENDING_GDPR"))
                .as("session must contain PENDING_GDPR for bootstrap login")
                .isNotNull();

        int profileCount = jdbc.queryForObject("SELECT COUNT(*) FROM user_profile", Integer.class);
        assertThat(profileCount).as("user_profile must not be written before GDPR acknowledgement").isZero();

        int oauthCount = jdbc.queryForObject("SELECT COUNT(*) FROM user_oauth_providers", Integer.class);
        assertThat(oauthCount).as("user_oauth_providers must not be written before GDPR acknowledgement").isZero();
    }

    @Test
    @Tag("TE-F001.F01-02")
    void bootstrapAdmin_emailMismatch_redirectsToNotInvited() {
        // Precondition: no admin on root, no pending invitation for other@example.com
        OidcUserRequest request = buildRequest("other@example.com", "other-sub-001");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        httpRequest.setSession(session);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(httpRequest, new MockHttpServletResponse()));

        try {
            assertThatThrownBy(() -> oidcUserService.loadUser(request))
                    .as("non-matching email with no invitation must throw OAuth2AuthenticationException")
                    .isInstanceOf(OAuth2AuthenticationException.class)
                    .satisfies(ex -> {
                        OAuth2AuthenticationException oauthEx = (OAuth2AuthenticationException) ex;
                        // The error code must be something that results in /login?error=not_invited
                        // (not leaking whether 'not found' or 'expired')
                        assertThat(oauthEx.getError().getErrorCode())
                                .as("error code must signal the not_invited redirect")
                                .isEqualTo("not_invited");
                    });
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
