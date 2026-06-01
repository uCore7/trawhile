# 0004. Store interactive session state

## Status

- Accepted, 2026-05-15

## Context

This decision answers: where is session state for interactive user access backed and managed?

### Client and authentication model

The public Caddy-managed web surface is used by different client types. Interactive users access the application through the served frontend and authenticate through OIDC login, registration, provider-linking, logout, application API, and SSE flows. These flows need server-side session state: the authenticated principal, logout and expiry state, CSRF integration, and temporary data during bootstrap, invitation, and provider-linking flows.

Non-browser clients also exist. MCP clients authenticate each request with bearer tokens on behalf of a user. Their authentication material is presented per request rather than established through an interactive OIDC login flow.

### Discarded alternatives

A custom PostgreSQL sessions table would make sessions part of the application schema. That would add schema surface, retention rules, cleanup jobs, and GDPR review obligations for data that is not part of the domain model.

A stateless JWT-based session model would avoid server-side session storage, but would move revocation, expiry, logout, and temporary login-flow state into application-specific token handling. That is a poor fit for OIDC callback flows, CSRF-protected same-origin requests, and explicit logout semantics.

### Viable alternatives

The two viable backing models are:

- Redis-backed Spring Session
- container-managed `HttpSession`

Redis-backed Spring Session stores interactive user sessions outside the Spring Boot process while preserving the normal Spring Security and servlet-session programming model. It keeps sessions across application restarts, allows any application instance to load the session identified by a client's session cookie without sticky routing, and avoids a later session-store migration if the deployment is scaled horizontally. It does not merge sessions across clients: the same user accessing the application from a phone and a desktop browser normally has separate session cookies and therefore separate sessions.

Redis adds a second stateful runtime dependency in addition to PostgreSQL, but the added operational burden is limited. Session data is transient, so Redis does not need business-data backups. Monitoring is standard through Redis exporters and Grafana dashboards. Redis must still be sized and configured so live session keys are not evicted unexpectedly, and session attributes must remain serialization-compatible across application deployments.

Session lifetime is based on a finite inactivity timeout. Normal authenticated activity refreshes the session's last-access time, so the timeout is sliding rather than fixed from login. Spring Session stores the session expiry in Redis and expired sessions are no longer returned to the application.

Container-managed `HttpSession` stores interactive user sessions in the servlet container of the running Spring Boot instance. It is the smallest deployment option and needs no additional infrastructure. Its drawbacks are that sessions are local to one application instance, are normally lost on application restart, and require either sticky routing or a later move to shared sessions when multiple application instances are introduced.

For the expected single application instance behind Caddy, Redis-backed Spring Session provides session continuity across application restarts and the same session backing model for a future multi-instance deployment. Container-managed `HttpSession` keeps the production runtime smaller, with sessions local to the application process.

## Decision

Use Spring Session backed by Redis for interactive user sessions.

Rationale: routine deployments, security updates, and other DevSecOps-driven restarts should not unnecessarily invalidate interactive user sessions. This matters for normal authenticated work and for temporary login-flow state during registration, bootstrap, and provider linking. Redis-backed Spring Session also avoids a later change to the session backing model if multiple application instances are added. The extra dependency is acceptable because Redis is a standard infrastructure component, session data is transient, and the operational requirements are limited to normal Redis sizing, monitoring, and internal access control.

Do not add a custom PostgreSQL sessions table.

Do not use stateless JWTs as the interactive user session state.

Do not rely on container-local session storage as the production backing store.

Do not use `HttpSession` as the authentication mechanism for bearer-token clients.

Configure a finite maximum inactive interval for interactive sessions so Redis-backed session entries expire after inactivity.

## Consequences

Session storage remains an infrastructure detail rather than a domain schema concern.

Logout and session expiry follow Spring Security and Spring Session behaviour.

Redis becomes part of the production runtime for the Caddy-managed web surface.

Multiple application instances can load the same client session without sticky routing.

Application deployments must keep session attributes serialization-compatible or invalidate existing sessions deliberately.

Expired sessions are no longer usable by the application. Physical removal of Redis keys follows Redis expiration behaviour and is eventual rather than a synchronous domain cleanup transaction.
