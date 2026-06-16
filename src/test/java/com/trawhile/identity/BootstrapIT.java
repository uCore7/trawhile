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
 * Integration tests for SR-01-F01.F01 and SR-01-F01.F02 first-admin bootstrap.
 *
 * <p>The bootstrap transaction assertion verifies the observed committed end
 * state: users, user_oauth_providers, and node_authorizations are present
 * together after the callback. It deliberately does not fault-inject a mid-flow
 * persistence failure; a finer-grained rollback test needs a stable injection
 * point in the outbound persistence ports and belongs to a follow-up.</p>
 *
 * <p>Traceability: TE-01-F01.F01-01, TE-01-F01.F01-02,
 * TE-01-F01.F02-01.</p>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.security.oauth2.client.registration.google.client-id=base-it-google-client",
        "spring.security.oauth2.client.registration.google.client-secret=base-it-google-secret",
        "BOOTSTRAP_ADMIN_EMAIL=bootstrap-canary@example.invalid"
    }
)
class BootstrapIT extends BaseIT {

    private static final String GOOGLE_REG_ID = "google";
    private static final UUID ROOT_NODE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BOOTSTRAP_EMAIL = "bootstrap-canary@example.invalid";
    private static final String WRONG_EMAIL = "not-the-admin@example.invalid";

    private static final UUID EXISTING_ADMIN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000a01");
    private static final UUID INVITED_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000b01");
    private static final UUID KNOWN_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000c01");
    private static final String PREEXISTING_SUBJECT = "preexisting-subject-c01";

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
    @Tag("TE-01-F01.F01-01")
    void bootstrapCallback_insertsAllThreeRowsInOneTransactionAndEmitsAuditWithBootstrapTrue() {
        assertAuthTablesEmpty();
        int snapshotSize = listAppender.list.size();
        String subject = UUID.randomUUID().toString();

        CallbackOutcome outcome = oidcCallbackPort.handle(command(subject, BOOTSTRAP_EMAIL));

        assertThat(outcome)
                .as("Bootstrap callback must return the bootstrap-eligible outcome")
                .isInstanceOf(BootstrapEligible.class);
        UUID userId = outcome.userId();

        assertThat(userRow(userId))
                .as("Bootstrap callback must insert one active users row")
                .isEqualTo(new UserRow(userId, BOOTSTRAP_EMAIL, null));
        assertThat(providerExists(userId, GOOGLE_REG_ID, subject))
                .as("Bootstrap callback must link the returned OIDC provider identity")
                .isTrue();
        assertThat(rootAdminGrantExists(userId))
                .as("Bootstrap callback must grant admin on the root node")
                .isTrue();

        List<ILoggingEvent> loginSucceededEvents = eventsSince(snapshotSize).stream()
                .filter(event -> eventText(event).startsWith("oidc_login_succeeded "))
                .toList();
        assertThat(loginSucceededEvents)
                .as("Bootstrap callback must emit exactly one oidc_login_succeeded audit event")
                .hasSize(1)
                .allSatisfy(event -> {
                    String observable = observableLogText(event);
                    assertThat(observable)
                            .contains("userId=" + userId)
                            .contains("bootstrap=true");
                    assertThat(event.getFormattedMessage())
                            .as("Bootstrap audit formatted message must not contain raw email")
                            .doesNotContain(BOOTSTRAP_EMAIL);
                });
        assertCapturedFormattedMessagesAreFreeOf(snapshotSize, BOOTSTRAP_EMAIL);
        assertCapturedFormattedMessagesAreFreeOf(snapshotSize, WRONG_EMAIL);
    }

    @Test
    @Tag("TE-01-F01.F01-02")
    void bootstrapCallback_withMismatchedEmail_rejectsAndDoesNotInsertAdminGrant() {
        assertAuthTablesEmpty();
        int snapshotSize = listAppender.list.size();

        CallbackOutcome outcome = oidcCallbackPort.handle(command(
                UUID.randomUUID().toString(),
                WRONG_EMAIL));

        assertThat(outcome)
                .as("Mismatched bootstrap email must reject instead of opening the trapdoor")
                .isInstanceOfSatisfying(Rejected.class, rejected -> {
                    assertThat(rejected.cause()).isEqualTo(RejectionCause.NOT_INVITED);
                    assertThat(rejected.redirectUrl()).isEqualTo("/login?error=not_invited");
                });
        assertAuthTablesEmpty();

        List<ILoggingEvent> rejectedEvents = eventsSince(snapshotSize).stream()
                .filter(event -> eventText(event).startsWith("oidc_login_rejected "))
                .toList();
        assertThat(rejectedEvents)
                .as("Mismatched bootstrap callback must emit exactly one rejected audit event")
                .hasSize(1)
                .allSatisfy(event -> {
                    assertThat(observableLogText(event))
                            .contains("oidc_login_rejected")
                            .contains("cause=not_invited");
                    assertThat(event.getFormattedMessage())
                            .as("Rejected audit formatted message must not contain raw email")
                            .doesNotContain(WRONG_EMAIL);
                });
        assertCapturedFormattedMessagesAreFreeOf(snapshotSize, WRONG_EMAIL);
        assertCapturedFormattedMessagesAreFreeOf(snapshotSize, BOOTSTRAP_EMAIL);
    }

