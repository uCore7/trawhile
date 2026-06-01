# 0002. Define persistence access boundaries

## Status

- Accepted, 2026-05-15

## Context

This decision answers: how are persistence access paths separated so external-actor operations enforce caller authorization while internal system operations remain explicit and separate?

Generic table repositories make it too easy to load data before authorization or to load broad result sets and filter them in Java. That is structurally weak for externally triggered access to node-scoped business data because it increases over-read risk and makes authorization boundaries harder to review.

Not every persistence operation has the same authorization context. Requests from external actors, including browser users and MCP clients, must enforce caller authorization. Internal system work, such as lifecycle jobs, retention cleanup, startup repair, and metrics, has no external caller and should not be forced through caller-scoped authorization APIs.

The architecture needs an explicit persistence boundary that separates external-actor data access from internal system data access.

## Decision

Do not model persistence as generic repositories in the target architecture.

Use explicit persistence components named by query, command, or internal system operation.

Use `persistence/external/read/` and `persistence/external/command/` for data access triggered by external actors. Method signatures in these components must encode caller scope, such as `actingUserId`, bearer-token subject, or explicit owner-only semantics.

External read and command components must enforce caller authorization in their persistence access rather than relying on callers to filter broad results.

Use `persistence/internal/` for system-owned persistence operations that are not initiated by an external actor and therefore do not have caller authorization semantics.

## Consequences

The intended invariant for external-actor access is that caller scope is visible in the persistence API and enforced by the persistence component.

Persistence APIs are named around business, authorization, or system-operation semantics, for example `findVisibleChildren`, `findOwnDetailedRecords`, `grantAuthorization`, `moveNode`, or `deleteExpiredInvitations`.

Generic names such as `findAll`, `findByNodeId`, and `findByUserId` are discouraged for external-actor reads because they do not expose caller scope in the API.

Internal persistence components remain explicit and reviewable, but they do not pretend to enforce caller authorization where no caller exists.

Persistence tests for external access must verify that external persistence components expose only authorized rows or mutations.
