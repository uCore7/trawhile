package com.trawhile._crosscutting;

import static org.assertj.core.api.Assertions.assertThat;

import com.trawhile.BaseIT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Integration tests for SR-00-C08.F01 and SR-00-C08.F02: the Spring Security
 * filter chain classifies every external-actor endpoint by auth mode and
 * enforces the classification:
 *
 * <ul>
 *   <li>Session-only endpoints ({@code /api/account/me/**},
 *       {@code /api/admin/**}) reject API-key-bearer requests with HTTP 403.</li>
 *   <li>Mixed-mode endpoints ({@code /api/tracking/**}, etc.) accept either
 *       OIDC session or API-key bearer.</li>
 *   <li>The MCP transport ({@code /api/mcp}) accepts API-key only; OIDC session
 *       requests are rejected with HTTP 401 before MCP dispatch.</li>
 * </ul>
 *
 * <p>Session seeding uses Spring Session's {@code SessionRepository} to
 * programmatically create an authenticated OIDC session. The test context
 * uses the simple (in-memory) store so no external Redis connection is
 * required during tests; the production store-type (redis) is overridden
 * here via {@code @TestPropertySource}.</p>
 *
 * <p>Traceability:
 * <ul>
 *   <li>TE-00-C08.F01-01 → SR-00-C08.F01 → UR-00-C08</li>
 *   <li>TE-00-C08.F01-02 → SR-00-C08.F01 → UR-00-C08</li>
 *   <li>TE-00-C08.F02-01 → SR-00-C08.F02 → UR-00-C08</li>
 * </ul>
 * </p>
 */
@TestPropertySource(properties = {
    "spring.session.store-type=simple",
    // The simple store does not connect to Redis; suppress the Redis connection
    // properties so the test context does not attempt to connect to a Redis
    // server that is not provisioned for unit/integration tests.
    "spring.data.redis.host=",
    "spring.data.redis.port=0"
})
class AuthAdapterIT extends BaseIT {

    // -------------------------------------------------------------------------
    // Test fixture constants — auditable record of what is seeded
    // -------------------------------------------------------------------------

    /** UUID of the test user row inserted in {@link #seedFixtures()}. */
    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000c08");

    /** OIDC subject claim used when building the {@code DefaultOidcUser}. */
    private static final String USER_OIDC_SUBJECT = "test-subject-c08";

    /** Email stored in the {@code users} row for this test user. */
    private static final String USER_EMAIL = "c08-test@example.invalid";

    /**
     * Raw API key value. SR-08-F01.C01: key names ≤ 64 chars; there is no
     * SR constraint on the raw key format beyond it being a 256-bit random
     * value at generation time. This constant is a fixed test-only value;
     * its SHA-256 hash is computed inline in {@link #seedFixtures()} and
     * only the hash is written to the database (SR-08-F01.F01 hard
     * constraint: raw key must not appear alongside its hash).
     */
    private static final String RAW_API_KEY = "tw-c08-test-fixed-key-for-it-tests-1234567890ab";

    /**
     * Root node UUID seeded by {@code V1__create_schema.sql}. Used as the
     * {@code scope_node_id} for the test API key so the key is valid and
     * covers all nodes.
     */
    private static final UUID ROOT_NODE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    // -------------------------------------------------------------------------
    // Injected test infrastructure
    // -------------------------------------------------------------------------

    @LocalServerPort
    private int port;

    /**
     * Spring Session {@code SessionRepository}. Provided by
     * {@code spring-session-core} auto-configuration when
     * {@code spring.session.store-type=simple} is active. Used to create and
     * persist an OIDC-authenticated session without going through the real
     * OAuth2 callback flow.
     */
    @Autowired
    private SessionRepository<? extends Session> sessionRepository;

    /**
     * Cookie value for the pre-seeded OIDC session. Populated in
     * {@link #seedFixtures()} and reused across test methods.
     *
     * <p>Spring Session's simple store base64url-encodes the session id when
     * writing the {@code SESSION} cookie. If the {@code CookieSerializer} bean
     * is not present (Spring Session does not expose it for the simple store
     * by default), we fall back to the manual encoding below.
     */
    private String oidcSessionCookieValue;

    // -------------------------------------------------------------------------
    // RestTemplate (shared, no-op error handler)
    // -------------------------------------------------------------------------

    /**
     * Configured once per test method invocation; the no-op error handler
     * ensures 4xx / 5xx responses are returned as {@code ResponseEntity}
     * rather than thrown as exceptions, enabling AssertJ assertions on the
     * status code with clear failure messages. Pattern taken from
     * {@code MetricsIT} lines 37-44.
     */
    private RestTemplate restTemplate;

    @BeforeEach
    void seedFixtures() {
        // Shared RestTemplate with a no-op error handler (MetricsIT pattern).
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
        // Insert a non-anonymised active user. The users table CHECK constraints
        // require display_name IS NOT NULL and email IS NOT NULL when
        // anonymised_at IS NULL (spec/schema.sql).
        jdbcTemplate.update(
                "INSERT INTO users (id, display_name, email, anonymised_at, created_at) "
                + "VALUES (?, 'C08 Test', ?, NULL, NOW())",
                USER_ID, USER_EMAIL);

        // --- 2. API key row ----------------------------------------------------
        // Compute SHA-256(RAW_API_KEY) as a 64-character hex string.
        // The hash is the only key material written to the database; the raw
        // value must never appear alongside it (SR-08-F01.F01 hard constraint).
        String keyHash = sha256Hex(RAW_API_KEY);

        UUID apiKeyId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO api_keys "
                + "(id, user_id, name, scope_node_id, scope_level, key_hash, "
                + " created_at, expires_at, last_used_at, revoked_at) "
                + "VALUES (?, ?, 'c08-test-key', ?, 'view'::auth_level, ?, "
                + "        NOW(), NOW() + INTERVAL '365 days', NULL, NULL)",
                apiKeyId, USER_ID, ROOT_NODE_ID, keyHash);

        // --- 3. OIDC session ---------------------------------------------------
        // Build a minimal OidcIdToken. Issuer and audience are not enforced
        // for the filter chain's purpose once the principal is in the
        // SecurityContext; only the subject and email claims are used.
        OidcIdToken idToken = OidcIdToken.withTokenValue("test-id-token")
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
                // Registration id is an arbitrary non-empty string; the filter
                // chain does not re-validate it after the callback phase.
                "test");

        SecurityContextImpl securityContext = new SecurityContextImpl(authToken);

        // Create a new Spring Session entry and store the SecurityContext under
        // the well-known key used by HttpSessionSecurityContextRepository.
        Session session = sessionRepository.createSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                securityContext);
        saveSession(session);

        // Spring Session (simple store) base64url-encodes the session id when
        // writing the SESSION cookie value. CookieSerializer is not guaranteed
        // to be exposed as a standalone Spring bean for the simple store, so
        // we fall back to the manual encoding described in the brief.
        // Ref: DefaultCookieSerializer#writeCookieValue()
        oidcSessionCookieValue = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(session.getId().getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------------
    // Test methods
    // -------------------------------------------------------------------------

    /**
     * TE-00-C08.F01-01: A valid, seeded API key presented as a Bearer token on
     * the session-only endpoint {@code GET /api/account/me} (classified as
     * session-only per SR-05-F01.C01 and SR-00-C08.F01) must be rejected with
     * exactly HTTP 403.
     *
     * <p>The key used here is the seeded, valid key — NOT a fake/unregistered
     * key. A fake key would trigger HTTP 401 at the lookup stage, which would
     * pass even against an impl that lacks the SR-00-C08.F01 classification
     * rule entirely. By using a valid key that passes lookup, the 403 assertion
     * is only satisfied when the auth-mode classification filter fires.</p>
     */
    @Test
    @Tag("TE-00-C08.F01-01")
    void apiKeyBearer_onSessionOnlyEndpoint_isRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(RAW_API_KEY);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/account/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode().value())
                .as("API-key bearer on session-only endpoint /api/account/me "
                    + "must be rejected with exactly HTTP 403 per SR-00-C08.F01 "
                    + "(not 401 which would indicate key-lookup failure before "
                    + "the classification rule fires)")
                .isEqualTo(403);
    }

    /**
     * TE-00-C08.F01-02: The mixed-mode endpoint {@code GET /api/tracking/current}
     * must accept both OIDC-session-cookie and API-key-bearer authentication.
     *
     * <p>An auth-success status is any status other than 401 or 403 — either
     * 200 (open record exists) or 404 (no open record) indicates the auth layer
     * accepted the request and forwarded it to the handler.</p>
     */
    @Test
    @Tag("TE-00-C08.F01-02")
    void mixedModeEndpoint_acceptsSessionAndApiKey() {
        String url = "http://localhost:" + port + "/api/tracking/current";

        // Sub-case 1: OIDC session cookie
        HttpHeaders sessionHeaders = new HttpHeaders();
        sessionHeaders.add(HttpHeaders.COOKIE, "SESSION=" + oidcSessionCookieValue);

        ResponseEntity<String> sessionResponse = restTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(sessionHeaders),
                String.class);

        int sessionStatus = sessionResponse.getStatusCode().value();
        assertThat(sessionStatus)
                .as("OIDC-session auth on mixed-mode endpoint /api/tracking/current "
                    + "must not be rejected by the auth layer (expected 200 or 404, "
                    + "not 401/403)")
                .isNotIn(401, 403);

        // Sub-case 2: API-key bearer
        HttpHeaders apiKeyHeaders = new HttpHeaders();
        apiKeyHeaders.setBearerAuth(RAW_API_KEY);

        ResponseEntity<String> apiKeyResponse = restTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(apiKeyHeaders),
                String.class);

        int apiKeyStatus = apiKeyResponse.getStatusCode().value();
        assertThat(apiKeyStatus)
                .as("API-key bearer auth on mixed-mode endpoint /api/tracking/current "
                    + "must not be rejected by the auth layer (expected 200 or 404, "
                    + "not 401/403)")
                .isNotIn(401, 403);
    }

    /**
     * TE-00-C08.F02-01: The MCP transport {@code /api/mcp} must accept
     * API-key-bearer requests and reject OIDC-session-cookie requests.
     *
     * <p>Session sub-case: SR-00-C08.F02 mandates rejection at the auth layer
     * before MCP dispatch, so the response must be exactly HTTP 401.</p>
     *
     * <p>API-key sub-case: at Phase 7 the Spring AI MCP server is not yet
     * wired; a 404 is acceptable (and expected) because the test verifies only
     * that the auth filter accepted the request. The assertion {@code isNotIn(401,
     * 403)} will continue to pass once the MCP server is wired and the status
     * flips to 200.</p>
     */
    @Test
    @Tag("TE-00-C08.F02-01")
    void mcpEndpoint_rejectsSession_acceptsApiKey() {
        String url = "http://localhost:" + port + "/api/mcp";
        String mcpBody = "{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"id\":1}";

        // Sub-case 1: OIDC session cookie — must be rejected with exactly 401
        HttpHeaders sessionHeaders = new HttpHeaders();
        sessionHeaders.setContentType(MediaType.APPLICATION_JSON);
        sessionHeaders.add(HttpHeaders.COOKIE, "SESSION=" + oidcSessionCookieValue);

        ResponseEntity<String> sessionResponse = restTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(mcpBody, sessionHeaders),
                String.class);

        assertThat(sessionResponse.getStatusCode().value())
                .as("OIDC-session request to /api/mcp must be rejected with exactly "
                    + "HTTP 401 per SR-00-C08.F02 (the auth layer rejects session-cookie "
                    + "authentication on the MCP transport before dispatch)")
                .isEqualTo(401);

        // Sub-case 2: API-key bearer — must pass auth (status != 401 and != 403)
        HttpHeaders apiKeyHeaders = new HttpHeaders();
        apiKeyHeaders.setContentType(MediaType.APPLICATION_JSON);
        apiKeyHeaders.setBearerAuth(RAW_API_KEY);

        ResponseEntity<String> apiKeyResponse = restTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(mcpBody, apiKeyHeaders),
                String.class);

        assertThat(apiKeyResponse.getStatusCode().value())
                .as("API-key bearer on /api/mcp must not be rejected by the auth layer "
                    + "(isNotIn 401/403); 404 is acceptable at Phase 7 while the MCP "
                    + "server is not yet wired; 200 is the expected steady-state value "
                    + "once the impl-backend wires the MCP server per SR-00-C08.F02)")
                .isNotIn(401, 403);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Saves the session using the raw-typed repository. The wildcard capture
     * is necessary because {@code SessionRepository<? extends Session>} does
     * not expose a save method on the wildcard; we capture the type to get a
     * concrete parameterisation.
     */
    @SuppressWarnings("unchecked")
    private <S extends Session> void saveSession(Session session) {
        ((SessionRepository<S>) sessionRepository).save((S) session);
    }

    /**
     * Computes the SHA-256 digest of {@code input} and returns the result as a
     * 64-character lowercase hex string. Used to compute the {@code key_hash}
     * for the {@code api_keys} row (spec/schema.sql: {@code VARCHAR(64) NOT NULL}).
     *
     * <p>The raw key value is never written to the database; only this hash is
     * persisted (SR-08-F01.F01 hard constraint).</p>
     */
    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
