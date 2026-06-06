package com.trawhile._crosscutting;

import static org.assertj.core.api.Assertions.assertThat;

import com.trawhile.BaseIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Integration tests for SR-00-C22.F01: unauthenticated requests must receive a
 * generic error response and must not leak internal information via HTTP response
 * headers or error body content (no version strings, no server identity, no stack
 * traces, no OpenAPI hints).
 *
 * <p>The test uses {@link RestTemplate} against {@link LocalServerPort} with a
 * no-op {@link DefaultResponseErrorHandler} so that 4xx/5xx responses are returned
 * as {@link ResponseEntity} rather than thrown as exceptions, enabling AssertJ
 * assertions on status codes with clear failure messages. This matches the pattern
 * established by {@code MetricsIT} and {@code AuthAdapterIT}.</p>
 *
 * <p>Traceability: TE-00-C22.F01-01 → SR-00-C22.F01 → UR-00-C22</p>
 */
@TestPropertySource(properties = {
    // Switch Spring Session to the in-memory simple store so no Redis connection
    // is required during tests (mirrors AuthAdapterIT).
    "spring.session.store-type=simple",
    "spring.data.redis.host=",
    "spring.data.redis.port=0"
})
class EdgeProtectionIT extends BaseIT {

    @LocalServerPort
    private int port;

    /** No-op RestTemplate shared across all test sub-cases in this class. */
    private RestTemplate restTemplate;

