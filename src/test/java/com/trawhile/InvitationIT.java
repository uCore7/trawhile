package com.trawhile;

import com.trawhile.lifecycle.InvitationExpiryJob;
import com.trawhile.security.TrawhileOidcUserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TE-F005.F01-01  GET /api/v1/invitations — list pending invitations
 * TE-F006.F01-01  POST /api/v1/invitations — create invitation
 * TE-F011.F01-01  POST /api/v1/invitations/{id}/resend — resend invitation
 * TE-F060.F01-01  OIDC callback matching pending invitation stores session data, no DB writes
 * TE-F007.F01-01  DELETE /api/v1/invitations/{id} — withdraw invitation triggers scrubbing
 * TE-C010.C01-01  UserService.expireInvitations() transitions expired invitations to scrubbing state
 */
class InvitationIT extends BaseIT {

    @Autowired
    private TrawhileOidcUserService oidcUserService;

    @Autowired
    private InvitationExpiryJob invitationExpiryJob;

    // -----------------------------------------------------------------------
    // Helper: client registration that avoids network call to userinfo endpoint
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

    // -----------------------------------------------------------------------
    // TE-F005.F01-01
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-F005.F01-01")
    void listInvitations_admin_returnsEmailInvitedByAndInvitedAt() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        UUID inviteeId = TestFixtures.insertUser(jdbc);
        TestFixtures.insertPendingInvitation(jdbc, "invitee@example.com", inviteeId, adminId);

