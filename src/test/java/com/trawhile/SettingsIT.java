package com.trawhile;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TE-F010.F01-01  GET /api/v1/settings — authenticated user receives resolved system config;
 *                 unauthenticated request returns 401.
 */
class SettingsIT extends BaseIT {

    @Test
    @Tag("TE-F010.F01-01")
    void getSettings_authenticatedUser_returnsResolvedConfig() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Member");

        mvc.perform(get("/api/v1/settings")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isOk())
                // All required fields from SR-F010.F01 must be present
                .andExpect(jsonPath("$.name").isString())
                .andExpect(jsonPath("$.timezone").isString())
                .andExpect(jsonPath("$.freezeOffsetYears").isNumber())
                .andExpect(jsonPath("$.effectiveFreezeCutoff").isString())
                .andExpect(jsonPath("$.retentionYears").isNumber())
                .andExpect(jsonPath("$.nodeRetentionExtraYears").isNumber());
    }

    @Test
    @Tag("TE-F010.F01-01")
    void getSettings_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/v1/settings"))
                .andExpect(status().isUnauthorized());
    }
}
