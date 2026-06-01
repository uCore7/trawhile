# 0009. Route SPA fallback requests

## Status

- Accepted, 2026-05-15

## Context

This decision answers: how are browser route fallback requests distinguished from server-owned routes?

The Angular SPA is served by Spring Boot, and browser navigation to SPA routes must return `index.html` so Angular routing can handle hard refreshes.

The same server also owns REST endpoints, Spring Security endpoints, OAuth2 callbacks, management endpoints, and static assets. A broad fallback could accidentally claim server-owned URLs.

## Decision

Forward only SPA-owned browser routes to `index.html`.

Explicitly exclude server-owned route families from SPA fallback routing. At minimum, exclude `/api`, `/login`, `/oauth2`, and `/login/oauth2`.

When new server-owned route families are introduced, add them to the exclusion list in the same change.

## Consequences

SPA hard refreshes work without a separate web server.

Backend and Spring Security routes remain reachable and unambiguous.

Developers must update the fallback boundary whenever the server URL space changes.