        mvc.perform(get("/api/v1/invitations")
                        .with(TestSecurityHelper.adminUser(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("invitee@example.com"))
                .andExpect(jsonPath("$[0].invitedBy").isNotEmpty())
                .andExpect(jsonPath("$[0].invitedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].userId").value(inviteeId.toString()));
    }

    @Test
    @Tag("TE-F005.F01-01")
    void listInvitations_nonAdmin_returns403() throws Exception {
        UUID nonAdminId = TestFixtures.insertUserWithProfile(jdbc, "Regular User");

        mvc.perform(get("/api/v1/invitations")
                        .with(TestSecurityHelper.authenticatedAs(nonAdminId)))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // TE-F006.F01-01
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-F006.F01-01")
    void createInvitation_newEmail_insertsUsersAndPendingRow_returnsMailtoLink() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        int usersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        int pendingBefore = jdbc.queryForObject("SELECT COUNT(*) FROM pending_invitations", Integer.class);

        mvc.perform(post("/api/v1/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"newmember@example.com\"}")
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mailtoLink").value(containsString("mailto:")))
                .andExpect(jsonPath("$.invitation.email").value("newmember@example.com"));

        int usersAfter = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        int pendingAfter = jdbc.queryForObject("SELECT COUNT(*) FROM pending_invitations", Integer.class);

        assertThat(usersAfter).as("a new users row must be inserted on invitation").isEqualTo(usersBefore + 1);
        assertThat(pendingAfter).as("a new pending_invitations row must be inserted").isEqualTo(pendingBefore + 1);
    }

    @Test
    @Tag("TE-F006.F01-01")
    void createInvitation_duplicateEmail_returns409() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        UUID existingUserId = TestFixtures.insertUser(jdbc);
        TestFixtures.insertPendingInvitation(jdbc, "already@example.com", existingUserId);

        mvc.perform(post("/api/v1/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"already@example.com\"}")
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    // -----------------------------------------------------------------------
    // TE-F011.F01-01
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-F011.F01-01")
    void resendInvitation_updatesExpiresAt_returnsFreshMailtoLink() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        UUID inviteeId = TestFixtures.insertUser(jdbc);
        UUID invitationId = TestFixtures.insertPendingInvitation(jdbc, "resend@example.com", inviteeId);

        OffsetDateTime originalExpiresAt = jdbc.queryForObject(
                "SELECT expires_at FROM pending_invitations WHERE id = ?",
                OffsetDateTime.class, invitationId);

        int usersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);

        mvc.perform(post("/api/v1/invitations/" + invitationId + "/resend")
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mailtoLink").value(containsString("mailto:")))
                .andExpect(jsonPath("$.invitation.id").value(invitationId.toString()));

        // expires_at must be updated to approximately NOW + 90 days
        OffsetDateTime updatedExpiresAt = jdbc.queryForObject(
                "SELECT expires_at FROM pending_invitations WHERE id = ?",
                OffsetDateTime.class, invitationId);
        assertThat(updatedExpiresAt)
                .as("expires_at must be updated to a later value on resend")
                .isAfter(originalExpiresAt);

        // No new users row must be created
        int usersAfter = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertThat(usersAfter).as("resend must not create a new users row").isEqualTo(usersBefore);
    }

    // -----------------------------------------------------------------------
    // TE-F060.F01-01
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-F060.F01-01")
    void oidcCallback_matchingPendingInvitation_storesSessionNoDatabaseWrite() {
        // Pre-created users row and pending invitation (as created by admin at invite time)
        UUID inviteeId = TestFixtures.insertUser(jdbc);
        TestFixtures.insertPendingInvitation(jdbc, "invited@example.com", inviteeId);

        int profilesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM user_profile", Integer.class);
        int oauthBefore = jdbc.queryForObject("SELECT COUNT(*) FROM user_oauth_providers", Integer.class);
        int usersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);

        OidcUserRequest request = buildRequest("invited@example.com", "google-sub-invited-001");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        httpRequest.setSession(session);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(httpRequest, new MockHttpServletResponse()));
        try {
            oidcUserService.loadUser(request);
        } catch (OAuth2AuthenticationException ignored) {
            // If thrown, the session data was not set — assertion below will catch it
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        // PENDING_GDPR must be set in the session
        assertThat(session.getAttribute("PENDING_GDPR"))
                .as("PENDING_GDPR must be stored in session after matching pending invitation")
                .isNotNull();

        // No new DB rows must be written yet (SR-F060.F01)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_profile", Integer.class))
                .as("user_profile must not be written before GDPR acknowledgement")
                .isEqualTo(profilesBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_oauth_providers", Integer.class))
                .as("user_oauth_providers must not be written before GDPR acknowledgement")
                .isEqualTo(oauthBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class))
                .as("no new users row must be written at callback time")
                .isEqualTo(usersBefore);
    }

    // -----------------------------------------------------------------------
    // TE-F007.F01-01
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-F007.F01-01")
    void withdrawInvitation_triggersScrubbingState() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        UUID inviteeId = TestFixtures.insertUser(jdbc);
        UUID invitationId = TestFixtures.insertPendingInvitation(jdbc, "withdraw@example.com", inviteeId);
        UUID childNode = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Team A");
        TestFixtures.grantAuth(jdbc, inviteeId, childNode, "view");

        // Invitee has no time records, so users row should be deleted after scrubbing
        mvc.perform(delete("/api/v1/invitations/" + invitationId)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // pending_invitations row must be deleted
        int pendingCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pending_invitations WHERE id = ?",
                Integer.class, invitationId);
        assertThat(pendingCount).as("pending_invitations row must be deleted on withdrawal").isZero();

        // node_authorizations for that user must be deleted
        int authCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM node_authorizations WHERE user_id = ?",
                Integer.class, inviteeId);
        assertThat(authCount).as("node_authorizations must be deleted on withdrawal").isZero();
    }

    @Test
    @Tag("TE-F007.F01-01")
    void withdrawInvitation_nonAdmin_returns403() throws Exception {
        UUID nonAdminId = TestFixtures.insertUserWithProfile(jdbc, "Regular User");
        UUID inviteeId = TestFixtures.insertUser(jdbc);
        UUID invitationId = TestFixtures.insertPendingInvitation(jdbc, "nonadmin@example.com", inviteeId);

        mvc.perform(delete("/api/v1/invitations/" + invitationId)
                        .with(TestSecurityHelper.authenticatedAs(nonAdminId))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // TE-C010.C01-01
    // -----------------------------------------------------------------------

    @Test
    @Tag("TE-C010.C01-01")
    void expireInvitations_expiredRows_triggersScrubbingForEach() {
        // Insert a user with an expired invitation
        UUID inviteeId = TestFixtures.insertUser(jdbc);
        UUID invitationId = TestFixtures.insertPendingInvitation(jdbc, "expired@example.com", inviteeId);

        // Set expires_at to 1 day in the past
        jdbc.update(
                "UPDATE pending_invitations SET expires_at = NOW() - INTERVAL '1 day' WHERE id = ?",
                invitationId);

        UUID childNode = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Old Project");
        TestFixtures.grantAuth(jdbc, inviteeId, childNode, "track");

        // Trigger the daily expiry job (delegates to UserService.expireInvitations())
        invitationExpiryJob.expireInvitations();

        // The expired invitation row must be deleted (scrubbing state applied)
        int pendingCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pending_invitations WHERE id = ?",
                Integer.class, invitationId);
        assertThat(pendingCount).as("expired pending_invitations row must be deleted").isZero();

        // node_authorizations for the expired user must be deleted
        int authCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM node_authorizations WHERE user_id = ?",
                Integer.class, inviteeId);
        assertThat(authCount).as("node_authorizations must be deleted when invitation expires").isZero();
    }
}
