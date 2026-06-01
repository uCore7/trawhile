# 0001. Choose backend architecture style

## Status

- Accepted, 2026-05-15

## Context

This decision answers: which fundamental application architecture style structures the backend?

The application has several inbound access paths: browser-facing application API routes, OIDC callback handling, MCP requests, SSE connection lifecycle, and scheduled lifecycle jobs. It also has several technical dependencies and deployment concerns: PostgreSQL persistence, Redis-backed interactive sessions, OIDC providers, Caddy edge behavior, and observability.

Testability is important. Use-case behavior should be testable without binding every test to HTTP controllers, Redis, OIDC provider mechanics, or detailed SQL shape.

The main alternatives are classic layered architecture, onion architecture, and ports-and-adapters architecture.

Classic layered architecture is simple and maps well to Spring MVC projects. Its drawback is that layers often become technical buckets, and inbound adapters, use-case orchestration, and outbound dependencies can become coupled through framework and persistence details.

Onion architecture gives strong dependency direction toward a domain model. Its drawback for this system is that the core domain is expressed mainly through use cases, workflow rules, authorization boundaries, and persistence contracts rather than through a rich persistence-independent domain object model.

Ports and adapters separates the application core from inbound protocols and outbound infrastructure. It fits multiple access paths and improves testability by making adapters replaceable at the boundary. Its drawback is additional interface and adapter structure that must stay use-case-shaped rather than becoming generic ceremony.

## Decision

Use a ports-and-adapters architecture with a use-case/service core.

The application core is organized around application services that implement use cases. A service method owns the use-case flow: authorization entry checks, business invariants, transaction boundary, calls to persistence ports, and mutation side effects such as SSE dispatch.

Inbound adapters translate external interaction styles into use-case calls. These include browser-facing application routes, OIDC callback handling, MCP requests, SSE connection handling, and scheduled lifecycle triggers.

Outbound adapters implement technical dependencies. Persistence ports expose use-case-shaped or read-model-shaped capabilities to the core. PostgreSQL/jOOQ adapters implement those ports and may use PostgreSQL authorization functions, joins, constraints, and generated schema objects internally.

Do not make controllers, MCP handlers, scheduled jobs, jOOQ code, or PostgreSQL functions part of the application core.

Do not introduce broad abstract ports for every framework service by default. Add a port when it protects a real boundary for use-case logic, testability, or multiple adapters.

## Consequences

The core depends on ports and application contracts, not on HTTP, MCP, Redis, OIDC provider details, jOOQ DSL, or PostgreSQL syntax.

Persistence access is not generic CRUD. Ports and adapters are shaped around caller scope, use cases, read models, and commands.

PostgreSQL authorization functions remain an implementation mechanism of the persistence adapter. They enforce authorization structurally at the data-access boundary, but they do not define the macro architecture style.

Tests can exercise use-case behavior through service APIs and replace adapter implementations where useful.

The architecture still uses internal layering, but layering is subordinate to the ports-and-adapters dependency direction.
