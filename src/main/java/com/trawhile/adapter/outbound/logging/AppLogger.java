package com.trawhile.adapter.outbound.logging;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Project-provided structured logger that pseudonymises PII fields at emission.
 *
 * <p>SR-00-C14.F01: raw emails, names, and OIDC subjects MUST NOT appear in
 * formatted log output. This class is the single application-code entry point
 * for emitting log events that reference user data; SR-00-C14.C01 forbids
 * direct SLF4J calls that bypass it.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * private static final AppLogger log = AppLogger.getLogger(MyService.class);
 * log.info("user.signed-in",
 *          new AppLogger.UserId(user.id()),
 *          new AppLogger.Email(user.email()),       // pseudonymised
 *          new AppLogger.RawField("provider", "google"));
 * </pre>
 */
public final class AppLogger {

    private final Logger delegate;

    private AppLogger(Logger delegate) {
        this.delegate = delegate;
    }

    public static AppLogger getLogger(Class<?> clazz) {
        return new AppLogger(LoggerFactory.getLogger(clazz));
    }

    public void info(String event, PiiField... fields) {
        delegate.info(format(event, fields));
    }

    public void warn(String event, PiiField... fields) {
        delegate.warn(format(event, fields));
    }

    public void error(String event, Throwable cause, PiiField... fields) {
        delegate.error(format(event, fields), cause);
    }

    private static String format(String event, PiiField[] fields) {
        if (fields == null || fields.length == 0) {
            return event;
        }
        return event + " " + Arrays.stream(fields)
                .map(f -> f.name() + "=" + f.pseudonym())
                .collect(Collectors.joining(" "));
    }

    public sealed interface PiiField
            permits Email, Name, OidcSubject, UserId, RawField {
        /** Field name as it appears in the formatted message (key in `key=value`). */
        String name();

        /**
         * Value that appears in the formatted message: the pseudonym for sensitive
         * fields, the raw value for non-sensitive ones (UserId, RawField).
         */
        String pseudonym();
    }

    public record Email(String value) implements PiiField {
        public String name() {
            return "email";
        }

        public String pseudonym() {
            return "<email-redacted>";
        }
    }

    public record Name(String value) implements PiiField {
        public String name() {
            return "name";
        }

        public String pseudonym() {
            return "<name-redacted>";
        }
    }

    public record OidcSubject(String value) implements PiiField {
        public String name() {
            return "oidcSubject";
        }

        public String pseudonym() {
            return "<oidcSubject-redacted>";
        }
    }

    public record UserId(UUID value) implements PiiField {
        public String name() {
            return "userId";
        }

        public String pseudonym() {
            return value.toString();
        }
    }

    public record RawField(String fieldName, String value) implements PiiField {
        public String name() {
            return fieldName;
        }

        public String pseudonym() {
            return value;
        }
    }
}
