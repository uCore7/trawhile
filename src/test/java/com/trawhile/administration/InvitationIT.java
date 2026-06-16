package com.trawhile.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trawhile.BaseIT;
import com.trawhile.adapter.inbound.web.dto.ApiAdminInvitationsPostRequest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@TestPropertySource(properties = {
    "spring.session.store-type=simple",
    "spring.data.redis.host=",
    "spring.data.redis.port=0"
})
class InvitationIT extends BaseIT {

    private static final UUID ROOT_NODE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000010f04");
    private static final String ADMIN_EMAIL = "admin-e01-f04@example.invalid";
    private static final String ADMIN_OIDC_SUBJECT = "admin-e01-f04-subject";

    private static final String INVITEE_EMAIL = "invitee@example.invalid";
    private static final String EXISTING_EMAIL = "existing@example.invalid";
    private static final String PENDING_EMAIL = "pending@example.invalid";
    private static final String FORWARDED_BASE_URL = "https://trawhile.example.invalid";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private SessionRepository<? extends Session> sessionRepository;

    private RestTemplate restTemplate;
    private String oidcSessionCookieValue;
    private String csrfTokenValue;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void seedAdminSessionAndAttachLogCapture() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(
                    org.springframework.http.client.ClientHttpResponse response)
                    throws java.io.IOException {
                return false;
            }
        });

        jdbcTemplate.update(
                "INSERT INTO users (id, display_name, email, anonymised_at, created_at) "
                + "VALUES (?, 'Invitation Admin', ?, NULL, NOW())",
                ADMIN_ID, ADMIN_EMAIL);
        jdbcTemplate.update(
                "INSERT INTO user_oauth_providers (user_id, provider, subject, linked_at) "
                + "VALUES (?, 'google', ?, NOW())",
                ADMIN_ID, ADMIN_OIDC_SUBJECT);
        jdbcTemplate.update(
                "INSERT INTO node_authorizations (node_id, user_id, auth_level, granted_at) "
                + "VALUES (?, ?, 'admin'::auth_level, NOW())",
                ROOT_NODE_ID, ADMIN_ID);

        OidcIdToken idToken = OidcIdToken.withTokenValue("test-id-token-e01-f04")
                .subject(ADMIN_OIDC_SUBJECT)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", ADMIN_EMAIL)
                .build();

        DefaultOidcUser oidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken);
        OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oidcUser,
                oidcUser.getAuthorities(),
                "google");

        Session session = sessionRepository.createSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authToken));
        session.setAttribute("trawhile.userId", ADMIN_ID);

        CsrfToken csrfToken = new DefaultCsrfToken(
                "X-CSRF-TOKEN",
                "_csrf",
                "csrf-" + UUID.randomUUID());
        session.setAttribute(
                HttpSessionCsrfTokenRepository.class.getName() + ".CSRF_TOKEN",
                csrfToken);
        csrfTokenValue = xoredCsrfTokenValue(csrfToken.getToken());
        saveSession(session);

        oidcSessionCookieValue = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(session.getId().getBytes(StandardCharsets.UTF_8));

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
    @Tag("TE-00-C03.F01-01")
    @Tag("TE-01-F04.F01-01")
    void createInvitationWithNewEmail_createsPendingUserAndInvitation_andReturnsServerGeneratedMailtoLink()
            throws Exception {
        int logSnapshotSize = listAppender.list.size();

        ResponseEntity<String> response = postInvitation(INVITEE_EMAIL);

        assertThat(response.getStatusCode().value())
                .as("POST /api/admin/invitations for a new .invalid email must create "
                    + "the pending user and invitation and return HTTP 201")
                .isEqualTo(201);

        JsonNode body = objectMapper.readTree(response.getBody());
        JsonNode invitation = body.path("invitation");
        assertThat(invitation.path("email").asText())
                .as("InvitationCreated.invitation.email must echo the invitee email")
                .isEqualTo(INVITEE_EMAIL);

        UUID responseInvitationId = UUID.fromString(invitation.path("id").asText());
        UUID responseUserId = UUID.fromString(invitation.path("userId").asText());
        assertThat(responseInvitationId)
                .as("InvitationCreated.invitation.id must be a non-null UUID")
                .isNotNull();
        assertThat(responseUserId)
                .as("InvitationCreated.invitation.userId must be a non-null UUID")
                .isNotNull();

        String invitedAtText = invitation.path("invitedAt").asText();
        String expiresAtText = invitation.path("expiresAt").asText();
        assertThat(invitedAtText)
                .as("invitedAt must be serialized as a UTC ISO-8601 instant")
                .isNotBlank()
                .endsWith("Z");
        assertThat(expiresAtText)
                .as("expiresAt must be serialized as a UTC ISO-8601 instant")
                .isNotBlank()
                .endsWith("Z");
        Instant.parse(invitedAtText);
        Instant.parse(expiresAtText);

        URI mailtoUri = URI.create(body.path("mailtoUrl").asText());
        assertThat(mailtoUri.getScheme())
                .as("InvitationCreated.mailtoUrl must be a mailto URI built by the backend")
                .isEqualTo("mailto");

        DecodedMailto decodedMailto = decodeMailto(mailtoUri);
        assertThat(decodedMailto.recipient())
                .as("mailto recipient must be the invitee email address")
                .isEqualTo(INVITEE_EMAIL);
        assertThat(decodedMailto.body())
                .as("mailto body must include the application base URL derived from forwarded origin")
                .contains(FORWARDED_BASE_URL)
                .as("mailto body must include the invitation email address")
                .contains(INVITEE_EMAIL);
        assertThat(decodedMailto.body().toLowerCase(Locale.ROOT))
                .as("mailto body must plainly instruct the invitee to sign in")
                .contains("sign in")
                .as("mailto body must mention OIDC or an identity provider")
                .containsAnyOf("oidc", "identity provider");
        assertThat(mailtoUri.toString().toLowerCase(Locale.ROOT))
                .as("mailto URI must not contain SMTP settings, secrets, or an invented token")
                .doesNotContain("smtp", "secret", "password", "token");

        assertThat(countUsersByEmail(INVITEE_EMAIL))
                .as("Exactly one pending users row must be created for the invitee email")
                .isEqualTo(1);
        UserRow pendingUser = userByEmail(INVITEE_EMAIL);
        assertThat(pendingUser.id())
                .as("Response invitation.userId must reference the pre-created pending user")
                .isEqualTo(responseUserId);
        assertThat(pendingUser.displayName())
                .as("Pending user row must have a non-null display name")
                .isNotBlank();
        assertThat(pendingUser.email())
                .as("Pending user row must keep the invitee email until activation or cleanup")
                .isEqualTo(INVITEE_EMAIL);
        assertThat(pendingUser.anonymisedAt())
                .as("Pending user row must not be anonymised")
                .isNull();
        assertThat(countOauthProviders(responseUserId))
                .as("Pending user must have no linked OIDC provider rows before first sign-in")
                .isZero();

        assertThat(countPendingInvitationsByEmail(INVITEE_EMAIL))
                .as("Exactly one pending_invitations row must be created for the invitee email")
                .isEqualTo(1);
        PendingInvitationRow pendingInvitation = pendingInvitationByEmail(INVITEE_EMAIL);
        assertThat(pendingInvitation.id())
                .as("Response invitation.id must reference the created pending invitation row")
                .isEqualTo(responseInvitationId);
        assertThat(pendingInvitation.userId())
                .as("pending_invitations.user_id must reference the pre-created user")
                .isEqualTo(responseUserId);
        assertThat(pendingInvitation.invitedBy())
                .as("pending_invitations.invited_by must identify the acting admin")
                .isEqualTo(ADMIN_ID);
        assertThat(Duration.between(
                pendingInvitation.invitedAt(),
                pendingInvitation.expiresAt()).toSeconds())
                .as("Invitation expiry must be approximately 90 days after invited_at")
                .isCloseTo(Duration.ofDays(90).toSeconds(), offset(60L));

        List<ILoggingEvent> requestEvents = eventsSince(logSnapshotSize);
        assertThat(requestEvents)
                .as("Successful invitation creation must emit an invitation_created "
                    + "audit event with actor and pending-user UUIDs and without raw email")
                .anySatisfy(event -> {
                    String observable = observableLogText(event);
                    assertThat(observable)
                            .contains("invitation_created")
                            .contains(ADMIN_ID.toString())
                            .contains(responseUserId.toString())
                            .doesNotContain(INVITEE_EMAIL);
                });
    }

    @Test
    @Tag("TE-01-F04.F01-02")
    void createInvitationForExistingActiveEmail_returnsConflictAndDoesNotCreateInvitation()
            throws Exception {
        UUID existingUserId = UUID.fromString("00000000-0000-0000-0000-00000001f402");
        jdbcTemplate.update(
                "INSERT INTO users (id, display_name, email, anonymised_at, created_at) "
                + "VALUES (?, 'Existing User', ?, NULL, NOW())",
                existingUserId, EXISTING_EMAIL);
        jdbcTemplate.update(
                "INSERT INTO user_oauth_providers (user_id, provider, subject, linked_at) "
                + "VALUES (?, 'google', 'existing-e01-f04-subject', NOW())",
                existingUserId);

        ResponseEntity<String> response = postInvitation(EXISTING_EMAIL);

        assertThat(response.getStatusCode().value())
                .as("Creating an invitation for an existing non-anonymised email must return HTTP 409")
                .isEqualTo(409);
        assertProblemConflict(response);
        assertThat(countPendingInvitationsByEmail(EXISTING_EMAIL))
                .as("Conflict for an existing user must not create a pending_invitations row")
                .isZero();
        assertThat(countUsersByEmail(EXISTING_EMAIL))
                .as("Conflict for an existing user must not create a second active users row")
                .isEqualTo(1);
    }

    @Test
    @Tag("TE-01-F04.F01-03")
    void createInvitationForExistingNonExpiredPendingEmail_returnsConflictAndDoesNotDuplicateRows()
            throws Exception {
        UUID pendingUserId = UUID.fromString("00000000-0000-0000-0000-00000001f403");
        UUID pendingInvitationId = UUID.fromString("00000000-0000-0000-0000-00000101f403");
        jdbcTemplate.update(
                "INSERT INTO users (id, display_name, email, anonymised_at, created_at) "
                + "VALUES (?, 'Pending User', ?, NULL, NOW())",
                pendingUserId, PENDING_EMAIL);
        jdbcTemplate.update(
                "INSERT INTO pending_invitations "
                + "(id, user_id, email, invited_by, invited_at, expires_at) "
                + "VALUES (?, ?, ?, ?, NOW() - INTERVAL '1 day', NOW() + INTERVAL '89 days')",
                pendingInvitationId, pendingUserId, PENDING_EMAIL, ADMIN_ID);

        ResponseEntity<String> response = postInvitation(PENDING_EMAIL);

        assertThat(response.getStatusCode().value())
                .as("Creating an invitation for an existing non-expired pending email must return HTTP 409")
                .isEqualTo(409);
        assertProblemConflict(response);
        assertThat(countPendingInvitationsByEmail(PENDING_EMAIL))
                .as("Conflict for a pending email must not duplicate pending_invitations rows")
                .isEqualTo(1);
        assertThat(countUsersByEmail(PENDING_EMAIL))
                .as("Conflict for a pending email must not duplicate linked pending users rows")
                .isEqualTo(1);

        PendingInvitationRow originalInvitation = pendingInvitationByEmail(PENDING_EMAIL);
        assertThat(originalInvitation.id())
                .as("Conflict must not replace the original pending_invitations row")
                .isEqualTo(pendingInvitationId);
        assertThat(originalInvitation.userId())
                .as("Conflict must not replace the original linked pending users row")
                .isEqualTo(pendingUserId);
    }

    @Test
    @Tag("TE-01-F03.F01-01")
    void adminInvitationsList_returnsAllPendingInvitationsWithInviterAndPreAssignedGrantCount()
            throws Exception {
        int logSnapshotSize = listAppender.list.size();
        UUID inviteeAId = UUID.fromString("00000000-0000-0000-0000-00000001f301");
        UUID inviteeBId = UUID.fromString("00000000-0000-0000-0000-00000001f302");
        UUID invitationAId = UUID.fromString("00000000-0000-0000-0000-00000101f301");
        UUID invitationBId = UUID.fromString("00000000-0000-0000-0000-00000101f302");
        String inviteeAEmail = "invitee-a-f03@example.invalid";
        String inviteeBEmail = "invitee-b-f03@example.invalid";
        Instant invitedAtA = Instant.parse("2026-01-02T03:04:05Z");
        Instant invitedAtB = Instant.parse("2026-01-03T03:04:05Z");
        Instant expiresAtA = invitedAtA.plus(Duration.ofDays(90));
        Instant expiresAtB = invitedAtB.plus(Duration.ofDays(90));

        jdbcTemplate.update(
                "INSERT INTO users (id, display_name, email, anonymised_at, created_at) "
                + "VALUES (?, 'Invitee A', ?, NULL, ?)",
                inviteeAId, inviteeAEmail, Timestamp.from(invitedAtA));
        jdbcTemplate.update(
                "INSERT INTO users (id, display_name, email, anonymised_at, created_at) "
                + "VALUES (?, 'Invitee B', ?, NULL, ?)",
                inviteeBId, inviteeBEmail, Timestamp.from(invitedAtB));
        jdbcTemplate.update(
                "INSERT INTO pending_invitations "
                + "(id, user_id, email, invited_by, invited_at, expires_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)",
                invitationAId,
                inviteeAId,
                inviteeAEmail,
                ADMIN_ID,
                Timestamp.from(invitedAtA),
                Timestamp.from(expiresAtA));
        jdbcTemplate.update(
                "INSERT INTO pending_invitations "
                + "(id, user_id, email, invited_by, invited_at, expires_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)",
                invitationBId,
                inviteeBId,
                inviteeBEmail,
                ADMIN_ID,
                Timestamp.from(invitedAtB),
                Timestamp.from(expiresAtB));
        jdbcTemplate.update(
                "INSERT INTO node_authorizations (node_id, user_id, auth_level, granted_at) "
                + "VALUES (?, ?, 'view'::auth_level, ?)",
                ROOT_NODE_ID, inviteeAId, Timestamp.from(invitedAtA));

        ResponseEntity<String> response = getInvitations();

        assertThat(response.getStatusCode().value())
                .as("GET /api/admin/invitations as root admin must list pending invitations")
                .isEqualTo(200);
        assertThat(response.getBody())
                .as("Invitation list response body must be a JSON array")
                .isNotBlank();

        JsonNode invitations = objectMapper.readTree(response.getBody());
        assertThat(invitations.isArray())
                .as("GET /api/admin/invitations must return a JSON array")
                .isTrue();
        assertThat(invitations)
                .as("Only the two seeded pending invitations must be returned")
                .hasSize(2);

        JsonNode invitationA = invitationByUserId(invitations, inviteeAId);
        assertInvitationListElement(
                invitationA,
                invitationAId,
                inviteeAEmail,
                invitedAtA,
                expiresAtA,
                inviteeAId,
                1);
        JsonNode invitationB = invitationByUserId(invitations, inviteeBId);
        assertInvitationListElement(
                invitationB,
                invitationBId,
                inviteeBEmail,
                invitedAtB,
                expiresAtB,
                inviteeBId,
                0);

        assertThat(response.getBody())
                .as("The embedded inviter reference must expose displayName, not admin email")
                .doesNotContain(ADMIN_EMAIL);

        List<ILoggingEvent> requestEvents = eventsSince(logSnapshotSize);
        assertThat(requestEvents)
                .as("Invitation list access logs must not include raw email literals")
                .allSatisfy(event -> assertThat(observableLogText(event))
                        .doesNotContain(ADMIN_EMAIL, inviteeAEmail, inviteeBEmail));
    }

    @Test
    @Tag("TE-01-F08.F01-01")
    void adminInvitationResend_movesExpiryForwardAndReturnsFreshMailto_withoutCreatingNewUserOrGrant()
            throws Exception {
        // Setup phase
        String resendInviteeEmail = "resend-f08@example.invalid";
        ResponseEntity<String> createResponse = postInvitation(resendInviteeEmail);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(201);
        JsonNode createBody = objectMapper.readTree(createResponse.getBody());
        UUID invitationId = UUID.fromString(createBody.path("invitation").path("id").asText());
        UUID userId = UUID.fromString(createBody.path("invitation").path("userId").asText());

        jdbcTemplate.update(
                "UPDATE pending_invitations "
                + "SET expires_at = invited_at + INTERVAL '30 days' WHERE id = ?",
                invitationId);
        Instant staleExpiresAt = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM pending_invitations WHERE id = ?",
                (rs, rowNum) -> rs.getTimestamp("expires_at").toInstant(),
                invitationId);

        Integer usersBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE anonymised_at IS NULL", Integer.class);
        Integer grantsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM node_authorizations", Integer.class);
        int logSnapshotSize = listAppender.list.size();

        // Action phase
        Instant before = Instant.now();
        ResponseEntity<String> response = resendInvitation(invitationId);
        Instant after = Instant.now();

        assertThat(response.getStatusCode().value())
                .as("POST /api/admin/invitations/{id}/resend must return HTTP 200 "
                    + "(not 201 — not a creation)")
                .isEqualTo(200);

        // Assertion phase — response body
        JsonNode body = objectMapper.readTree(response.getBody());
        JsonNode invitation = body.path("invitation");

        assertThat(invitation.path("id").asText())
                .as("SR-01-F08.F01: resend must retain the existing invitation UUID, "
                    + "not create a new one")
                .isEqualTo(invitationId.toString());
        assertThat(invitation.path("userId").asText())
                .as("SR-01-F08.F01: resend must retain the existing linked userId")
                .isEqualTo(userId.toString());

        Instant freshExpiresAt = Instant.parse(invitation.path("expiresAt").asText());
        assertThat(freshExpiresAt)
                .as("Resend must move expires_at strictly past staleExpiresAt + 30 days, "
                    + "proving a genuine expiry extension rather than a no-op touch")
                .isAfter(staleExpiresAt.plus(Duration.ofDays(30)));
        assertThat(freshExpiresAt)
                .as("Resend must set expires_at to NOW() + 90 days (lower bracket)")
                .isAfterOrEqualTo(before.plus(Duration.ofDays(90)).minus(Duration.ofSeconds(2)));
        assertThat(freshExpiresAt)
                .as("Resend must set expires_at to NOW() + 90 days (upper bracket)")
                .isBeforeOrEqualTo(after.plus(Duration.ofDays(90)).plus(Duration.ofSeconds(2)));

        URI mailtoUri = URI.create(body.path("mailtoUrl").asText());
        assertThat(mailtoUri.getScheme())
                .as("Resend mailtoUrl must be a mailto URI")
                .isEqualTo("mailto");
        DecodedMailto decodedMailto = decodeMailto(mailtoUri);
        assertThat(decodedMailto.recipient())
                .as("Resend mailtoUrl recipient must be the invitee email")
                .isEqualTo(resendInviteeEmail);
        assertThat(decodedMailto.body())
                .as("Resend mailtoUrl body must contain the application base URL (SR-00-C03.F01)")
                .contains(FORWARDED_BASE_URL)
                .as("Resend mailtoUrl body must contain the invitee email (SR-00-C03.F01)")
                .contains(resendInviteeEmail);

        // Assertion phase — database state
        PendingInvitationRow dbRow = pendingInvitationByEmail(resendInviteeEmail);
        assertThat(dbRow.expiresAt())
                .as("pending_invitations.expires_at in DB must reflect freshExpiresAt (lower bracket)")
                .isAfterOrEqualTo(freshExpiresAt.minus(Duration.ofSeconds(1)));
        assertThat(dbRow.expiresAt())
                .as("pending_invitations.expires_at in DB must reflect freshExpiresAt (upper bracket)")
                .isBeforeOrEqualTo(freshExpiresAt.plus(Duration.ofSeconds(1)));
        assertThat(dbRow.id())
                .as("SR-01-F08.F01: pending_invitations.id must be unchanged after resend")
                .isEqualTo(invitationId);
        assertThat(dbRow.userId())
                .as("pending_invitations.user_id must be unchanged after resend")
                .isEqualTo(userId);

        Integer usersAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE anonymised_at IS NULL", Integer.class);
        Integer grantsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM node_authorizations", Integer.class);
        assertThat(usersAfter)
                .as("SR-01-F08.F01: resend must not create new users rows")
                .isEqualTo(usersBefore);
        assertThat(grantsAfter)
                .as("SR-01-F08.F01: resend must not create new node_authorizations rows")
                .isEqualTo(grantsBefore);

        // Assertion phase — audit log
        List<ILoggingEvent> requestEvents = eventsSince(logSnapshotSize);
        List<ILoggingEvent> resentEvents = requestEvents.stream()
                .filter(e -> observableLogText(e).contains("invitation_resent"))
                .toList();
        assertThat(resentEvents)
                .as("Resend must emit exactly one invitation_resent audit event")
                .hasSize(1);
        assertThat(observableLogText(resentEvents.get(0)))
                .as("invitation_resent audit event must carry the acting admin UUID")
                .contains(ADMIN_ID.toString());
        assertThat(observableLogText(resentEvents.get(0)))
                .as("invitation_resent audit event must carry the invitation UUID")
                .contains(invitationId.toString());

        assertThat(requestEvents)
                .as("SR-00-C14.F01: no log event's formatted message may contain "
                    + "the raw invitee email")
                .allSatisfy(event -> assertThat(event.getFormattedMessage())
                        .doesNotContain(resendInviteeEmail));
    }

    private ResponseEntity<String> postInvitation(String email) throws Exception {
        HttpHeaders headers = authenticatedJsonHeaders();
        headers.add("X-Forwarded-Proto", "https");
        headers.add("X-Forwarded-Host", "trawhile.example.invalid");

        String requestBody = objectMapper.writeValueAsString(
                new ApiAdminInvitationsPostRequest(email));
        return restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/invitations",
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                String.class);
    }

    private ResponseEntity<String> getInvitations() {
        return restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/invitations",
                HttpMethod.GET,
                new HttpEntity<>(authenticatedJsonHeaders()),
                String.class);
    }

    private ResponseEntity<String> resendInvitation(UUID invitationId) {
        HttpHeaders headers = authenticatedJsonHeaders();
        headers.add("X-Forwarded-Proto", "https");
        headers.add("X-Forwarded-Host", "trawhile.example.invalid");
        return restTemplate.exchange(
                "http://localhost:" + port + "/api/admin/invitations/" + invitationId + "/resend",
                HttpMethod.POST,
                new HttpEntity<>("", headers),
                String.class);
    }

    private HttpHeaders authenticatedJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
        headers.add(HttpHeaders.COOKIE, "SESSION=" + oidcSessionCookieValue);
        headers.add("X-CSRF-TOKEN", csrfTokenValue);
        return headers;
    }

    private void assertProblemConflict(ResponseEntity<String> response) throws Exception {
        assertThat(response.getBody())
                .as("HTTP 409 conflict response must include a neutral Problem body")
                .isNotBlank();
        JsonNode problem = objectMapper.readTree(response.getBody());
        assertThat(problem.path("status").asInt())
                .as("Problem.status must mirror HTTP 409 for invitation conflicts")
                .isEqualTo(409);
        assertThat(problem.path("code").asText())
                .as("Problem.code must be a stable locale-neutral conflict code")
                .isNotBlank();
    }

    private static JsonNode invitationByUserId(JsonNode invitations, UUID userId) {
        JsonNode match = null;
        for (JsonNode invitation : invitations) {
            if (userId.toString().equals(invitation.path("userId").asText())) {
                match = invitation;
                break;
            }
        }
        assertThat(match)
                .as("Invitation list must include the pending invitation for userId %s", userId)
                .isNotNull();
        return match;
    }

    private static void assertInvitationListElement(
            JsonNode invitation,
            UUID invitationId,
            String email,
            Instant invitedAt,
            Instant expiresAt,
            UUID userId,
            int preAssignedGrantCount) {
        assertThat(invitation.path("id").asText())
                .as("Invitation.id must expose the pending_invitations id")
                .isEqualTo(invitationId.toString());
        assertThat(invitation.path("email").asText())
                .as("Invitation.email must expose the invitee email")
                .isEqualTo(email);
        assertThat(invitation.path("invitedBy").path("id").asText())
                .as("Invitation.invitedBy.id must expose the inviter user id")
                .isEqualTo(ADMIN_ID.toString());
        assertThat(invitation.path("invitedBy").path("displayName").asText())
                .as("Invitation.invitedBy.displayName must expose the inviter display name")
                .isEqualTo("Invitation Admin");
        assertInstantCloseTo(
                invitation.path("invitedAt").asText(),
                invitedAt,
                "Invitation.invitedAt must expose pending_invitations.invited_at");
        assertInstantCloseTo(
                invitation.path("expiresAt").asText(),
                expiresAt,
                "Invitation.expiresAt must expose pending_invitations.expires_at");
        assertThat(invitation.path("userId").asText())
                .as("Invitation.userId must expose the linked pre-created user id")
                .isEqualTo(userId.toString());
        assertThat(invitation.path("preAssignedGrantCount").asInt(-1))
                .as("Invitation.preAssignedGrantCount must count pre-assigned node grants")
                .isEqualTo(preAssignedGrantCount);
    }

    private static void assertInstantCloseTo(
            String actualText,
            Instant expected,
            String description) {
        Instant actual = Instant.parse(actualText);
        assertThat(Duration.between(expected, actual).toMillis())
                .as(description)
                .isCloseTo(0L, offset(1000L));
    }

    private int countUsersByEmail(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ? AND anonymised_at IS NULL",
                Integer.class,
                email);
        return count == null ? 0 : count;
    }

    private int countPendingInvitationsByEmail(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pending_invitations WHERE email = ?",
                Integer.class,
                email);
        return count == null ? 0 : count;
    }

    private int countOauthProviders(UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_oauth_providers WHERE user_id = ?",
                Integer.class,
                userId);
        return count == null ? 0 : count;
    }

    private UserRow userByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id, display_name, email, anonymised_at FROM users WHERE email = ?",
                (rs, rowNum) -> new UserRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        instantOrNull(rs, "anonymised_at")),
                email);
    }

    private PendingInvitationRow pendingInvitationByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id, user_id, invited_by, invited_at, expires_at "
                + "FROM pending_invitations WHERE email = ?",
                (rs, rowNum) -> new PendingInvitationRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("invited_by", UUID.class),
                        instantOrNull(rs, "invited_at"),
                        instantOrNull(rs, "expires_at")),
                email);
    }

    private static Instant instantOrNull(ResultSet rs, String columnName) throws SQLException {
        var timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    @SuppressWarnings("unchecked")
    private <S extends Session> void saveSession(Session session) {
        ((SessionRepository<S>) sessionRepository).save((S) session);
    }

    private List<ILoggingEvent> eventsSince(int snapshotSize) {
        return List.copyOf(listAppender.list.subList(snapshotSize, listAppender.list.size()));
    }

    private static String observableLogText(ILoggingEvent event) {
        return event.getFormattedMessage() + " " + event.getMDCPropertyMap();
    }

    private static DecodedMailto decodeMailto(URI mailtoUri) {
        String schemeSpecificPart = mailtoUri.getRawSchemeSpecificPart();
        String[] parts = schemeSpecificPart.split("\\?", 2);
        String recipient = urlDecode(parts[0]);
        Map<String, String> query = parts.length == 2
                ? decodeQuery(parts[1])
                : Map.of();
        return new DecodedMailto(recipient, query.getOrDefault("body", ""));
    }

    private static Map<String, String> decodeQuery(String rawQuery) {
        Map<String, String> decoded = new LinkedHashMap<>();
        for (String parameter : rawQuery.split("&")) {
            String[] nameValue = parameter.split("=", 2);
            String name = urlDecode(nameValue[0]);
            String value = nameValue.length == 2 ? urlDecode(nameValue[1]) : "";
            decoded.put(name, value);
        }
        return decoded;
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String xoredCsrfTokenValue(String rawToken) {
        byte[] rawTokenBytes = rawToken.getBytes(StandardCharsets.UTF_8);
        byte[] randomBytes = new byte[rawTokenBytes.length];
        new SecureRandom().nextBytes(randomBytes);

        byte[] xoredBytes = new byte[rawTokenBytes.length];
        for (int i = 0; i < rawTokenBytes.length; i++) {
            xoredBytes[i] = (byte) (randomBytes[i] ^ rawTokenBytes[i]);
        }

        byte[] encodedBytes = new byte[randomBytes.length + xoredBytes.length];
        System.arraycopy(randomBytes, 0, encodedBytes, 0, randomBytes.length);
        System.arraycopy(xoredBytes, 0, encodedBytes, randomBytes.length, xoredBytes.length);
        return Base64.getUrlEncoder().encodeToString(encodedBytes);
    }

    private record UserRow(
            UUID id,
            String displayName,
            String email,
            Instant anonymisedAt) {
    }

    private record PendingInvitationRow(
            UUID id,
            UUID userId,
            UUID invitedBy,
            Instant invitedAt,
            Instant expiresAt) {
    }

    private record DecodedMailto(String recipient, String body) {
    }
}
