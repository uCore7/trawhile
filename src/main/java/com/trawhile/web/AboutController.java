package com.trawhile.web;

import com.trawhile.config.TrawhileConfig;
import com.trawhile.service.AuthorizationService;
import com.trawhile.web.api.AboutApi;
import com.trawhile.web.dto.AboutInfo;
import com.trawhile.web.dto.AboutInfoGdprSummary;
import com.trawhile.web.dto.AboutInfoLicensesInner;
import com.trawhile.web.dto.PublicAboutInfo;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** GET /api/v1/about — application version, licenses, SBOM link. Publicly accessible. */
@RestController
@RequestMapping("/api/v1")
public class AboutController implements AboutApi {

    private static final String SBOM_URL = "/sbom.json";
    private static final String OPENAPI_URL = "/openapi.yaml";
    private static final String LICENSES_RESOURCE = "3rdpartylicenses.txt";

    private final String version;
    private final TrawhileConfig trawhileConfig;
    private final AuthorizationService authorizationService;

    public AboutController(@Value("${spring.application.version:unknown}") String version,
                           TrawhileConfig trawhileConfig,
                           AuthorizationService authorizationService) {
        this.version = version;
        this.trawhileConfig = trawhileConfig;
        this.authorizationService = authorizationService;
    }

    @Override
    public ResponseEntity<AboutInfo> getAbout() {
        AboutInfo info = new PublicAboutInfo(
            version,
            readLicenses(),
            SBOM_URL,
            OPENAPI_URL,
            buildGdprSummary()
        );

        privacyNoticeUrl().ifPresent(info::setPrivacyNoticeUrl);
        return ResponseEntity.ok(info);
    }

    private AboutInfoGdprSummary buildGdprSummary() {
        return new AboutInfoGdprSummary(
            "Your name and linked OAuth provider identifier are stored. No email address or profile picture is stored.",
            "Activity data is retained for the configured period and then purged automatically.",
            "You may anonymize your account at any time. This is irreversible and requires a new invitation to return."
        );
    }

    private List<AboutInfoLicensesInner> readLicenses() {
        ClassPathResource resource = new ClassPathResource(LICENSES_RESOURCE);
        if (!resource.exists()) {
            return List.of();
        }

        List<AboutInfoLicensesInner> licenses = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String component = null;
            String license = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Package: ")) {
                    component = line.substring("Package: ".length()).trim();
                    continue;
                }
                if (line.startsWith("License: ")) {
                    license = line.substring("License: ".length()).trim().replace("\"", "");
                }
                if (component != null && license != null) {
                    licenses.add(new AboutInfoLicensesInner(component, license));
                    component = null;
                    license = null;
                }
            }
        } catch (IOException e) {
            return List.of();
        }

        return licenses;
    }

    private Optional<String> privacyNoticeUrl() {
        String configuredUrl = trawhileConfig.getPrivacyNoticeUrl();
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return Optional.empty();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        try {
            UUID userId = UUID.fromString(authentication.getName());
            return authorizationService.hasAnyAuthorization(userId)
                ? Optional.of(configuredUrl)
                : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
