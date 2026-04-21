package com.trawhile;

import com.trawhile.config.TrawhileConfig;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TrawhileConfigTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @Tag("TE-F050.F05-01")
    void validConfig_passesAllConstraints() {
        Set<ConstraintViolation<TrawhileConfig>> violations = VALIDATOR.validate(validConfig());

        assertThat(violations).isEmpty();
    }

    @Test
    @Tag("TE-F050.F05-02")
    void retentionYearsLessThan2_failsValidation() {
        TrawhileConfig config = validConfig();
        config.setRetentionYears(1);

        Set<ConstraintViolation<TrawhileConfig>> violations = VALIDATOR.validate(config);

        assertThat(violations)
            .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                .isEqualTo("retentionYears"));
    }

    @Test
    @Tag("TE-F050.F05-03")
    void freezeOffsetExceedsRetentionYears_failsValidation() {
        TrawhileConfig config = validConfig();
        config.setRetentionYears(5);
        config.setFreezeOffsetYears(6);

        Set<ConstraintViolation<TrawhileConfig>> violations = VALIDATOR.validate(config);

        assertThat(violations)
            .anySatisfy(violation -> assertThat(violation.getMessage())
                .contains("freeze-offset-years must not exceed retention-years"));
    }

    @Test
    @Tag("TE-F050.F05-04")
    void nodeRetentionExtraYearsNegative_failsValidation() {
        TrawhileConfig config = validConfig();
        config.setNodeRetentionExtraYears(-1);

        Set<ConstraintViolation<TrawhileConfig>> violations = VALIDATOR.validate(config);

        assertThat(violations)
            .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                .isEqualTo("nodeRetentionExtraYears"));
    }

    @Test
    @Tag("TE-F065.F01-02")
    void invalidIanaTimezone_failsValidation() {
        TrawhileConfig config = validConfig();
        config.setTimezone("Not/ATimezone");

        Set<ConstraintViolation<TrawhileConfig>> violations = VALIDATOR.validate(config);

        assertThat(violations)
            .anySatisfy(violation -> assertThat(violation.getMessage())
                .contains("valid IANA timezone identifier"));
    }

    @Test
    @Tag("TE-F065.F01-03")
    void privacyNoticeUrlMalformed_failsValidation() {
        TrawhileConfig config = validConfig();
        config.setPrivacyNoticeUrl("not-a-url");

        Set<ConstraintViolation<TrawhileConfig>> violations = VALIDATOR.validate(config);

        assertThat(violations)
            .anySatisfy(violation -> assertThat(violation.getMessage())
                .contains("valid HTTPS URL"));
    }

    private TrawhileConfig validConfig() {
        TrawhileConfig config = new TrawhileConfig();
        config.setName("trawhile");
        config.setTimezone("Europe/Zurich");
        config.setFreezeOffsetYears(2);
        config.setRetentionYears(5);
        config.setNodeRetentionExtraYears(1);
        config.setPrivacyNoticeUrl("https://example.com/privacy");
        return config;
    }
}
