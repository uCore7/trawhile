package com.trawhile;

import com.trawhile.security.TrawhileOidcUserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TE-F049.F01-01  successful login flow records a security event
 * TE-F049.F01-02  authorization grant records a security event
 * TE-F049.F01-03  MCP token generation records a security event
 * TE-F049.F01-04  MCP token revocation records a security event
 * TE-F049.F02-01  GET /api/v1/security-events supports admin-only filtered listing
 * TE-C007.F01-01  scheduled cleanup deletes security events older than 90 days
 */
class SecurityEventIT extends BaseIT {

    private static final String[] LOGIN_SUCCESS_EVENT_TYPES = {
        "OAUTH_LOGIN_SUCCESS",
        "LOGIN_SUCCESS"
    };

    private static final String[] AUTH_GRANT_EVENT_TYPES = {
        "NODE_ADMIN_GRANTED",
        "AUTH_GRANT"
    };

    @Autowired
    private TrawhileOidcUserService oidcUserService;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @Tag("TE-F049.F01-01")
    void successfulLogin_insertsSecurityEventRow() {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Returning User");
        UUID profileId = jdbc.queryForObject(
            "SELECT id FROM user_profile WHERE user_id = ?",
            UUID.class,
            userId
        );
        jdbc.update(
            "INSERT INTO user_oauth_providers (id, profile_id, provider, subject) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(),
            profileId,
            "google",
            "google-sub-returning-001"
        );

        int before = countEventsByType(LOGIN_SUCCESS_EVENT_TYPES);

        MockHttpSession session = callLoadUserAndReturnSession(
            buildOidcRequest("returning@example.com", "google-sub-returning-001")
        );

        assertThat(session.getAttribute("PENDING_GDPR"))
            .as("successful returning-user login must not enter the GDPR registration flow")
            .isNull();
        assertThat(countEventsByType(LOGIN_SUCCESS_EVENT_TYPES))
            .as("successful OIDC login must create one security_events row")
            .isEqualTo(before + 1);
    }

    @Test
    @Tag("TE-F049.F01-02")
    void grantAuth_insertsSecurityEventRow() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID targetUserId = TestFixtures.insertUserWithProfile(jdbc, "Target User");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        int before = countEventsByType(AUTH_GRANT_EVENT_TYPES);

        mvc.perform(put("/api/v1/nodes/" + nodeId + "/authorizations/" + targetUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"authorization":"view"}
                            """)
                        .with(TestSecurityHelper.adminUser(adminId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(countEventsByType(AUTH_GRANT_EVENT_TYPES))
            .as("granting authorization must create one security_events row")
            .isEqualTo(before + 1);
    }

    @Test
    @Tag("TE-F049.F01-03")
    void mcpTokenGeneration_insertsSecurityEventRow() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Token Owner");

        int before = countEventsByType("MCP_TOKEN_GENERATED");

        mvc.perform(post("/api/v1/account/mcp-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"label":"CLI token"}
                            """)
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.mcpToken.label").value("CLI token"));

