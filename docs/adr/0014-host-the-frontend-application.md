# 0014. Host the frontend application

## Status

- Accepted, 2026-05-15

## Context

This decision answers: where is the Angular SPA served from in production?

Production deployment should remain simple: one application container behind Caddy and one PostgreSQL database. The Angular SPA is static after build and does not require a separate Node server in production.

The OWASP-related HTTP hardening requirements require consistent handling for the public application surface. That surface includes the SPA shell, static assets, SPA fallback routes, OAuth2 endpoints, and API endpoints.

The main alternatives are serving SPA assets directly from Caddy, adding a separate static frontend container, publishing the SPA to external static hosting or a CDN, or adding a Node production server. These alternatives either add another deployable unit or move SPA fallback routing and same-origin browser behavior out of the Spring Boot application.

Serving the SPA from a different origin would also require CORS for credentialed browser requests. That would make session cookies, CSRF tokens, OAuth2 redirects, SSE connections, and preflight handling more delicate. Same-origin serving keeps browser security policy simple: the SPA, REST API, OAuth2 callback paths, and SSE endpoint share one origin.

## Decision

Build the Angular SPA into Spring Boot static resources for production packaging.

Serve the SPA from Spring Boot behind Caddy so all production browser routes share one public origin and one application route space after edge handling.

Run Angular separately only during native development.

## Consequences

Production has one application process for both REST API and static frontend assets.

Caddy terminates TLS, applies baseline edge abuse controls, and reverse-proxies accepted requests to Spring Boot.

Spring Boot can apply response security headers consistently for the complete public application surface after Caddy edge handling.

The production browser client does not require CORS to call the REST API or open the SSE stream.

The Maven build must integrate the frontend production build.

Frontend development can still use `ng serve` independently in native development.
