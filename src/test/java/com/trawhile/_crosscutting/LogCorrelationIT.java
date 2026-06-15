package com.trawhile._crosscutting;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.trawhile.BaseIT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Integration tests for SR-00-C16.F01: every emitted log entry carries the required
 * SLF4J MDC correlation identifiers.
 *
 * <p>Two sub-cases:
 * <ol>
 *   <li>Anonymous management request — asserts {@code traceId} and {@code requestId}
 *       are populated; asserts {@code sessionId} and {@code actorId} are absent.</li>
 *   <li>Authenticated application request with a seeded OIDC session — asserts all
 *       four keys are present, and that {@code actorId} is the pseudonymous user UUID
 *       (never an email or display name).</li>
 * </ol>
 * </p>
 *
 * <p>Traceability: TE-00-C16.F01-01 → SR-00-C16.F01 → UR-00-C16</p>
 */
@TestPropertySource(properties = {
    "spring.session.store-type=simple",
    "spring.data.redis.host=",
    "spring.data.redis.port=0"
})
class LogCorrelationIT extends BaseIT {

    // -------------------------------------------------------------------------
    // Test fixture constants
    // -------------------------------------------------------------------------

    /** UUID of the test user row inserted in {@link #seedFixtures()}. */
    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000c16");

    private static final String USER_OIDC_SUBJECT = "test-subject-c16";
    private static final String USER_EMAIL = "c16-test@example.invalid";

    // -------------------------------------------------------------------------
    // Injected test infrastructure
    // -------------------------------------------------------------------------

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private SessionRepository<? extends Session> sessionRepository;

    // -------------------------------------------------------------------------
    // Per-test state
    // -------------------------------------------------------------------------

    private RestTemplate restTemplate;
    private String oidcSessionCookieValue;

    /** Logback list-appender; captured log events accumulate here during each test. */
    private ListAppender<ILoggingEvent> listAppender;

    // -------------------------------------------------------------------------
    // Setup / teardown
    // -------------------------------------------------------------------------

