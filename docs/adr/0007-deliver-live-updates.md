# 0007. Deliver live updates

## Status

- Accepted, 2026-05-15

## Context

This decision answers: how are live updates delivered to active user sessions after visible state changes?

The application needs live updates across all browser sessions of a user after visible state changes. The expected deployment is small and single-tenant, so a full reactive stack is unnecessary.

The backend already uses Spring MVC. Adding WebFlux only for server-sent events would introduce another programming model and more dependencies.

## Decision

Implement SSE with Spring MVC `SseEmitter`.

Keep an application-scoped emitter registry keyed by user id.

Call `SseDispatcher` after every state mutation that affects visible state.

Remove dead emitters on completion, timeout, or send failure. Rely on browser `EventSource` reconnect behaviour.

On reconnect, the Angular client re-fetches current state through REST before processing live events. Do not maintain a server-side event replay buffer.

## Consequences

Live updates use the existing servlet stack.

The implementation stays simple for the expected number of users and sessions.

The server remains the source of truth after reconnects.

Clients may briefly re-fetch state after a dropped connection instead of replaying missed events.
