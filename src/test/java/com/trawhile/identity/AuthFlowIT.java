package com.trawhile.identity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.trawhile.BaseIT;
import com.trawhile.port.inbound.identity.OidcCallbackPort;
import com.trawhile.port.inbound.identity.OidcCallbackPort.BootstrapEligible;
import com.trawhile.port.inbound.identity.OidcCallbackPort.CallbackOutcome;
import com.trawhile.port.inbound.identity.OidcCallbackPort.InvitationMatched;
import com.trawhile.port.inbound.identity.OidcCallbackPort.KnownIdentityLogin;
import com.trawhile.port.inbound.identity.OidcCallbackPort.OidcCallbackCommand;
import com.trawhile.port.inbound.identity.OidcCallbackPort.ProviderLinked;
import com.trawhile.port.inbound.identity.OidcCallbackPort.Rejected;
import com.trawhile.port.inbound.identity.OidcCallbackPort.RejectionCause;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for SR-01-F13.F01 OIDC callback classification.
 *
 * <p>The invitation-match transaction assertion verifies the observed committed
 * end state and audit emission. It deliberately does not fault-inject a mid-flow
 * persistence failure because the outbound persistence ports are introduced by
 * the companion implementation brief and do not yet provide a stable spy point.</p>
 *
 * <p>Traceability: TE-01-F13.F01-01, TE-01-F13.F01-02, TE-01-F13.F01-03.</p>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.security.oauth2.client.registration.google.client-id=base-it-google-client",
        "spring.security.oauth2.client.registration.google.client-secret=base-it-google-secret",
        "BOOTSTRAP_ADMIN_EMAIL=bootstrap-canary@example.invalid"
    }
)
class AuthFlowIT extends BaseIT {

    private static final String GOOGLE_REG_ID = "google";
    private static final UUID ROOT_NODE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BOOTSTRAP_EMAIL = "bootstrap-canary@example.invalid";

    // Per-branch UUIDs make assertion failures obvious about which path was taken.
    private static final UUID INVITED_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000f13");
    private static final UUID EXISTING_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000f14");
    private static final UUID LINKED_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000f15");

    private static final String INVITED_EMAIL = "invited@example.invalid";
    private static final String EXISTING_EMAIL = "existing-auth-flow@example.invalid";
    private static final String LINKED_EMAIL = "linked-auth-flow@example.invalid";
    private static final String UNKNOWN_EMAIL = "truly-unknown@example.invalid";

