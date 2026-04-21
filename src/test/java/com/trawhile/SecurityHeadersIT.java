package com.trawhile;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityHeadersIT extends BaseIT {

    @Test
    @Tag("TE-C012.C01-01")
    void allResponses_haveContentSecurityPolicyHeader() throws Exception {
        mvc.perform(get("/api/v1/auth/providers").secure(true))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Security-Policy", not(isEmptyString())));
    }

    @Test
    @Tag("TE-C012.C01-02")
    void allResponses_haveStrictTransportSecurityHeader() throws Exception {
        mvc.perform(get("/api/v1/auth/providers").secure(true))
            .andExpect(status().isOk())
            .andExpect(header().exists("Strict-Transport-Security"));
    }

    @Test
    @Tag("TE-C012.C01-03")
    void allResponses_haveXFrameOptionsDeny() throws Exception {
        mvc.perform(get("/api/v1/auth/providers").secure(true))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    @Tag("TE-C012.C01-04")
    void allResponses_haveXContentTypeOptionsNosniff() throws Exception {
        mvc.perform(get("/api/v1/auth/providers").secure(true))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @Tag("TE-C012.C01-05")
    void allResponses_haveReferrerPolicyNoReferrer() throws Exception {
        mvc.perform(get("/api/v1/auth/providers").secure(true))
            .andExpect(status().isOk())
            .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    @Tag("TE-C013.C01-01")
    void postWithValidCsrfToken_proceeds_andMissingCsrfToken_returns403() throws Exception {
        mvc.perform(post("/api/v1/auth/gdpr-notice")
                        .secure(true)
                        .with(csrf()))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/gdpr-notice").secure(true))
            .andExpect(status().isForbidden());
    }
}
