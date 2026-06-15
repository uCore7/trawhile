package com.trawhile._crosscutting;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.trawhile.BaseIT;
import com.trawhile.adapter.outbound.logging.AppLogger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Integration tests for SR-00-C14.F01: personal data is redacted at log
 * emission, and request payloads are not echoed into application logs.
 *
 * <p>Traceability: TE-00-C14.F01-01 -> SR-00-C14.F01 -> UR-00-C14.</p>
 */
@TestPropertySource(properties = {
    "spring.session.store-type=simple",
    "spring.data.redis.host=",
    "spring.data.redis.port=0"
})
class LogRedactionIT extends BaseIT {

    // Canary email - .invalid TLD per RFC 2606 so the test can never
    // accidentally touch a real domain even if assertion logic regresses.
    private static final String SENSITIVE_EMAIL = "redaction-canary@example.invalid";

    // Canary OIDC subject - clearly synthetic so a redaction failure is obvious.
    private static final String SENSITIVE_OIDC_SUBJECT =
            "redaction-canary-sub-xyz-123-not-a-real-subject";

    private static final String SENSITIVE_NAME = "Redaction Canary";
    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000c14");
    private static final String RAW_API_KEY =
            "tw-c14-test-fixed-key-for-log-redaction-it-123456";
    private static final UUID ROOT_NODE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void seedFixturesAndAttachLogCapture() {
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
                + "VALUES (?, 'C14 Test', 'c14-test@example.invalid', NULL, NOW())",
                USER_ID);

        jdbcTemplate.update(
                "INSERT INTO api_keys "
                + "(id, user_id, name, scope_node_id, scope_level, key_hash, "
                + " created_at, expires_at, last_used_at, revoked_at) "
                + "VALUES (?, ?, 'c14-test-key', ?, 'view'::auth_level, ?, "
                + "        NOW(), NOW() + INTERVAL '365 days', NULL, NULL)",
                UUID.randomUUID(), USER_ID, ROOT_NODE_ID, sha256Hex(RAW_API_KEY));

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
    @Tag("TE-00-C14.F01-01")
    void helperEmitsLogEntriesWithoutPii_directInvocation() {
        AppLogger log = AppLogger.getLogger(LogRedactionIT.class);
        int snapshotSize = listAppender.list.size();

        log.info("user.signed-in", sensitiveFields());
        log.warn("user.signed-in-warned", sensitiveFields());
        log.error("user.signed-in-errored", new RuntimeException("test"), sensitiveFields());

        List<ILoggingEvent> events = eventsSince(snapshotSize);

        assertThat(events)
                .as("AppLogger direct invocation must capture at least one event per "
                    + "info/warn/error call for TE-00-C14.F01-01")
                .hasSizeGreaterThanOrEqualTo(3);

        assertThat(events)
                .as("Every event emitted by AppLogger during the direct-invocation "
                    + "window must expose the internal user UUID pseudonym and no "
                    + "raw email, OIDC subject, or display name")
                .allSatisfy(event -> {
                    assertThat(event.getFormattedMessage())
                            .as("Formatted log message must contain the user UUID "
                                + "pseudonym required by SR-00-C14.F01")
                            .contains(USER_ID.toString());

                    assertNoSensitiveValues(event);
                });

        assertThat(events)
                .as("The AppLogger.error call must record the RuntimeException as the "
                    + "event throwable, not flatten it into a redaction-bypass field")
                .anySatisfy(event -> {
                    assertThat(event.getFormattedMessage())
                            .as("The error event must be identifiable by its event name")
                            .contains("user.signed-in-errored");

                    assertThat(event.getThrowableProxy())
                            .as("The error event must retain the RuntimeException "
                                + "throwable proxy")
                            .isNotNull();

                    assertThat(event.getThrowableProxy().getClassName())
                            .as("The retained throwable must be the RuntimeException "
                                + "passed to AppLogger.error")
                            .isEqualTo(RuntimeException.class.getName());

                    assertThat(event.getThrowableProxy().getMessage())
                            .as("The retained throwable message must be available only "
                                + "through the throwable proxy")
                            .isEqualTo("test");

                    assertThat(event.getFormattedMessage())
                            .as("The throwable message must not be copied into the "
                                + "formatted redaction pipeline message")
                            .doesNotContain("test");
                });
    }

