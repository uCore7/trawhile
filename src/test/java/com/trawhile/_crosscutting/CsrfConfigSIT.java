package com.trawhile._crosscutting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.trawhile.adapter.inbound.security.SecurityConfig;
import com.trawhile.port.outbound.persistence.ApiKeyLookupPort;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * System Integration Test for SR-00-C21.F01: CSRF bypass for API-key bearer requests
 * and CSRF enforcement for session-only (cookie-based) requests.
 *
 * <p>Uses {@link SpringJUnitWebConfig} with the real {@link SecurityConfig} imported so
 * that the CSRF filter chain is exercised without spinning up the full application context
 * or any database. No Testcontainers. No {@code @SpringBootTest}.</p>
 *
 * <p>A throwaway protected POST endpoint is defined via {@link ThrowawayController}
 * (nested {@code @RestController}) solely to give the test a target for its POST requests.
 * It is never added to {@code src/main/}.</p>
 *
 * <p>Traceability: TE-00-C21.F01-01 → SR-00-C21.F01 → UR-00-C21</p>
 */
@SpringJUnitWebConfig
@ContextConfiguration(classes = {
    SecurityConfig.class,
    CsrfConfigSIT.TestWebConfig.class
})
class CsrfConfigSIT {

    // -------------------------------------------------------------------------
    // Throwaway test controller
    // -------------------------------------------------------------------------

    /**
     * Throwaway protected POST endpoint used exclusively by this test class.
     * Accepts any JSON body and returns 204 No Content when the request passes
     * the security filter chain (both CSRF and authentication must succeed).
     *
     * <p>This controller MUST NOT be added to {@code src/main/}. It exists here
     * only so the test has a concrete target to POST to.</p>
     */
    @RestController
    static class ThrowawayController {

        @PostMapping("/test/csrf-probe")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void probe(@RequestBody(required = false) String body) {
            // No-op — test only verifies the HTTP status coming out of the filter chain.
        }
    }

    // -------------------------------------------------------------------------
    // Test configuration
    // -------------------------------------------------------------------------

    /**
     * Minimal web configuration that wires the throwaway controller and the
     * Spring Security infrastructure for the security filter. Registered via
     * {@code @ContextConfiguration} above.
     *
     * <p>{@code @EnableWebSecurity} imports {@code WebSecurityConfiguration}
     * which provides the {@code HttpSecurity} bean that {@link SecurityConfig}
     * requires. {@code @EnableWebMvc} registers the DispatcherServlet machinery
     * so the throwaway controller is callable.</p>
     */
    @Configuration
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    static class TestWebConfig {

        @Bean
        ThrowawayController throwawayController() {
            return new ThrowawayController();
        }
    }

    // -------------------------------------------------------------------------
    // Infrastructure
    // -------------------------------------------------------------------------

    /**
     * Mock of the outbound persistence port so {@link SecurityConfig} can construct
     * its {@link com.trawhile.adapter.inbound.security.ApiKeyAuthenticationFilter}
     * without a real database. All lookups return empty (key not found), which is
     * the correct "bogus bearer" behaviour for sub-case 1.
     */
    @MockitoBean
    private ApiKeyLookupPort apiKeyLookupPort;

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Wire the full Spring Security filter chain into MockMvc so that the
        // CSRF filter and the ApiKeyAuthenticationFilter are both active.
        mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .apply(springSecurity())
                .build();

