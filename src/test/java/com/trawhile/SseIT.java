package com.trawhile;

import com.trawhile.sse.SseEmitterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SseIT extends BaseIT {

    @Autowired
    private SseEmitterRegistry sseEmitterRegistry;

    @AfterEach
    void clearEmitters() {
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<UUID, ?> emitters =
            (ConcurrentHashMap<UUID, ?>) ReflectionTestUtils.getField(sseEmitterRegistry, "emitters");
        if (emitters != null) {
            emitters.clear();
        }
    }

    @Test
    @Tag("TE-F068.F01-01")
    void trackingChange_dispatchedToAllSessionsOfSameUser() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Tracked User");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Trackable Task");
        TestFixtures.grantAuth(jdbc, userId, nodeId, "track");

        MvcResult stream = openEventStream(userId);

        mvc.perform(post("/api/v1/tracking/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nodeId":"%s","timezone":"UTC"}
                            """.formatted(nodeId))
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
            .andExpect(status().isOk());

        assertThat(awaitContent(stream, "TRACKING_STATUS", Duration.ofSeconds(2)))
            .contains("TRACKING_STATUS")
            .contains(nodeId.toString());
    }

    @Test
    @Tag("TE-F068.F01-02")
    void nodeUpdate_dispatchedToUsersWithAtLeastViewOnNode() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID viewerId = TestFixtures.insertUserWithProfile(jdbc, "Viewer");
        UUID outsiderId = TestFixtures.insertUserWithProfile(jdbc, "Outsider");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Original Name");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");
        TestFixtures.grantAuth(jdbc, viewerId, nodeId, "view");

        MvcResult viewerStream = openEventStream(viewerId);
        MvcResult outsiderStream = openEventStream(outsiderId);

        mvc.perform(patch("/api/v1/nodes/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Updated Name"}
                            """)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
            .andExpect(status().isOk());

        assertThat(awaitContent(viewerStream, "NODE_CHANGE", Duration.ofSeconds(2)))
            .contains("NODE_CHANGE")
            .contains(nodeId.toString());
        assertThat(awaitContent(outsiderStream, "NODE_CHANGE", Duration.ofMillis(400)))
            .as("users without view authorization must not receive node-update SSE events")
            .doesNotContain("NODE_CHANGE");
    }

    @Test
    @Tag("TE-F068.F01-03")
    void authorizationChange_dispatchedToAffectedUser() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID affectedUserId = TestFixtures.insertUserWithProfile(jdbc, "Affected User");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Node");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        MvcResult stream = openEventStream(affectedUserId);

        mvc.perform(put("/api/v1/nodes/" + nodeId + "/authorizations/" + affectedUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"authorization":"view"}
                            """)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
            .andExpect(status().isNoContent());

        assertThat(awaitContent(stream, "AUTHORIZATION_CHANGE", Duration.ofSeconds(2)))
            .contains("AUTHORIZATION_CHANGE")
            .contains(nodeId.toString());
    }

    private MvcResult openEventStream(UUID userId) throws Exception {
        return mvc.perform(get("/api/v1/events")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .with(TestSecurityHelper.authenticatedAs(userId)))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andReturn();
    }

    private String awaitContent(MvcResult result, String token, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        String content = result.getResponse().getContentAsString();
        while (!content.contains(token) && System.nanoTime() < deadline) {
            Thread.sleep(50);
            content = result.getResponse().getContentAsString();
        }
        return content;
    }
}
