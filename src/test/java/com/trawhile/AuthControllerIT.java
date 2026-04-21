package com.trawhile;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends BaseIT {

    @Test
    @Tag("TE-F067.F02-01")
    void getProviders_noAuth_returnsConfiguredProviderIds() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providers").isArray())
            .andExpect(jsonPath("$.providers", hasSize(1)))
            .andExpect(jsonPath("$.providers[0]").value("google"))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("clientSecret");
        assertThat(body).doesNotContain("test-client-secret");
    }
}
