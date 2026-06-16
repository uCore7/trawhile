package com.trawhile._crosscutting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trawhile.adapter.inbound.security.SecurityConfig;
import com.trawhile.port.outbound.persistence.ApiKeyLookupPort;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = CsrfConfigSIT.ThrowawayController.class)
@Import({
    SecurityConfig.class,
    CsrfConfigSIT.ThrowawayEndpointConfiguration.class
})
class CsrfConfigSIT {

    private static final String PROBE_PATH = "/test/csrf-probe";
    private static final String BOGUS_BEARER = "Bearer fake-key-value";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ApiKeyLookupPort apiKeyLookupPort;

    @Test
    @Tag("TE-00-C21.F01-01")
    void bearerAuthorizationBypassesCsrf_sessionWithoutCsrfTokenIsRejected() throws Exception {
        Mockito.when(apiKeyLookupPort.findValidByHash(Mockito.anyString()))
                .thenReturn(Optional.empty());

        MvcResult bearerWithoutCsrf = mockMvc.perform(
                        post(PROBE_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", BOGUS_BEARER)
                                .content("{}"))
                .andReturn();

        int bearerStatus = bearerWithoutCsrf.getResponse().getStatus();
        assertThat(bearerStatus)
                .as("SR-00-C21.C01: bearer requests bypass CsrfFilter, so a bogus API key "
                    + "must fail as authentication, not as HTTP 403 CSRF")
                .isEqualTo(401);
        assertThat(responseBody(bearerWithoutCsrf))
                .as("SR-00-C21.C01: bearer CSRF bypass must not return a CSRF Problem body")
                .doesNotContainIgnoringCase("csrf");

        MvcResult sessionWithCsrf = mockMvc.perform(
                        post(PROBE_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .with(csrf())
                                .content("{}"))
                .andReturn();

        int sessionWithCsrfStatus = sessionWithCsrf.getResponse().getStatus();
        assertThat(sessionWithCsrfStatus)
                .as("SR-00-C21.F01: a valid CSRF token must pass CsrfFilter before the "
                    + "unauthenticated session-only request fails authentication")
                .isEqualTo(401);
        assertThat(responseBody(sessionWithCsrf))
                .as("SR-00-C21.F01: a valid CSRF token must not produce a CSRF rejection body")
                .doesNotContainIgnoringCase("csrf");

        MvcResult sessionWithoutCsrf = mockMvc.perform(
                        post(PROBE_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andReturn();

        int sessionWithoutCsrfStatus = sessionWithoutCsrf.getResponse().getStatus();
        assertThat(sessionWithoutCsrfStatus)
                .as("SR-00-C21.F01: session-authenticated mutating requests without a "
                    + "valid CSRF token must be rejected by CsrfFilter")
                .isEqualTo(403);

        String csrfProblemBody = responseBody(sessionWithoutCsrf);
        assertThat(sessionWithoutCsrf.getResponse().getContentType())
                .as("SR-00-C21.F01: missing CSRF token must return an RFC 7807 Problem body")
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(csrfProblemBody)
                .as("SR-00-C21.F01: missing CSRF token must include a non-empty Problem body")
                .isNotBlank();

        JsonNode problem = objectMapper.readTree(csrfProblemBody);
        assertThat(problem.path("status").asInt())
                .as("SR-00-C21.F01: CSRF Problem body status must mirror HTTP 403")
                .isEqualTo(403);
        assertThat(problem.path("code").asText())
                .as("SR-00-C21.F01: CSRF Problem body must identify the failure as CSRF")
                .containsIgnoringCase("csrf");
    }

    private static String responseBody(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    static class ThrowawayEndpointConfiguration {

        @Bean
        ThrowawayController throwawayController() {
            return new ThrowawayController();
        }
    }

    @RestController
    static class ThrowawayController {

        @PostMapping(PROBE_PATH)
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void probe(@RequestBody(required = false) String body) {
            // Test-only endpoint: behaviour is supplied by the security filter chain.
        }
    }
}