        // All API-key lookups return empty (bogus bearer — key not found).
        Mockito.when(apiKeyLookupPort.findValidByHash(Mockito.anyString()))
               .thenReturn(Optional.empty());
    }

    // -------------------------------------------------------------------------
    // Test
    // -------------------------------------------------------------------------

    /**
     * TE-00-C21.F01-01 — three sub-cases covering the CSRF bypass and enforcement logic.
     *
     * <h2>Sub-case 1 — Bearer header present → CSRF filter bypassed, auth fails (not CSRF 403)</h2>
     * <p>A POST with {@code Authorization: Bearer <placeholder>} and no CSRF token must
     * NOT be rejected with HTTP 403 (CSRF). The bearer request bypasses the CSRF filter
     * per SR-00-C21.F01 ({@code ignoringRequestMatchers(API_KEY_REQUEST)} in
     * {@link SecurityConfig}). Because the bearer value is bogus, the API-key lookup
     * returns empty and the filter chain responds with HTTP 401 (auth failure from
     * {@code ApiKeyAuthenticationFilter}). The essential assertion is that the status is
     * NOT 403 — the CSRF filter was bypassed.</p>
     *
     * <h2>Sub-case 2 — No Authorization header + valid CSRF token → auth failure (not CSRF 403)</h2>
     * <p>A POST with a valid CSRF token (injected via {@code csrf()}) and no Authorization
     * header must NOT be rejected at the CSRF layer. The CSRF token is valid so the CSRF
     * filter passes; the request then proceeds to authentication where it fails because no
     * session or credentials are present. The response must be HTTP 4xx but NOT 403 CSRF.
     * (Sub-cases 1 and 2 may fail until SR-01-F13/SR-01-F12 auth wiring lands — the status
     * may be unexpected, but the assertion preserves the SR-00-C21.F01 intent.)</p>
     *
     * <h2>Sub-case 3 — No Authorization header + no CSRF token → 403 CSRF rejection</h2>
     * <p>A POST with no auth and no CSRF token must be rejected by the CSRF filter with
     * HTTP 403. This validates that CSRF protection is active on the default path and that
     * the bypass is scoped only to requests carrying an {@code Authorization: Bearer} header.</p>
     */
    @Test
    @Tag("TE-00-C21.F01-01")
    void bearerAuthorizationBypassesCsrf_sessionWithoutCsrfTokenIsRejected() throws Exception {

        // -----------------------------------------------------------------
        // Sub-case 1: Bearer header present → CSRF bypass, auth fails (NOT 403 CSRF)
        // -----------------------------------------------------------------
        // The bogus bearer value below is an obvious placeholder — not a real token.
        // SR-00-C21.F01: requests with Authorization: Bearer are exempt from CSRF.
        MvcResult bearerResult = mockMvc.perform(
                        post("/test/csrf-probe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Bearer PLACEHOLDER-NOT-A-REAL-KEY")
                                .content("{}"))
                .andReturn();

        int bearerStatus = bearerResult.getResponse().getStatus();
        assertThat(bearerStatus)
                .as("Sub-case 1 (SR-00-C21.F01): A POST with 'Authorization: Bearer <bogus>' "
                    + "and no CSRF token must NOT be rejected with HTTP 403 (CSRF filter "
                    + "must be bypassed for bearer requests per "
                    + "ignoringRequestMatchers(API_KEY_REQUEST) in SecurityConfig). "
                    + "Got HTTP " + bearerStatus + ". "
                    + "If this is 403, the ignoringRequestMatchers rule is not matching "
                    + "'Authorization: Bearer' requests correctly.")
                .isNotEqualTo(403);

        // A bogus bearer causes a 401 auth failure (ApiKeyAuthenticationFilter rejects it).
        // This assertion verifies the CSRF-bypass path ends in auth failure, not CSRF rejection.
        assertThat(bearerStatus)
                .as("Sub-case 1 (SR-00-C21.F01): After bypassing CSRF, a bogus bearer token "
                    + "must cause an HTTP 401 auth failure — ApiKeyAuthenticationFilter finds "
                    + "no valid key for the placeholder value and responds 401 per the "
                    + "ApiKeyAuthenticationFilter.doFilterInternal() contract. "
                    + "Got HTTP " + bearerStatus + ".")
                .isEqualTo(401);

        // -----------------------------------------------------------------
        // Sub-case 2: No Authorization header + valid CSRF token → auth failure (NOT 403 CSRF)
        // -----------------------------------------------------------------
        // with(csrf()) injects a valid CSRF token via SecurityMockMvcRequestPostProcessors.
        MvcResult csrfPresentResult = mockMvc.perform(
                        post("/test/csrf-probe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .with(csrf())
                                .content("{}"))
                .andReturn();

        int csrfPresentStatus = csrfPresentResult.getResponse().getStatus();
        assertThat(csrfPresentStatus)
                .as("Sub-case 2 (SR-00-C21.F01): A POST with a valid CSRF token but no "
                    + "Authorization header must NOT be rejected at the CSRF layer (status "
                    + "must not be 403 CSRF). The CSRF filter passes because the token is "
                    + "valid; the request then reaches the authentication stage and fails. "
                    + "Got HTTP " + csrfPresentStatus + ". "
                    + "If this is 403, the CSRF filter is incorrectly rejecting a request "
                    + "that carries a valid token.")
                .isNotEqualTo(403);

        // Confirm it's a 4xx auth failure (not 2xx which would mean auth somehow passed).
        assertThat(csrfPresentStatus)
                .as("Sub-case 2 (SR-00-C21.F01): After passing the CSRF layer, a "
                    + "session-only request with no credentials must fail authentication "
                    + "(expected HTTP 4xx, not 2xx). Got HTTP " + csrfPresentStatus + ".")
                .isBetween(400, 499);

        // -----------------------------------------------------------------
        // Sub-case 3: No Authorization header + no CSRF token → 403 CSRF rejection
        // -----------------------------------------------------------------
        MvcResult noCsrfResult = mockMvc.perform(
                        post("/test/csrf-probe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andReturn();

        int noCsrfStatus = noCsrfResult.getResponse().getStatus();
        assertThat(noCsrfStatus)
                .as("Sub-case 3 (SR-00-C21.F01): A POST with no Authorization header and no "
                    + "CSRF token must be rejected by the CSRF filter with HTTP 403. "
                    + "Got HTTP " + noCsrfStatus + ". "
                    + "If this is not 403, CSRF protection is not active on the default path, "
                    + "which violates SR-00-C21.F01 — all state-mutating endpoints reached by "
                    + "session-authenticated users must require a valid CSRF token.")
                .isEqualTo(403);
    }
}
