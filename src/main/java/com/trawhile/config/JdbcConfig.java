package com.trawhile.config;

import com.trawhile.domain.AuthLevel;
import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

import java.sql.Timestamp;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Registers custom type converters for Spring Data JDBC:
 * - AuthLevel ↔ PostgreSQL auth_level enum (lowercase string)
 * - String ↔ PostgreSQL JSONB (for last_report_settings)
 * - OffsetDateTime ↔ JDBC Timestamp for TIMESTAMPTZ columns
 */
@Configuration
public class JdbcConfig extends AbstractJdbcConfiguration {

    @Bean
    @Override
    public JdbcCustomConversions jdbcCustomConversions() {
        return new JdbcCustomConversions(List.of(
            new AuthLevelWritingConverter(),
            new AuthLevelReadingConverter(),
            new JsonbWritingConverter(),
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
     * Writes a JSON string into a PostgreSQL JSONB column.
     * Spring Data JDBC calls this when persisting a String field mapped to a JSONB column.
     */
    @WritingConverter
    static class JsonbWritingConverter implements Converter<String, PGobject> {
        @Override
        public PGobject convert(String source) {
            try {
                PGobject obj = new PGobject();
                obj.setType("jsonb");
                obj.setValue(source);
                return obj;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to create PGobject for JSONB", e);
            }
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
     * Writes an OffsetDateTime into a JDBC Timestamp.
     * PostgreSQL TIMESTAMPTZ stores an instant, not the original offset.
     */
    @WritingConverter
    static class OffsetDateTimeWritingConverter implements Converter<OffsetDateTime, Timestamp> {
        @Override
        public Timestamp convert(OffsetDateTime source) {
            return Timestamp.from(source.toInstant());
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
