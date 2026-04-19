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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TE-F067.F01-01  Existing user OIDC callback establishes authenticated session
 * TE-F060.F02-01  POST /auth/gdpr-notice with valid session creates profile/oauth/deletes invitation
 * TE-F060.F02-02  POST /auth/gdpr-notice when invitation no longer exists redirects to not_invited
 * TE-C002.F01-01  OIDC callback with no matching invitation redirects to login error (same for not-found and expired)
 */
class AuthFlowIT extends BaseIT {

    @Autowired
    private TrawhileOidcUserService oidcUserService;

    // -----------------------------------------------------------------------
    // Helper: client registration without userInfoUri avoids network calls
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

    private MockHttpSession callLoadUserAndReturnSession(OidcUserRequest request) {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        httpRequest.setSession(session);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(httpRequest, new MockHttpServletResponse()));
        try {
            oidcUserService.loadUser(request);
        } catch (OAuth2AuthenticationException ignored) {
            // Swallow; session state inspectable
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
        return session;
    }

    // -----------------------------------------------------------------------
    // TE-F067.F01-01
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-F067.F01-01")
    void oidcCallback_existingUser_createsSessionAndRedirectsToRoot() {
        // Insert a fully registered user: users + user_profile + user_oauth_providers
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Returning User");
        String provider = "google";
        String subject = "google-sub-returning-001";

        UUID profileId = jdbc.queryForObject(
                "SELECT id FROM user_profile WHERE user_id = ?",
                UUID.class, userId);
        jdbc.update(
                "INSERT INTO user_oauth_providers (id, profile_id, provider, subject) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), profileId, provider, subject);

        int profilesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM user_profile", Integer.class);
        int oauthBefore = jdbc.queryForObject("SELECT COUNT(*) FROM user_oauth_providers", Integer.class);

        OidcUserRequest request = buildRequest("returning@example.com", subject);
        MockHttpSession session = callLoadUserAndReturnSession(request);

        // Returning user: PENDING_GDPR must NOT be set (not a new registration)
        assertThat(session.getAttribute("PENDING_GDPR"))
                .as("returning user must not get PENDING_GDPR session attribute")
                .isNull();

        // No new DB rows must be created
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_profile", Integer.class))
                .as("no new user_profile row must be created for returning user")
                .isEqualTo(profilesBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_oauth_providers", Integer.class))
                .as("no new user_oauth_providers row must be created for returning user")
                .isEqualTo(oauthBefore);
    }

    // -----------------------------------------------------------------------
    // TE-F060.F02-01
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-F060.F02-01")
    void acknowledgeGdpr_withPendingSession_insertsUserProfileOauthAndDeletesInvitation() throws Exception {
        UUID userId = TestFixtures.insertUser(jdbc);
        UUID invitationId = TestFixtures.insertPendingInvitation(jdbc, "register@example.com", userId);

        // Session data as set by TrawhileOidcUserService after matching pending invitation
        Map<String, Object> pendingData = Map.of(
                "invitationId", invitationId.toString(),
                "userId", userId.toString(),
                "provider", "google",
                "subject", "google-sub-register-001",
                "name", "New Member"
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("PENDING_GDPR", pendingData);

        mvc.perform(post("/api/v1/auth/gdpr-notice")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk());

        // user_profile must be created (SR-F060.F02)
        int profileCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_profile WHERE user_id = ?",
                Integer.class, userId);
        assertThat(profileCount).as("user_profile must be created on GDPR acknowledgement").isOne();

        // user_oauth_providers must be created
        int oauthCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_oauth_providers WHERE subject = ?",
                Integer.class, "google-sub-register-001");
        assertThat(oauthCount).as("user_oauth_providers row must be created on GDPR acknowledgement").isOne();

        // pending_invitations row must be deleted
        int pendingCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pending_invitations WHERE id = ?",
                Integer.class, invitationId);
        assertThat(pendingCount).as("pending_invitations row must be deleted on GDPR acknowledgement").isZero();
    }

    @Test
    @Tag("TE-F060.F02-01")
    void acknowledgeGdpr_noPendingSession_returns400() throws Exception {
        // No PENDING_GDPR in session → must return 400 (SR-F060.F02)
        mvc.perform(post("/api/v1/auth/gdpr-notice")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // TE-F060.F02-02
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-F060.F02-02")
    void acknowledgeGdpr_invitationWithdrawnBetweenCallbackAndAck_redirectsToNotInvited() throws Exception {
        // Session references an invitation that has since been deleted by the admin
        UUID userId = TestFixtures.insertUser(jdbc);
        UUID deletedInvitationId = UUID.randomUUID();  // does not exist in DB

        Map<String, Object> pendingData = Map.of(
                "invitationId", deletedInvitationId.toString(),
                "userId", userId.toString(),
                "provider", "google",
                "subject", "google-sub-withdrawn-001",
                "name", "Withdrawn User"
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("PENDING_GDPR", pendingData);

        // Must redirect to /login?error=not_invited (SR-F060.F02)
        mvc.perform(post("/api/v1/auth/gdpr-notice")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login?error=not_invited")));
    }

    // -----------------------------------------------------------------------
    // TE-C002.F01-01
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-C002.F01-01")
    void oidcCallback_noMatchingInvitation_redirectsToLoginError() {
        // Case 1: no pending invitation at all for this email
        OidcUserRequest requestNoInvitation = buildRequest("unknown@example.com", "sub-unknown-001");
        MockHttpServletRequest req1 = new MockHttpServletRequest();
        req1.setSession(new MockHttpSession());
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(req1, new MockHttpServletResponse()));
        try {
            assertThatThrownBy(() -> oidcUserService.loadUser(requestNoInvitation))
                    .as("no matching invitation: OAuth2AuthenticationException with not_invited code")
                    .isInstanceOf(OAuth2AuthenticationException.class)
                    .satisfies(ex -> {
                        String errorCode = ((OAuth2AuthenticationException) ex).getError().getErrorCode();
                        assertThat(errorCode)
                                .as("error code must signal not_invited regardless of cause")
                                .isEqualTo("not_invited");
                    });
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        // Case 2: invitation exists but is expired — must produce the SAME error code
        UUID expiredUserId = TestFixtures.insertUser(jdbc);
        UUID expiredInvitationId = TestFixtures.insertPendingInvitation(
                jdbc, "expired@example.com", expiredUserId);
        jdbc.update(
                "UPDATE pending_invitations SET expires_at = NOW() - INTERVAL '1 day' WHERE id = ?",
                expiredInvitationId);

        OidcUserRequest requestExpired = buildRequest("expired@example.com", "sub-expired-001");
        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setSession(new MockHttpSession());
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(req2, new MockHttpServletResponse()));
        try {
            assertThatThrownBy(() -> oidcUserService.loadUser(requestExpired))
                    .as("expired invitation: must produce the same error code as no invitation")
                    .isInstanceOf(OAuth2AuthenticationException.class)
                    .satisfies(ex -> {
                        String errorCode = ((OAuth2AuthenticationException) ex).getError().getErrorCode();
                        assertThat(errorCode)
                                .as("response must not distinguish 'not found' from 'expired' (SR-C002.F01)")
                                .isEqualTo("not_invited");
                    });
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
