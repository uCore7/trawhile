package com.trawhile;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TE-F048.F01-01  GET /api/v1/about is public and returns the legal/diagnostic links
 * TE-F048.F01-02  privacy notice URL is shown only to authenticated users with effective node access
 */
@TestPropertySource(properties = "trawhile.privacy-notice-url=https://company.example/privacy")
class AboutIT extends BaseIT {

    @Test
    @Tag("TE-F048.F01-01")
    void getAbout_unauthenticated_returnsVersionSbomOpenApiAndGdprSummary() throws Exception {
        mvc.perform(get("/api/v1/about"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").isString())
                .andExpect(jsonPath("$.licenses").isArray())
                .andExpect(jsonPath("$.sbomUrl").value(containsString("sbom")))
                .andExpect(jsonPath("$.openApiUrl").value(containsString("openapi")))
                .andExpect(jsonPath("$.gdprSummary.storedData").isString())
                .andExpect(jsonPath("$.gdprSummary.retentionPeriod").isString())
                .andExpect(jsonPath("$.gdprSummary.rightToErasure").isString())
                .andExpect(jsonPath("$.privacyNoticeUrl").doesNotExist());
    }

    @Test
    @Tag("TE-F048.F01-02")
    void getAbout_authenticatedWithViewAuth_includesPrivacyNoticeUrlWhenConfigured() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Authorized Reader");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible Node");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "view");

        mvc.perform(get("/api/v1/about")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacyNoticeUrl").value("https://company.example/privacy"));
    }

    @Test
    @Tag("TE-F048.F01-02")
    void getAbout_authenticatedNoAuth_omitsPrivacyNoticeUrl() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "No Access User");

        mvc.perform(get("/api/v1/about")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacyNoticeUrl").doesNotExist());
    }
}
