package com.trawhile.web;

import com.trawhile.config.TrawhileConfig;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/settings — system settings, read-only, any authenticated user (SR-F010.F01). */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final TrawhileConfig trawhileConfig;

    public SettingsController(TrawhileConfig trawhileConfig) {
        this.trawhileConfig = trawhileConfig;
    }

    // TODO: implement SR-F010.F01 — return resolved system configuration
}
