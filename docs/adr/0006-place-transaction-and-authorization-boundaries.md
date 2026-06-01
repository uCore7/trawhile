# 0006. Place transaction and authorization boundaries

## Status

- Accepted, 2026-05-15

## Context

This decision answers: where are transaction boundaries and explicit authorization checks placed in the backend call path?

Business operations often combine authorization, invariants, persistence changes, and SSE dispatch. The transaction boundary needs to cover the business operation, not controller request handling or individual repository calls.

Spring method-security annotations such as `@PreAuthorize` hide authorization rules away from the service code path. For this application, authorization must remain visible during review and must be easy to test against the domain model.

## Decision

Place `@Transactional` on service methods only.

Do not put `@Transactional` on controllers or persistence components.

For service methods that execute an external-actor operation, check authorization explicitly at the top of the method through `AuthorizationService`.

Do not use `@PreAuthorize`.

## Consequences

Transaction boundaries align with business operations.

Authorization remains visible in service code and code review.

Persistence components stay focused on SQL and data access.

Service methods for external-actor operations must consistently call `AuthorizationService`; external persistence authorization SQL complements this rule for scoped reads and mutations but does not remove the service-layer check. Internal system operations do not require caller authorization semantics, but they must remain separate from external-actor service paths.