    @BeforeEach
    void seedFixtures() {
        // Shared RestTemplate with a no-op error handler (MetricsIT / AuthAdapterIT pattern).
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(
                    org.springframework.http.client.ClientHttpResponse response)
                    throws java.io.IOException {
                return false;
            }
        });

        // --- 1. User row -------------------------------------------------------
        jdbcTemplate.update(
                "INSERT INTO users (id, display_name, email, anonymised_at, created_at) "
                + "VALUES (?, 'C16 Test', ?, NULL, NOW())",
                USER_ID, USER_EMAIL);

        // --- 2. OIDC session with trawhile.userId attribute --------------------
        OidcIdToken idToken = OidcIdToken.withTokenValue("test-id-token-c16")
                .subject(USER_OIDC_SUBJECT)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", USER_EMAIL)
                .build();

        DefaultOidcUser oidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken);

        OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oidcUser,
                oidcUser.getAuthorities(),
                "test");

        SecurityContextImpl securityContext = new SecurityContextImpl(authToken);

        Session session = sessionRepository.createSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                securityContext);
        // SR-00-C16.F01: the MDC filter derives actorId from this session attribute.
        // The OIDC callback flow (SR-01-F13.F01) sets this attribute during real
        // session establishment; here we set it directly to mirror that contract.
        session.setAttribute("trawhile.userId", USER_ID);
        saveSession(session);

        oidcSessionCookieValue = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(session.getId().getBytes(StandardCharsets.UTF_8));

        // --- 3. Log capture ---------------------------------------------------
        Logger rootLogger = (Logger) LoggerFactory.getILoggerFactory()
                .getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDownLogCapture() {
        Logger rootLogger = (Logger) LoggerFactory.getILoggerFactory()
                .getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAppender(listAppender);
        listAppender.stop();
    }

    // -------------------------------------------------------------------------
    // Test methods
    // -------------------------------------------------------------------------

    /**
     * TE-00-C16.F01-01 (sub-case A): An anonymous GET to the management-port health
     * endpoint must yield at least one log event with both {@code traceId} and
     * {@code requestId} in its MDC map; neither {@code sessionId} nor {@code actorId}
     * must appear (no session, no known actor).
     */
    @Test
    @Tag("TE-00-C16.F01-01")
    void anonymousManagementRequest_emitsTraceIdAndRequestId_butNoSessionOrActor() {
        int snapshotSize = listAppender.list.size();

        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + managementPort + "/actuator/health",
                String.class);

        assertThat(response.getStatusCode().value())
                .as("GET /actuator/health on management port must return HTTP 200 "
                    + "(the endpoint is permitAll per SR-00-C19 / Spring Boot default "
                    + "actuator security)")
                .isEqualTo(200);

        List<ILoggingEvent> requestEvents = listAppender.list.subList(
                snapshotSize, listAppender.list.size());

        // At least one event must have both traceId and requestId in MDC.
        assertThat(requestEvents)
                .as("At least one log event emitted during GET /actuator/health must "
                    + "carry both 'traceId' and 'requestId' in its MDC map "
                    + "(SR-00-C16.F01: all request-scoped log entries carry these keys)")
                .anySatisfy(event -> {
                    Map<String, String> mdc = event.getMDCPropertyMap();
                    assertThat(mdc.get("traceId"))
                            .as("MDC 'traceId' must be non-empty "
                                + "(emitted by Micrometer Tracing auto-config)")
                            .isNotEmpty();
                    assertThat(mdc.get("requestId"))
                            .as("MDC 'requestId' must be non-empty "
                                + "(emitted by MdcFilter per SR-00-C16.F01)")
                            .isNotEmpty();
                });

        // No event in this request window must carry sessionId.
        assertThat(requestEvents)
                .as("No log event during an anonymous management request must carry "
                    + "'sessionId' — there is no session for anonymous requests "
                    + "(SR-00-C16.F01)")
                .noneSatisfy(event ->
                    assertThat(event.getMDCPropertyMap())
                            .as("MDC must not contain 'sessionId' for anonymous request")
                            .containsKey("sessionId"));

        // No event in this request window must carry actorId.
        assertThat(requestEvents)
                .as("No log event during an anonymous management request must carry "
                    + "'actorId' — the actor is unknown for anonymous requests "
                    + "(SR-00-C16.F01)")
                .noneSatisfy(event ->
                    assertThat(event.getMDCPropertyMap())
                            .as("MDC must not contain 'actorId' for anonymous request")
                            .containsKey("actorId"));
    }

    /**
     * TE-00-C16.F01-01 (sub-case B): An authenticated GET to {@code /api/about}
     * with a seeded OIDC session must yield at least one log event with all four
     * MDC keys populated: {@code traceId}, {@code requestId}, {@code sessionId},
     * {@code actorId}. The {@code actorId} must be a UUID string matching the
     * seeded user's UUID — proving it is the pseudonymous identifier, not an email
     * or display name.
     */
    @Test
    @Tag("TE-00-C16.F01-01")
    void authenticatedAppRequest_emitsAllFourCorrelationKeys() {
        int snapshotSize = listAppender.list.size();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "SESSION=" + oidcSessionCookieValue);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/about",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode().value())
                .as("GET /api/about with a valid seeded OIDC session must not be "
                    + "rejected by the auth layer (expected any status except 401/403)")
                .isNotIn(401, 403);

        List<ILoggingEvent> requestEvents = listAppender.list.subList(
                snapshotSize, listAppender.list.size());

        // At least one event must have all four MDC keys.
        assertThat(requestEvents)
                .as("At least one log event emitted during GET /api/about must carry "
                    + "all four MDC keys: 'traceId', 'requestId', 'sessionId', 'actorId' "
                    + "(SR-00-C16.F01)")
                .anySatisfy(event -> {
                    Map<String, String> mdc = event.getMDCPropertyMap();

                    assertThat(mdc.get("traceId"))
                            .as("MDC 'traceId' must be non-empty")
                            .isNotEmpty();

                    assertThat(mdc.get("requestId"))
                            .as("MDC 'requestId' must be non-empty")
                            .isNotEmpty();

                    assertThat(mdc.get("sessionId"))
                            .as("MDC 'sessionId' must be non-empty for an authenticated "
                                + "session-cookie request")
                            .isNotEmpty();

                    String actorId = mdc.get("actorId");
                    assertThat(actorId)
                            .as("MDC 'actorId' must be non-empty for an authenticated request")
                            .isNotEmpty();

                    // UUID-syntax check: actorId must be a pseudonymous UUID,
                    // not an email address or display name.
                    assertThat(Pattern.matches(
                            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
                            actorId))
                            .as("MDC 'actorId' must be a lowercase UUID string — "
                                + "SR-00-C16.F01: actorId is the pseudonymous user UUID, "
                                + "never an email or display name. Got: '" + actorId + "'")
                            .isTrue();

                    assertThat(actorId)
                            .as("MDC 'actorId' must equal the seeded user's UUID "
                                + USER_ID + " — proving the correct identity source "
                                + "is used (SR-00-C16.F01)")
                            .isEqualTo(USER_ID.toString());
                });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private <S extends Session> void saveSession(Session session) {
        ((SessionRepository<S>) sessionRepository).save((S) session);
    }
}
