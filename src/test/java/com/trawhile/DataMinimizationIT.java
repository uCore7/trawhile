package com.trawhile;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TE-C006.C01-01
 *
 * After the complete GDPR first-login flow (SR-F060.F02) completes, no email address must
 * exist in any user-facing column. The email is used transiently to look up the pending
 * invitation and is then discarded; it must never be written to user_profile or any other table.
 */
class DataMinimizationIT extends BaseIT {

    @Test
    @Tag("TE-C006.C01-01")
    void gdprRegistration_emailNotPersistedInAnyTable() throws Exception {
        String email = "alice@example.com";

        // Insert the pre-created users row and pending_invitations row (created at invite time)
        UUID userId = TestFixtures.insertUser(jdbc);
        UUID invitationId = TestFixtures.insertPendingInvitation(jdbc, email, userId);

        // Simulate what TrawhileOidcUserService stores in the session after matching
        // the pending invitation (SR-F060.F01)
        Map<String, Object> pendingData = Map.of(
                "invitationId", invitationId.toString(),
                "userId", userId.toString(),
                "provider", "google",
                "subject", "google-sub-alice-001",
                "name", "Alice"
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("PENDING_GDPR", pendingData);

        // Execute the GDPR acknowledgement (SR-F060.F02) — this must:
        //   1. insert user_profile (name = "Alice", not the email)
        //   2. insert user_oauth_providers
        //   3. delete pending_invitations row (which held the email)
        mvc.perform(post("/api/v1/auth/gdpr-notice")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk());

        // After registration completes, the email must not appear in any profile name
        int emailInProfiles = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_profile WHERE name LIKE '%@%'",
                Integer.class);
        assertThat(emailInProfiles)
                .as("email must not be stored in user_profile.name (SR-C006.C01)")
                .isZero();

        // The pending_invitations row (which held the email) must be deleted
        int pendingRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pending_invitations WHERE email = ?",
                Integer.class, email);
        assertThat(pendingRows)
                .as("pending_invitations row with email must be deleted on successful registration")
                .isZero();

        // No raw email must survive in any other text column of known tables
        int emailInSecurityEvents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM security_events WHERE details::text LIKE ?",
                Integer.class, "%" + email + "%");
        assertThat(emailInSecurityEvents)
                .as("email must not be persisted in security_events.details")
                .isZero();
    }
}