        assertThat(countEventsByType("MCP_TOKEN_GENERATED"))
            .as("token generation must create one MCP_TOKEN_GENERATED security event")
            .isEqualTo(before + 1);
    }

    @Test
    @Tag("TE-F049.F01-04")
    void mcpTokenRevocation_insertsSecurityEventRow() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Token Owner");

        mvc.perform(post("/api/v1/account/mcp-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"label":"CLI token"}
                            """)
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isCreated());

        UUID tokenId = jdbc.queryForObject(
            "SELECT id FROM mcp_tokens WHERE user_id = ? AND label = ?",
            UUID.class,
            userId,
            "CLI token"
        );

        int before = countEventsByType("MCP_TOKEN_REVOKED");

        mvc.perform(delete("/api/v1/account/mcp-tokens/" + tokenId)
                        .with(TestSecurityHelper.authenticatedAs(userId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(countEventsByType("MCP_TOKEN_REVOKED"))
            .as("token revocation must create one MCP_TOKEN_REVOKED security event")
            .isEqualTo(before + 1);
    }

    @Test
    @Tag("TE-F049.F02-01")
    void listSecurityEvents_admin_supportsFilterByTypeUserAndDateRange() throws Exception {
        UUID adminId = TestFixtures.insertUserWithProfile(jdbc, "Admin");
        UUID matchingUserId = TestFixtures.insertUserWithProfile(jdbc, "Target User");
        UUID otherUserId = TestFixtures.insertUserWithProfile(jdbc, "Other User");
        TestFixtures.grantAuth(jdbc, adminId, TestFixtures.ROOT_NODE_ID, "admin");

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime from = now.minusDays(2);
        OffsetDateTime to = now.minusHours(1);
        OffsetDateTime matchingOccurredAt = now.minusDays(1);

        UUID matchingEventId = insertSecurityEvent(
            matchingUserId,
            "NODE_ADMIN_GRANTED",
            matchingOccurredAt
        );
        insertSecurityEvent(matchingUserId, "NODE_ADMIN_GRANTED", now.minusDays(4));
        insertSecurityEvent(matchingUserId, "MCP_TOKEN_GENERATED", matchingOccurredAt);
        insertSecurityEvent(otherUserId, "NODE_ADMIN_GRANTED", matchingOccurredAt);

        mvc.perform(get("/api/v1/security-events")
                        .with(TestSecurityHelper.adminUser(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").value(4));

        mvc.perform(get("/api/v1/security-events")
                        .param("eventType", "NODE_ADMIN_GRANTED")
                        .param("userId", matchingUserId.toString())
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .with(TestSecurityHelper.adminUser(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(matchingEventId.toString()))
                .andExpect(jsonPath("$.items[0].userId").value(matchingUserId.toString()))
                .andExpect(jsonPath("$.items[0].eventType").value("NODE_ADMIN_GRANTED"));
    }

    @Test
    @Tag("TE-F049.F02-01")
    void listSecurityEvents_nonAdmin_returns403() throws Exception {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Regular User");

        mvc.perform(get("/api/v1/security-events")
                        .with(TestSecurityHelper.authenticatedAs(userId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("TE-C007.F01-01")
    void scheduledCleanup_deletesEventsOlderThan90Days_retainsRecentEvents() {
        UUID userId = TestFixtures.insertUserWithProfile(jdbc, "Audited User");

        UUID staleEventId = insertSecurityEvent(
            userId,
            "MCP_TOKEN_GENERATED",
            OffsetDateTime.now().minusDays(91)
        );
        UUID recentEventId = insertSecurityEvent(
            userId,
            "MCP_TOKEN_REVOKED",
            OffsetDateTime.now().minusDays(89)
        );

        runSecurityEventCleanup();

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM security_events WHERE id = ?",
            Integer.class,
            staleEventId
        )).as("events older than 90 days must be deleted").isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM security_events WHERE id = ?",
            Integer.class,
            recentEventId
        )).as("events newer than 90 days must be retained").isOne();
    }

    private static ClientRegistration noNetworkRegistration() {
        return ClientRegistration.withRegistrationId("google")
            .clientId("test-client-id")
            .clientSecret("test-client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://oauth2.googleapis.com/token")
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .scope("openid", "email", "profile")
            .build();
    }

    private static OidcUserRequest buildOidcRequest(String email, String subject) {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
            "test-token",
            now,
            now.plusSeconds(300),
            Map.of(
                IdTokenClaimNames.SUB, subject,
                IdTokenClaimNames.ISS, "https://accounts.google.com",
                IdTokenClaimNames.IAT, now,
                IdTokenClaimNames.EXP, now.plusSeconds(300),
                IdTokenClaimNames.AUD, List.of("test-client-id"),
                "email", email,
                "name", "Returning User"
            )
        );
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "test-access-token",
            now,
            now.plusSeconds(300)
        );
        return new OidcUserRequest(noNetworkRegistration(), accessToken, idToken, Map.of());
    }

    private MockHttpSession callLoadUserAndReturnSession(OidcUserRequest request) {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        httpRequest.setSession(session);
        RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(httpRequest, new MockHttpServletResponse())
        );
        try {
            oidcUserService.loadUser(request);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
        return session;
    }

    private UUID insertSecurityEvent(UUID userId, String eventType, OffsetDateTime occurredAt) {
        UUID eventId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO security_events (id, user_id, event_type, occurred_at) VALUES (?, ?, ?, ?)",
            eventId,
            userId,
            eventType,
            occurredAt
        );
        return eventId;
    }

    private int countEventsByType(String... eventTypes) {
        String predicate = IntStream.range(0, eventTypes.length)
            .mapToObj(index -> "event_type = ?")
            .collect(Collectors.joining(" OR "));
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM security_events WHERE " + predicate,
            Integer.class,
            (Object[]) eventTypes
        );
        return count == null ? 0 : count;
    }

    private void runSecurityEventCleanup() {
        List<CleanupHook> candidates = new ArrayList<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            if (targetClass == null || !targetClass.getName().startsWith("com.trawhile.")) {
                continue;
            }
            for (Method method : targetClass.getDeclaredMethods()) {
                if (isSecurityEventCleanupMethod(targetClass, method)) {
                    candidates.add(new CleanupHook(bean, method));
                }
            }
        }

        assertThat(candidates)
            .as("expected exactly one application cleanup hook for 90-day security event retention")
            .hasSize(1);

        CleanupHook hook = candidates.getFirst();
        ReflectionUtils.makeAccessible(hook.method());
        ReflectionUtils.invokeMethod(hook.method(), hook.bean());
    }

    private boolean isSecurityEventCleanupMethod(Class<?> targetClass, Method method) {
        if (method.getParameterCount() != 0) {
            return false;
        }
        String className = targetClass.getSimpleName().toLowerCase(Locale.ROOT);
        String methodName = method.getName().toLowerCase(Locale.ROOT);
        boolean cleanupVerb = methodName.contains("cleanup")
            || methodName.contains("purge")
            || methodName.contains("delete")
            || methodName.contains("expire")
            || methodName.contains("prune")
            || methodName.contains("trigger");
        boolean securityScope = className.contains("security")
            || className.contains("audit")
            || methodName.contains("security")
            || methodName.contains("audit");
        boolean eventScope = className.contains("event") || methodName.contains("event");
        return cleanupVerb && securityScope && eventScope;
    }

    private record CleanupHook(Object bean, Method method) {
    }
}