    @Autowired
    private OidcCallbackPort oidcCallbackPort;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void attachLogCapture() {
        Logger rootLogger = (Logger) LoggerFactory.getILoggerFactory()
                .getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);
    }

    @AfterEach
    void detachLogCapture() {
        Logger rootLogger = (Logger) LoggerFactory.getILoggerFactory()
                .getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    @Tag("TE-01-F13.F01-01")
    void oidcCallback_classifiesIntoFiveOutcomes() {
        cleanAuthState();
        assertThat(rootAdminGrantExists())
                .as("Bootstrap sub-case precondition: no root admin grant exists")
                .isFalse();
        assertThat(userExistsByEmail(BOOTSTRAP_EMAIL))
                .as("Bootstrap sub-case precondition: no user exists for bootstrap email")
                .isFalse();
        int bootstrapSnapshot = listAppender.list.size();
        CallbackOutcome bootstrapOutcome = oidcCallbackPort.handle(command(
                "bootstrap-subject",
                BOOTSTRAP_EMAIL,
                Optional.empty()));

        assertThat(bootstrapOutcome)
                .as("sub-case 1 — bootstrap eligible")
                .isInstanceOf(BootstrapEligible.class);
        assertNoCapturedFormattedMessageContains(bootstrapSnapshot, BOOTSTRAP_EMAIL);

        cleanAuthState();
        seedPendingInvitation(INVITED_USER_ID, INVITED_EMAIL);
        int invitationSnapshot = listAppender.list.size();
        CallbackOutcome invitationOutcome = oidcCallbackPort.handle(command(
                "invited-subject",
                INVITED_EMAIL,
                Optional.empty()));

        assertThat(invitationOutcome)
                .as("sub-case 2 — invitation match")
                .isInstanceOf(InvitationMatched.class);
        assertNoCapturedFormattedMessageContains(invitationSnapshot, INVITED_EMAIL);

        cleanAuthState();
        seedActiveUser(EXISTING_USER_ID, "Existing User", EXISTING_EMAIL);
        seedProvider(EXISTING_USER_ID, "keycloak", "existing-keycloak-subject");
        int providerLinkingSnapshot = listAppender.list.size();
        CallbackOutcome providerLinkedOutcome = oidcCallbackPort.handle(command(
                "new-google-subject",
                EXISTING_EMAIL,
                Optional.of(EXISTING_USER_ID)));

        assertThat(providerLinkedOutcome)
                .as("sub-case 3 — provider linking")
                .isInstanceOf(ProviderLinked.class);
        assertNoCapturedFormattedMessageContains(providerLinkingSnapshot, EXISTING_EMAIL);

        cleanAuthState();
        seedActiveUser(LINKED_USER_ID, "Linked User", LINKED_EMAIL);
        seedProvider(LINKED_USER_ID, GOOGLE_REG_ID, "known-google-subject");
        int knownIdentitySnapshot = listAppender.list.size();
        CallbackOutcome knownIdentityOutcome = oidcCallbackPort.handle(command(
                "known-google-subject",
                LINKED_EMAIL,
                Optional.empty()));

        assertThat(knownIdentityOutcome)
                .as("sub-case 4 — known identity login")
                .isInstanceOf(KnownIdentityLogin.class);
        assertNoCapturedFormattedMessageContains(knownIdentitySnapshot, LINKED_EMAIL);

        cleanAuthState();
        int rejectedSnapshot = listAppender.list.size();
        CallbackOutcome rejectedOutcome = oidcCallbackPort.handle(command(
                "unknown-google-subject",
                UNKNOWN_EMAIL,
                Optional.empty()));

        assertThat(rejectedOutcome)
                .as("sub-case 5 — rejected not invited")
                .isInstanceOfSatisfying(Rejected.class, rejected -> {
                    assertThat(rejected.redirectUrl())
                            .as("sub-case 5 — rejected redirect")
                            .isEqualTo("/login?error=not_invited");
                    assertThat(rejected.cause())
                            .as("sub-case 5 — rejected cause")
                            .isEqualTo(RejectionCause.NOT_INVITED);
                });
        assertNoCapturedFormattedMessageContains(rejectedSnapshot, UNKNOWN_EMAIL);
    }

    @Test
    @Tag("TE-01-F13.F01-02")
    void invitationMatchCallback_runsAllSideEffectsInOneTransaction() {
        seedPendingInvitation(INVITED_USER_ID, INVITED_EMAIL);
        int snapshotSize = listAppender.list.size();

        CallbackOutcome outcome = oidcCallbackPort.handle(command(
                "invitation-match-subject",
                INVITED_EMAIL,
                Optional.empty()));

        assertThat(outcome)
                .as("Invitation-match callback must return the pre-created user id")
                .isInstanceOfSatisfying(InvitationMatched.class, matched ->
                    assertThat(matched.userId()).isEqualTo(INVITED_USER_ID));
        assertThat(providerExists(INVITED_USER_ID, GOOGLE_REG_ID, "invitation-match-subject"))
                .as("Invitation-match callback must link the provider to the pending user")
                .isTrue();
        assertThat(pendingInvitationExists(INVITED_USER_ID))
                .as("Invitation-match callback must delete the consumed pending invitation")
                .isFalse();
        assertThat(userEmail(INVITED_USER_ID))
                .as("Successful callback must refresh users.email from the OIDC email claim")
                .isEqualTo(INVITED_EMAIL);

        List<ILoggingEvent> loginSucceededEvents = eventsSince(snapshotSize).stream()
                .filter(event -> observableLogText(event).contains("oidc_login_succeeded"))
                .toList();
        assertThat(loginSucceededEvents)
                .as("Invitation-match callback must emit exactly one oidc_login_succeeded audit event")
                .hasSize(1)
                .allSatisfy(event -> {
                    String observable = observableLogText(event);
                    assertThat(observable)
                            .contains(INVITED_USER_ID.toString())
                            .doesNotContain(INVITED_EMAIL);
                    assertThat(event.getFormattedMessage())
                            .as("Audit formatted message must not contain raw email")
                            .doesNotContain(INVITED_EMAIL);
                });
    }

    @Test
    @Tag("TE-01-F13.F01-03")
    void rejectedCallback_redirectsWithoutLeakingEmailKnowledge() {
        int unknownSnapshot = listAppender.list.size();
        Rejected unknownEmailRejected = assertRejectedNotInvited(oidcCallbackPort.handle(command(
                "unknown-rejected-subject",
                UNKNOWN_EMAIL,
                Optional.empty())));
        List<String> unknownLogShape = rejectedLogShape(eventsSince(unknownSnapshot));

        assertThat(unknownEmailRejected.redirectUrl())
                .as("Rejected unknown-email callback must redirect to the generic login error")
                .isEqualTo("/login?error=not_invited");
        assertThat(unknownEmailRejected.cause())
                .as("Rejected unknown-email callback must carry NOT_INVITED")
                .isEqualTo(RejectionCause.NOT_INVITED);
        assertThat(unknownLogShape)
                .as("Rejected callback must emit exactly one oidc_login_rejected audit event")
                .hasSize(1)
                .allSatisfy(text -> assertThat(text)
                        .contains("oidc_login_rejected")
                        .contains("cause=not_invited")
                        .doesNotContain(UNKNOWN_EMAIL));
        assertNoCapturedFormattedMessageContains(unknownSnapshot, UNKNOWN_EMAIL);

        cleanAuthState();
        seedActiveUser(EXISTING_USER_ID, "Known But Unlinked", UNKNOWN_EMAIL);
        int knownButUnlinkedSnapshot = listAppender.list.size();
        Rejected knownButUnlinkedRejected = assertRejectedNotInvited(oidcCallbackPort.handle(command(
                "known-but-unlinked-rejected-subject",
                UNKNOWN_EMAIL,
                Optional.empty())));
        List<String> knownButUnlinkedLogShape = rejectedLogShape(
                eventsSince(knownButUnlinkedSnapshot));

        assertThat(knownButUnlinkedRejected.redirectUrl())
                .as("Rejected known-but-unlinked callback must use the same redirect")
                .isEqualTo(unknownEmailRejected.redirectUrl());
        assertThat(knownButUnlinkedRejected.cause())
                .as("Rejected known-but-unlinked callback must use the same cause")
                .isEqualTo(unknownEmailRejected.cause());
        assertThat(knownButUnlinkedLogShape)
                .as("Rejected log shape must not distinguish known emails from unknown emails")
                .containsExactlyElementsOf(unknownLogShape);
        assertNoCapturedFormattedMessageContains(knownButUnlinkedSnapshot, UNKNOWN_EMAIL);
    }

    private OidcCallbackCommand command(
            String subject,
            String email,
            Optional<UUID> existingAuthenticatedUserId) {
        return new OidcCallbackCommand(
                GOOGLE_REG_ID,
                subject,
                email,
                existingAuthenticatedUserId);
    }

    private Rejected assertRejectedNotInvited(CallbackOutcome outcome) {
        assertThat(outcome)
                .as("Rejected callback must return the rejected outcome variant")
                .isInstanceOf(Rejected.class);
        Rejected rejected = (Rejected) outcome;
        assertThat(rejected.redirectUrl()).isEqualTo("/login?error=not_invited");
        assertThat(rejected.cause()).isEqualTo(RejectionCause.NOT_INVITED);
        return rejected;
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

    private boolean providerExists(UUID userId, String provider, String subject) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS ("
                + "SELECT 1 FROM user_oauth_providers "
                + "WHERE user_id = ? AND provider = ? AND subject = ?)",
                Boolean.class,
                userId,
                provider,
                subject);
        return Boolean.TRUE.equals(exists);
    }

    private boolean pendingInvitationExists(UUID userId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pending_invitations WHERE user_id = ?)",
                Boolean.class,
                userId);
        return Boolean.TRUE.equals(exists);
    }

    private String userEmail(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE id = ?",
                String.class,
                userId);
    }

    private boolean rootAdminGrantExists() {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS ("
                + "SELECT 1 FROM node_authorizations "
                + "WHERE node_id = ? AND auth_level = 'admin'::auth_level)",
                Boolean.class,
                ROOT_NODE_ID);
        return Boolean.TRUE.equals(exists);
    }

    private boolean userExistsByEmail(String email) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE email = ?)",
                Boolean.class,
                email);
        return Boolean.TRUE.equals(exists);
    }

    private void cleanAuthState() {
        jdbcTemplate.execute("DELETE FROM node_authorizations");
        jdbcTemplate.execute("DELETE FROM pending_invitations");
        jdbcTemplate.execute("DELETE FROM user_oauth_providers");
        jdbcTemplate.execute("DELETE FROM user_profile");
        jdbcTemplate.execute("DELETE FROM users");
    }

    private List<ILoggingEvent> eventsSince(int snapshotSize) {
        return List.copyOf(listAppender.list.subList(snapshotSize, listAppender.list.size()));
    }

    private List<String> rejectedLogShape(List<ILoggingEvent> events) {
        return events.stream()
                .filter(event -> observableLogText(event).contains("oidc_login_rejected"))
                .map(AuthFlowIT::observableLogText)
                .toList();
    }

    private void assertNoCapturedFormattedMessageContains(int snapshotSize, String forbiddenText) {
        assertThat(eventsSince(snapshotSize))
                .as("Captured log formatted messages must not contain raw email " + forbiddenText)
                .noneSatisfy(event ->
                    assertThat(event.getFormattedMessage()).doesNotContain(forbiddenText));
    }

    private static String observableLogText(ILoggingEvent event) {
        return event.getFormattedMessage() + " " + event.getMDCPropertyMap();
    }
}
