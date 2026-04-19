package com.trawhile.web;

import com.trawhile.config.TrawhileConfig;
import com.trawhile.web.api.SettingsApi;
import com.trawhile.web.dto.SystemSettings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/** GET /api/v1/settings — system settings, read-only, any authenticated user (SR-F010.F01). */
@RestController
@RequestMapping("/api/v1")
public class SettingsController implements SettingsApi {

    private final TrawhileConfig trawhileConfig;

    public SettingsController(TrawhileConfig trawhileConfig) {
        this.trawhileConfig = trawhileConfig;
    }

    @Override
    public ResponseEntity<SystemSettings> getSettings() {
        SystemSettings settings = new SystemSettings(
            trawhileConfig.getName(),
            trawhileConfig.getTimezone(),
            trawhileConfig.getFreezeOffsetYears(),
            OffsetDateTime.now().minusYears(trawhileConfig.getFreezeOffsetYears()),
            trawhileConfig.getRetentionYears(),
            trawhileConfig.getNodeRetentionExtraYears()
        );
        if (trawhileConfig.getPrivacyNoticeUrl() != null && !trawhileConfig.getPrivacyNoticeUrl().isBlank()) {
            settings.setPrivacyNoticeUrl(trawhileConfig.getPrivacyNoticeUrl());
        }
        return ResponseEntity.ok(settings);
    }
}