    @BeforeEach
    void buildRestTemplate() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(
                    org.springframework.http.client.ClientHttpResponse response)
                    throws java.io.IOException {
                return false;
            }
        });
    }

    /**
     * TE-00-C22.F01-01: An unauthenticated request to a protected endpoint must
     * receive a generic HTTP 401 error response that does not leak:
     * <ul>
     *   <li>the application version or dependency version strings;</li>
     *   <li>the application server identity ({@code Server}, {@code X-Powered-By});</li>
     *   <li>stack trace fragments ({@code at org.springframework}, {@code at java.base});</li>
     *   <li>OpenAPI, Swagger, or About-page field content
     *       ({@code OpenAPI}, {@code swagger}, {@code applicationVersion}).</li>
     * </ul>
     *
     * <p>Sub-case 1: {@code GET /api/about} — an auth-gated endpoint per SR-05-F06.F01
     * that requires authentication. The anonymous response must be HTTP 401 and its
     * body and headers must contain no disclosure tokens.</p>
     *
     * <p>Sub-case 2: {@code GET /actuator/info} — the management info endpoint is
     * not included in the public management exposure list ({@code application.yml}
     * lists only {@code prometheus} and {@code health}); it must therefore be
     * inaccessible (HTTP 401 or HTTP 404).</p>
     *
     * <p>Sub-case 3: {@code POST /api/nonexistent-endpoint-that-does-not-exist} —
     * an anonymous POST to a nonexistent path must receive HTTP 401 or HTTP 403,
     * NOT HTTP 404. Returning 404 would confirm that the path does not exist,
     * which is itself an information leak (per SR-00-C22.F01's intent: error
     * responses to unauthenticated requests use generic Problem documents that
     * do not reveal whether the path exists or not).</p>
     */
    @Test
    @Tag("TE-00-C22.F01-01")
    void unauthenticatedRequestReceivesGenericErrorWithoutInformationLeak() {

        // -----------------------------------------------------------------------
        // Sub-case 1: GET /api/about with no auth credentials
        // -----------------------------------------------------------------------
        ResponseEntity<String> aboutResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/about",
                String.class);

        assertThat(aboutResponse.getStatusCode().value())
                .as("GET /api/about with no auth must return HTTP 401 "
                    + "(auth-gated endpoint per SR-05-F06.F01)")
                .isEqualTo(401);

        String aboutBody = aboutResponse.getBody() == null ? "" : aboutResponse.getBody();

        // Body must not contain version strings in the pattern '<appname> <semver>'
        assertThat(aboutBody)
                .as("Response body for unauthenticated /api/about must not contain "
                    + "a version string in the form 'trawhile <version>' "
                    + "(SR-00-C22.F01: no application version leak)")
                .doesNotContainPattern("trawhile\\s+\\d+\\.\\d+");

        // Body must not reveal the application server stack
        assertThat(aboutBody)
                .as("Response body must not contain 'Tomcat' (server identity leak, "
                    + "SR-00-C22.F01)")
                .doesNotContainIgnoringCase("tomcat");

        assertThat(aboutBody)
                .as("Response body must not contain 'Jetty' (server identity leak, "
                    + "SR-00-C22.F01)")
                .doesNotContainIgnoringCase("jetty");

        assertThat(aboutBody)
                .as("Response body must not contain 'Undertow' (server identity leak, "
                    + "SR-00-C22.F01)")
                .doesNotContainIgnoringCase("undertow");

        // Body must not contain stack-trace fragments
        assertThat(aboutBody)
                .as("Response body must not contain Spring stack trace frames "
                    + "('at org.springframework') — SR-00-C22.F01: no stack traces")
                .doesNotContain("at org.springframework");

        assertThat(aboutBody)
                .as("Response body must not contain JDK stack trace frames "
                    + "('at java.base') — SR-00-C22.F01: no stack traces")
                .doesNotContain("at java.base");

        // Body must not contain OpenAPI / Swagger / About-page field content
        assertThat(aboutBody)
                .as("Response body must not contain 'OpenAPI' — SR-00-C22.F01 / "
                    + "SR-00-C22.C01: OpenAPI spec is auth-gated")
                .doesNotContainIgnoringCase("openapi");

        assertThat(aboutBody)
                .as("Response body must not contain 'swagger' — SR-00-C22.F01 / "
                    + "SR-00-C22.C01: Swagger UI must not leak via error body")
                .doesNotContainIgnoringCase("swagger");

        assertThat(aboutBody)
                .as("Response body must not contain the 'applicationVersion' field "
                    + "— SR-00-C22.F01 / SR-05-F06.F01: About-page fields must not "
                    + "appear in unauthenticated error responses")
                .doesNotContain("applicationVersion");

        // Response headers must not reveal server identity
        assertThat(aboutResponse.getHeaders().get("Server"))
                .as("Response must not carry a 'Server' header that reveals the "
                    + "application-server identity — SR-00-C22.F01")
                .isNullOrEmpty();

        assertThat(aboutResponse.getHeaders().get("X-Powered-By"))
                .as("Response must not carry an 'X-Powered-By' header — "
                    + "SR-00-C22.F01")
                .isNullOrEmpty();

        // -----------------------------------------------------------------------
        // Sub-case 2: GET /actuator/info with no auth on the public port
        // -----------------------------------------------------------------------
        // application.yml exposes only 'prometheus' and 'health' on the management
        // port. /actuator/info is therefore either 404 (not exposed) or 401
        // (exposed but auth-gated). In neither case should it be reachable and
        // return content on the public port.
        ResponseEntity<String> actuatorInfoResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/info",
                String.class);

        int actuatorInfoStatus = actuatorInfoResponse.getStatusCode().value();
        assertThat(actuatorInfoStatus)
                .as("GET /actuator/info on the public port must NOT be reachable "
                    + "anonymously — expected HTTP 401 (auth-gated) or 404 (not "
                    + "exposed), got " + actuatorInfoStatus
                    + " — SR-00-C22.F01: management endpoints must not leak info "
                    + "to anonymous callers")
                .isIn(401, 404);

        // -----------------------------------------------------------------------
        // Sub-case 3: POST to a nonexistent path with no auth
        // -----------------------------------------------------------------------
        // Per SR-00-C22.F01, error responses to unauthenticated requests must be
        // generic. Returning HTTP 404 on a nonexistent path would distinguish
        // "path not found" from "needs auth", which is itself an information leak
        // (an attacker could enumerate which paths exist). The response must be
        // 401 or 403 — the auth filter must fire before the routing/404 stage.
        ResponseEntity<String> nonexistentResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/nonexistent-endpoint-that-does-not-exist",
                HttpMethod.POST,
                new HttpEntity<>(new HttpHeaders()),
                String.class);

        int nonexistentStatus = nonexistentResponse.getStatusCode().value();
        assertThat(nonexistentStatus)
                .as("POST /api/nonexistent-endpoint-that-does-not-exist with no auth "
                    + "must return HTTP 401 or HTTP 403, NOT HTTP 404 — SR-00-C22.F01: "
                    + "returning 404 on an unknown path leaks path-existence information "
                    + "to unauthenticated callers; got " + nonexistentStatus)
                .isIn(401, 403);
    }
}