    @Test
    @Tag("TE-01-F01.F02-01")
    void bootstrapBranchIsSingleShot_afterAdminExistsBranchIsUnreachable() {
        seedRootAdmin();
        seedPendingInvitation(INVITED_USER_ID, BOOTSTRAP_EMAIL);
        int invitationSnapshotSize = listAppender.list.size();

        CallbackOutcome invitationOutcome = oidcCallbackPort.handle(command(
                UUID.randomUUID().toString(),
                BOOTSTRAP_EMAIL));

        assertThat(invitationOutcome)
                .as("sub-case A — matching email after admin exists must reach "
                    + "invitation-match, not bootstrap")
                .isInstanceOfSatisfying(InvitationMatched.class, matched ->
                    assertThat(matched.userId()).isEqualTo(INVITED_USER_ID));
        assertThat(rootAdminGrantCount())
                .as("Sub-case A must leave the existing root admin as the only admin grant")
                .isEqualTo(1);
        assertThat(rootAdminGrantExists(INVITED_USER_ID))
                .as("Sub-case A invitation user must not receive a bootstrap admin grant")
                .isFalse();
        assertCapturedFormattedMessagesAreFreeOf(invitationSnapshotSize, BOOTSTRAP_EMAIL);
        assertCapturedFormattedMessagesAreFreeOf(invitationSnapshotSize, WRONG_EMAIL);

        cleanAuthState();
        seedRootAdmin();
        seedActiveUser(KNOWN_USER_ID, "Known Bootstrap Email", BOOTSTRAP_EMAIL);
        seedProvider(KNOWN_USER_ID, GOOGLE_REG_ID, PREEXISTING_SUBJECT);
        int knownIdentitySnapshotSize = listAppender.list.size();

        CallbackOutcome knownIdentityOutcome = oidcCallbackPort.handle(command(
                PREEXISTING_SUBJECT,
                BOOTSTRAP_EMAIL));

        assertThat(knownIdentityOutcome)
                .as("sub-case B — matching email + prior link after admin exists must reach "
                    + "known-identity-login, not bootstrap")
                .isInstanceOfSatisfying(KnownIdentityLogin.class, known ->
                    assertThat(known.userId()).isEqualTo(KNOWN_USER_ID));
        assertThat(rootAdminGrantCount())
                .as("Sub-case B must leave the existing root admin as the only admin grant")
                .isEqualTo(1);
        assertThat(rootAdminGrantExists(KNOWN_USER_ID))
                .as("Sub-case B known user must not receive a bootstrap admin grant")
                .isFalse();
        assertCapturedFormattedMessagesAreFreeOf(knownIdentitySnapshotSize, BOOTSTRAP_EMAIL);
        assertCapturedFormattedMessagesAreFreeOf(knownIdentitySnapshotSize, WRONG_EMAIL);
    }

    private OidcCallbackCommand command(String subject, String email) {
        return new OidcCallbackCommand(
                GOOGLE_REG_ID,
                subject,
                email,
                Optional.empty());
    }

    private void assertAuthTablesEmpty() {
        assertThat(tableCount("users"))
                .as("Bootstrap precondition: no test users exist")
                .isZero();
        assertThat(tableCount("user_oauth_providers"))
                .as("Bootstrap precondition: no provider links exist")
                .isZero();
        assertThat(rootAdminGrantCount())
                .as("Bootstrap precondition: no root admin grants exist")
                .isZero();
    }

    private int tableCount(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Integer.class);
        return count == null ? 0 : count;
    }

    private UserRow userRow(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT id, email, anonymised_at FROM users WHERE id = ?",
                (rs, rowNum) -> new UserRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getObject("anonymised_at")),
                userId);
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

    private boolean rootAdminGrantExists(UUID userId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS ("
                + "SELECT 1 FROM node_authorizations "
                + "WHERE node_id = ? AND user_id = ? "
                + "AND auth_level = 'admin'::auth_level)",
                Boolean.class,
                ROOT_NODE_ID,
                userId);
        return Boolean.TRUE.equals(exists);
    }

    private int rootAdminGrantCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM node_authorizations "
                + "WHERE node_id = ? AND auth_level = 'admin'::auth_level",
                Integer.class,
                ROOT_NODE_ID);
        return count == null ? 0 : count;
    }

    private void seedRootAdmin() {
        seedActiveUser(EXISTING_ADMIN_ID, "Existing Root Admin", "root-admin@example.invalid");
        jdbcTemplate.update(
                "INSERT INTO node_authorizations (node_id, user_id, auth_level, granted_at) "
                + "VALUES (?, ?, 'admin'::auth_level, NOW())",
                ROOT_NODE_ID,
                EXISTING_ADMIN_ID);
    }

    private void seedPendingInvitation(UUID userId, String email) {
        seedActiveUser(userId, "Pending Bootstrap Candidate", email);
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

    private void assertCapturedFormattedMessagesAreFreeOf(
            int snapshotSize,
            String forbiddenText) {
        assertThat(eventsSince(snapshotSize))
                .as("Captured log formatted messages must not contain raw email "
                    + forbiddenText)
                .allSatisfy(event ->
                    assertThat(event.getFormattedMessage()).doesNotContain(forbiddenText));
    }

    private static String observableLogText(ILoggingEvent event) {
        return event.getFormattedMessage() + " " + event.getMDCPropertyMap();
    }

    private static String eventText(ILoggingEvent event) {
        return event.getFormattedMessage();
    }

    private record UserRow(UUID id, String email, Object anonymisedAt) {}
}