    @Test
    @Tag("TE-00-C14.F01-01")
    void requestWithMalformedBodyDoesNotEchoBodyInErrorLog() {
        String malformedBody = "{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"params\":{\"email\":\""
                + SENSITIVE_EMAIL + "\" \"trailing-garbage\":";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(RAW_API_KEY);

        int snapshotSize = listAppender.list.size();
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/mcp",
                HttpMethod.POST,
                new HttpEntity<>(malformedBody, headers),
                String.class);
        List<ILoggingEvent> events = eventsSince(snapshotSize);

        int status = response.getStatusCode().value();
        assertThat(response.getStatusCode().is4xxClientError())
                .as("POST /api/mcp with a valid API key and malformed JSON must fail "
                    + "as a 4xx client error caused by request-body handling")
                .isTrue();

        assertThat(status)
                .as("Malformed JSON test must reach request-body handling, not stop at "
                    + "authentication, authorization/CSRF, missing route, or method "
                    + "classification")
                .isNotIn(401, 403, 404, 405);

        assertThat(events)
                .as("Malformed request must emit at least one log event during the "
                    + "request window so the no-payload-leak assertion is non-vacuous")
                .isNotEmpty();

        assertThat(events)
                .as("No log event emitted while handling the malformed request may "
                    + "contain the canary email or the verbatim malformed body")
                .allSatisfy(event -> {
                    assertThat(event.getFormattedMessage())
                            .as("Formatted log message must not contain the canary "
                                + "email from the malformed request body")
                            .doesNotContain(SENSITIVE_EMAIL);

                    assertThat(event.getFormattedMessage())
                            .as("Formatted log message must not contain the verbatim "
                                + "malformed request body")
                            .doesNotContain(malformedBody);
                });
    }

    private List<ILoggingEvent> eventsSince(int snapshotSize) {
        return List.copyOf(listAppender.list.subList(snapshotSize, listAppender.list.size()));
    }

    private static AppLogger.PiiField[] sensitiveFields() {
        return new AppLogger.PiiField[] {
            new AppLogger.Email(SENSITIVE_EMAIL),
            new AppLogger.Name(SENSITIVE_NAME),
            new AppLogger.OidcSubject(SENSITIVE_OIDC_SUBJECT),
            new AppLogger.UserId(USER_ID)
        };
    }

    private static void assertNoSensitiveValues(ILoggingEvent event) {
        assertThat(event.getFormattedMessage())
                .as("Formatted log message must not contain the raw canary email")
                .doesNotContain(SENSITIVE_EMAIL);

        assertThat(event.getFormattedMessage())
                .as("Formatted log message must not contain the raw canary OIDC subject")
                .doesNotContain(SENSITIVE_OIDC_SUBJECT);

        assertThat(event.getFormattedMessage())
                .as("Formatted log message must not contain the raw canary display name")
                .doesNotContain(SENSITIVE_NAME);

        Map<String, String> mdc = event.getMDCPropertyMap();
        assertThat(mdc.values())
                .as("MDC values must not contain raw personal-data canaries")
                .allSatisfy(value -> {
                    assertThat(value)
                            .as("An MDC value must not contain the raw canary email")
                            .doesNotContain(SENSITIVE_EMAIL);

                    assertThat(value)
                            .as("An MDC value must not contain the raw canary OIDC subject")
                            .doesNotContain(SENSITIVE_OIDC_SUBJECT);

                    assertThat(value)
                            .as("An MDC value must not contain the raw canary display name")
                            .doesNotContain(SENSITIVE_NAME);
                });
    }

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
