package com.trawhile.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/settings — system settings, read-only, any authenticated user (SR-012). */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {
    // TODO: implement F1.10 — return TrawhileConfig values as SystemSettingsResponse
}
