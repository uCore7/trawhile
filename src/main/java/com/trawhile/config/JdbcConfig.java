package com.trawhile.config;

import com.trawhile.domain.AuthLevel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

import java.util.List;

/**
 * Registers converters between Java AuthLevel (UPPER_CASE) and PostgreSQL auth_level (lowercase).
 */
@Configuration
public class JdbcConfig extends AbstractJdbcConfiguration {

    @Bean
    @Override
    public JdbcCustomConversions jdbcCustomConversions() {
        return new JdbcCustomConversions(List.of(
            new AuthLevelWritingConverter(),
            new AuthLevelReadingConverter()
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
}
