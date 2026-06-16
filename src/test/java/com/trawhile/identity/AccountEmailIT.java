package com.trawhile.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.trawhile.BaseIT;
import com.trawhile.port.inbound.identity.OidcCallbackPort;
import com.trawhile.port.inbound.identity.OidcCallbackPort.BootstrapEligible;
import com.trawhile.port.inbound.identity.OidcCallbackPort.CallbackOutcome;
import com.trawhile.port.inbound.identity.OidcCallbackPort.InvitationMatched;
import com.trawhile.port.inbound.identity.OidcCallbackPort.KnownIdentityLogin;
import com.trawhile.port.inbound.identity.OidcCallbackPort.OidcCallbackCommand;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies SR-00-C11.F01: the OIDC callback persists {@code users.email}
 * from the OIDC {@code email} claim on every authenticated path, and a
 * subsequent login refreshes the column.
 *
 * <p>Traceability: TE-00-C11.F01-01.</p>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.security.oauth2.client.registration.google.client-id=base-it-google-client",
        "spring.security.oauth2.client.registration.google.client-secret=base-it-google-secret",
        "BOOTSTRAP_ADMIN_EMAIL=bootstrap-c11@example.invalid"
    }
)
class AccountEmailIT extends BaseIT {

    private static final String GOOGLE_REG_ID = "google";

    private static final String BOOTSTRAP_EMAIL = "bootstrap-c11@example.invalid";
    private static final String INVITED_EMAIL = "invited-c11@example.invalid";
    private static final String KNOWN_INITIAL_EMAIL = "known-c11@example.invalid";
    private static final String KNOWN_REFRESHED_EMAIL = "known-c11-renamed@example.invalid";

    private static final UUID INVITED_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-00000000c111");
    private static final UUID KNOWN_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-00000000c112");

    @Autowired
    private OidcCallbackPort oidcCallbackPort;

    @Test
    @Tag("TE-00-C11.F01-01")
    void oidcCallback_persistsEmailFromClaimOnAllAuthenticatedPaths_andRefreshesOnSubsequentLogin() {
        // Phase 1 — bootstrap: empty auth state, matching email creates the
        // first admin user with users.email taken from the OIDC claim.
        cleanAuthState();
        CallbackOutcome bootstrapOutcome = oidcCallbackPort.handle(new OidcCallbackCommand(
                GOOGLE_REG_ID,
                "bootstrap-subject-c11",
                BOOTSTRAP_EMAIL,
                Optional.empty()));

        assertThat(bootstrapOutcome)
                .as("SR-00-C11.F01 phase 1: bootstrap path must produce a BootstrapEligible outcome")
                .isInstanceOf(BootstrapEligible.class);
        assertThat(userEmail(bootstrapOutcome.userId()))
                .as("SR-00-C11.F01 phase 1: bootstrap path must persist users.email "
                    + "from the OIDC claim")
                .isEqualTo(BOOTSTRAP_EMAIL);

        // Phase 2 — invitation match: pre-created pending user; callback links
        // the provider AND refreshes users.email to the claim's value.
        cleanAuthState();
        seedPendingInvitation(INVITED_USER_ID, INVITED_EMAIL);
        CallbackOutcome invitationOutcome = oidcCallbackPort.handle(new OidcCallbackCommand(
                GOOGLE_REG_ID,
                "invited-subject-c11",
                INVITED_EMAIL,
                Optional.empty()));

        assertThat(invitationOutcome)
                .as("SR-00-C11.F01 phase 2: invitation-match path must produce an "
                    + "InvitationMatched outcome")
                .isInstanceOf(InvitationMatched.class);
        assertThat(userEmail(INVITED_USER_ID))
                .as("SR-00-C11.F01 phase 2: invitation-match path must persist users.email "
                    + "from the OIDC claim")
                .isEqualTo(INVITED_EMAIL);

        // Phase 3 — known-identity login + subsequent-login refresh: seed a
        // user whose stored email is KNOWN_INITIAL_EMAIL, then have the same
        // (provider, subject) log in again carrying a NEW email claim. The
        // stored column must move to KNOWN_REFRESHED_EMAIL.
        cleanAuthState();
        seedActiveUser(KNOWN_USER_ID, "Known C11 user", KNOWN_INITIAL_EMAIL);
        seedProvider(KNOWN_USER_ID, GOOGLE_REG_ID, "known-subject-c11");

        CallbackOutcome knownOutcome = oidcCallbackPort.handle(new OidcCallbackCommand(
                GOOGLE_REG_ID,
                "known-subject-c11",
                KNOWN_REFRESHED_EMAIL,
                Optional.empty()));

        assertThat(knownOutcome)
                .as("SR-00-C11.F01 phase 3: known-identity-login path must produce a "
                    + "KnownIdentityLogin outcome")
                .isInstanceOf(KnownIdentityLogin.class);
        assertThat(userEmail(KNOWN_USER_ID))
                .as("SR-00-C11.F01 phase 3: a subsequent login must refresh users.email "
                    + "from the OIDC claim — the column moves from the prior value to the new one")
                .isEqualTo(KNOWN_REFRESHED_EMAIL);
    }

    private void seedPendingInvitation(UUID userId, String email) {
        seedActiveUser(userId, "Pending " + userId.toString().substring(31), email);
        jdbcTemplate.update(
                "INSERT INTO pending_invitations "
                + "(id, user_id, email, invited_by, invited_at, expires_at) "
                + "VALUES (?, ?, ?, NULL, NOW() - INTERVAL '1 day', "
                + "NOW() + INTERVAL '89 days')",
                UUID.randomUUID(),
                userId,
                email);
    }

    private void seedActiveUser(UUID userId, String displayName, String email) {
        jdbcTemplate.update(
                "INSERT INTO users (id, display_name, email, anonymised_at, created_at) "
                + "VALUES (?, ?, ?, NULL, NOW())",
                userId,
                displayName,
                email);
    }

    private void seedProvider(UUID userId, String provider, String subject) {
        jdbcTemplate.update(
                "INSERT INTO user_oauth_providers (user_id, provider, subject, linked_at) "
                + "VALUES (?, ?, ?, NOW())",
                userId,
                provider,
                subject);
    }

    private String userEmail(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE id = ?",
                String.class,
                userId);
    }

    private void cleanAuthState() {
        jdbcTemplate.execute("DELETE FROM node_authorizations");
        jdbcTemplate.execute("DELETE FROM pending_invitations");
        jdbcTemplate.execute("DELETE FROM user_oauth_providers");
        jdbcTemplate.execute("DELETE FROM user_profile");
        jdbcTemplate.execute("DELETE FROM users");
    }
}
