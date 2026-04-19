package com.trawhile;

import com.trawhile.service.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BaseITSmokeTest extends BaseIT {

    @Autowired
    private AuthorizationService authorizationService;

    @Test
    void resetDatabaseLeavesOnlySeedData() {
        Integer userCount = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        Integer rootCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM nodes WHERE id = ?",
            Integer.class,
            TestFixtures.ROOT_NODE_ID
        );
        Integer purgeJobCount = jdbc.queryForObject("SELECT COUNT(*) FROM purge_jobs", Integer.class);

        assertThat(userCount).isZero();
        assertThat(rootCount).isEqualTo(1);
        assertThat(purgeJobCount).isEqualTo(2);
    }

    @Test
    void fixturesAndAuthenticationWorkTogether() throws Exception {
        UUID userId = TestFixtures.insertUser(jdbc);
        TestFixtures.grantAuth(jdbc, userId, TestFixtures.ROOT_NODE_ID, "admin");

        assertThat(authorizationService.hasAdmin(userId, TestFixtures.ROOT_NODE_ID)).isTrue();

        mvc.perform(get("/api/v1/events").with(TestSecurityHelper.authenticatedAs(userId)))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted());
    }
}
