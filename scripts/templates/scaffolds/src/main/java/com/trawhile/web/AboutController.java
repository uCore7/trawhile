package com.trawhile.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** GET /api/v1/about — application version, licenses, SBOM link. Publicly accessible. */
@RestController
@RequestMapping("/api/v1/about")
public class AboutController {

    @Value("${spring.application.version:unknown}")
    private String version;

    @GetMapping
    public Map<String, String> about() {
        return Map.of(
            "version", version,
            "sbom", "/sbom.json"
        );
    }
}
