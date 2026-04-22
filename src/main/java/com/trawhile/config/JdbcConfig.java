package com.trawhile.config;

import com.trawhile.domain.AuthLevel;
import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.mapping.JdbcValue;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

import java.sql.JDBCType;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Registers custom type converters for Spring Data JDBC:
 * - AuthLevel ↔ PostgreSQL auth_level enum (lowercase string)
 * - PostgreSQL JSONB → String (for JSONB-backed read models)
 * - OffsetDateTime ↔ JDBC TIMESTAMPTZ values
 *
 * JSONB writes intentionally stay explicit in SQL (`CAST(? AS jsonb)`) instead of a global
 * String -> jsonb converter. That keeps ordinary VARCHAR/String bindings from being
 * accidentally treated as JSONB in unrelated repository operations.
 */
@Configuration
public class JdbcConfig extends AbstractJdbcConfiguration {

    @Bean
    @Override
    public JdbcCustomConversions jdbcCustomConversions() {
        return new JdbcCustomConversions(List.of(
            new AuthLevelWritingConverter(),
            new AuthLevelReadingConverter(),
            new JsonbReadingConverter(),
            new OffsetDateTimeWritingConverter(),
            new OffsetDateTimeReadingConverter()
        ));
    }

    @WritingConverter
    static class AuthLevelWritingConverter implements Converter<AuthLevel, String> {
        @Override
        public String convert(AuthLevel source) {
            return source.name().toLowerCase();
        }
    }

    @ReadingConverter
    static class AuthLevelReadingConverter implements Converter<String, AuthLevel> {
        @Override
        public AuthLevel convert(String source) {
            return AuthLevel.valueOf(source.toUpperCase());
        }
    }

    /**
     * Reads a PostgreSQL JSONB value back as a plain JSON string.
     */
    @ReadingConverter
    static class JsonbReadingConverter implements Converter<PGobject, String> {
        @Override
        public String convert(PGobject source) {
            return source.getValue();
        }
    }

    /**
     * Writes an OffsetDateTime with the JDBC TIMESTAMPTZ type so PostgreSQL
     * receives a value it can bind directly to timestamptz columns.
     */
    @WritingConverter
    static class OffsetDateTimeWritingConverter implements Converter<OffsetDateTime, JdbcValue> {
        @Override
        public JdbcValue convert(OffsetDateTime source) {
            return JdbcValue.of(source, JDBCType.TIMESTAMP_WITH_TIMEZONE);
        }
    }

    /**
     * Reads a JDBC Timestamp back as OffsetDateTime in UTC.
     */
    @ReadingConverter
    static class OffsetDateTimeReadingConverter implements Converter<Timestamp, OffsetDateTime> {
        @Override
        public OffsetDateTime convert(Timestamp source) {
            return source.toInstant().atOffset(ZoneOffset.UTC);
        }
    }
}
