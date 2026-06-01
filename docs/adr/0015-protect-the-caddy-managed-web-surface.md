# 0015. Protect the Caddy-managed web surface

## Status

- Accepted, 2026-05-15

## Context

This decision answers: which web security and baseline edge abuse-control mechanisms does the Caddy-managed web surface use? The backing model for interactive user session state is decided in ADR 0004.

### Public access model

The system exposes a public Caddy-managed web surface. Some routes are intentionally reachable without an authenticated application session, including the SPA shell, static assets, login and OAuth2 routes, and public information or download routes. Other routes require credentials, such as authenticated application API and SSE traffic from browser sessions and MCP requests using bearer tokens.

No Caddy-managed web application traffic should be served over plain HTTP. If port 80 is exposed, it is used only for HTTP-to-HTTPS redirects and, depending on the configured ACME challenge type, certificate validation. The deployment can avoid plain HTTP certificate validation by using TLS-ALPN-01 or DNS-01.

Management and database interfaces are deployment-internal and are outside the public edge-abuse surface addressed by this decision.

### Abuse and protection need

Unauthenticated public routes can be requested by any internet client. Abuse is not limited to high request volume: clients can also hold connections open, send request bodies slowly, retry authentication flows, or create many long-lived connection attempts. Even when requests are later rejected by Spring Security, they may already have consumed connection capacity, application threads, authentication or session processing, or other server resources.

Authenticated routes can also be abused by valid users or tokens, but baseline abuse protection must not depend on a successfully authenticated principal. Request-rate controls and timeout controls therefore need to apply at the public edge entry point before application processing. Application-specific limits that require user identity or domain semantics can be added inside Spring Boot later.

Mutating requests need CSRF protection. The Caddy-managed web surface also needs baseline hardening through security headers and edge abuse controls.

### Edge abuse-control alternatives

Caddy can provide several edge abuse controls, including HTTPS enforcement, request-size limits, timeouts, connection handling, forwarding-header normalization, path/method rejection, access logs, metrics, and rate limiting through a suitable module. Baseline rate limiting could instead be implemented with bucket4j in the Spring Boot application, with a custom servlet filter, or through Spring Cloud Gateway. As the public reverse proxy, Caddy is the natural edge enforcement point: it can reject excessive traffic before it consumes application-container threads, Spring Security processing, or database resources. bucket4j would keep the policy inside the application, but would let abusive traffic reach Spring Boot before rejection. A custom servlet filter would require hand-rolled token-bucket behavior and metrics, and Spring Cloud Gateway would add a gateway stack that is otherwise not needed.

Caddy has Prometheus instrumentation and access logging, so rate-limit rejections can be observed operationally at the edge. Application-specific limits that depend on authenticated user identity or domain semantics may still be added inside Spring Boot later, but they are separate from baseline public edge rate limiting.

## Decision

Use Spring Security for browser authentication, CSRF protection, and request security, using the Redis-backed Spring Session model from ADR 0004.

Enable CSRF protection and expose the token through a cookie/header pattern that Angular can use.

Set security headers, including frame protection, content security policy, and HSTS.

Serve production Caddy-managed web application traffic over HTTPS only.

Apply baseline edge abuse controls in Caddy for public application routes before requests are proxied to Spring Boot.

Use Caddy token-bucket rate limiting as the baseline request-rate control.

Configure Caddy request and connection timeouts as part of the edge abuse-control baseline.

Do not use bucket4j for baseline public edge rate limiting.

## Consequences

The browser client can use same-origin session cookies for authenticated API calls.

CSRF protection remains active for mutating requests.

Baseline edge abuse controls apply before expensive authentication or application processing.

Timeouts protect against slow-client, slow-upload, and idle-connection abuse that token-bucket rate limiting does not address.

Rate-limit rejections are observable through Caddy metrics and logs rather than application `security_events` rows.

Security policy is split intentionally: Caddy owns edge abuse controls and TLS termination; Spring Security owns browser sessions, CSRF, and response security headers.

Timeout values become operational security parameters and must be documented with the production Caddy configuration.

The production Caddy image must include the chosen rate-limiting module; the stock Caddy image is not sufficient if it lacks HTTP rate-limiting support.
