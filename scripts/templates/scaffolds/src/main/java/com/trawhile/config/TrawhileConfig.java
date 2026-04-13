package com.trawhile.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.ZoneId;

/**
 * System configuration (SR-F050.F05). Loaded from application.yml under the "trawhile:" namespace.
 * Validated on startup — application fails fast if any constraint is violated.
 *
 * Secrets (DB credentials, OAuth client secrets, BOOTSTRAP_ADMIN_EMAIL) are provided via
 * environment variables and must not appear in the config file.
 */
@Validated
@ConfigurationProperties(prefix = "trawhile")
public class TrawhileConfig {

    /** Instance name displayed in the browser tab title and throughout the UI. */
    @NotBlank
    private String name = "trawhile";

    /** IANA timezone identifier; reference for date boundaries in reports and purge scheduling. */
    @NotBlank
    private String timezone = "UTC";

    /**
     * Number of years before now after which time entries become immutable.
     * The effective freeze cutoff is computed as NOW() - freezeOffsetYears years.
     * Must be >= 0 and <= retentionYears.
     */
    @Min(0)
    private int freezeOffsetYears = 2;

    /** Activity purge cutoff in years; minimum 2. */
    @Min(2)
    private int retentionYears = 5;

    /** Additional years before deactivated nodes are deleted beyond retentionYears; minimum 0. */
    @Min(0)
    private int nodeRetentionExtraYears = 1;

    /** Optional HTTPS URL to the company Privacy Notice; blank if not configured. */
    private String privacyNoticeUrl = "";

    @AssertTrue(message = "freeze-offset-years must not exceed retention-years")
    public boolean isFreezeOffsetWithinRetention() {
        return freezeOffsetYears <= retentionYears;
    }

    @AssertTrue(message = "timezone must be a valid IANA timezone identifier")
    public boolean isTimezoneValid() {
        try {
            ZoneId.of(timezone);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @AssertTrue(message = "privacy-notice-url must be a valid HTTPS URL if set")
    public boolean isPrivacyNoticeUrlValid() {
        if (privacyNoticeUrl == null || privacyNoticeUrl.isBlank()) return true;
        try {
            var uri = URI.create(privacyNoticeUrl);
            return "https".equals(uri.getScheme()) && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    // Getters and setters (required by @ConfigurationProperties)

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public int getFreezeOffsetYears() { return freezeOffsetYears; }
    public void setFreezeOffsetYears(int freezeOffsetYears) { this.freezeOffsetYears = freezeOffsetYears; }

    public int getRetentionYears() { return retentionYears; }
    public void setRetentionYears(int retentionYears) { this.retentionYears = retentionYears; }

    public int getNodeRetentionExtraYears() { return nodeRetentionExtraYears; }
    public void setNodeRetentionExtraYears(int nodeRetentionExtraYears) { this.nodeRetentionExtraYears = nodeRetentionExtraYears; }

    public String getPrivacyNoticeUrl() { return privacyNoticeUrl; }
    public void setPrivacyNoticeUrl(String privacyNoticeUrl) { this.privacyNoticeUrl = privacyNoticeUrl; }
}
