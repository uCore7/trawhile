# 0017. Shape SSE event payloads

## Status

- Proposed, 2026-06-01

## Context

This decision answers: what should an SSE event payload carry — the change that occurred, or the new state of the affected resource?

ADR 0007 chose SSE with Spring MVC `SseEmitter` as the live-update delivery mechanism and decided against a server-side replay buffer; on reconnect the client re-fetches current state through REST before processing live events. That ADR left the **shape of the event payload itself** open. Two shapes are commonly used, and a hybrid is possible.

**Option A: delta / command events.** The payload describes what changed. Examples: `{type: "node_renamed", nodeId: "...", newName: "..."}`, `{type: "node_deleted", nodeId: "..."}`, `{type: "tracking_started", recordId: "...", nodeId: "...", startedAt: "..."}`.

Advantages: small payloads; the event description is self-explanatory; close to event-sourcing intuition; useful for audit-style consumers that want to know *what happened*.

Disadvantages: the client must maintain delta-application logic per event type and per resource (apply rename, remove from tree, insert in correct parent's children, etc.); a delete-then-recreate sequence with the same identifier produces a wrong end state if any event in the middle is lost; on reconnect, replaying deltas from "what the client last had" requires either a server-side replay buffer (rejected in ADR 0007) or a REST refresh followed by re-listening, which may briefly show stale state between the refresh and the next delta; the SSE payload shape is a new schema that must stay in sync with the REST contract independently.

**Option B: snapshot / state events.** The payload is the current state of the affected resource (or the affected slice of it for the current recipient). Examples: `TrackingChanged` carries the recipient's current `TimeRecord` (or `null` if stopped); `NodeTreeChanged` carries the recipient's full visible node subtree; `AuthorizationChanged` carries the recipient's full effective-authorizations list.

Advantages: receipt is idempotent at the application level — the same event applied twice produces the same state; missing intermediate events does not corrupt the client because the next event re-establishes truth; reducer logic on the client is trivial (`state.tracking = payload`); the payload shape can be exactly the response type of the corresponding REST endpoint, so the backend computes one read and reuses it for both REST replies and SSE pushes, and the OpenAPI contract directly types both channels; the REST refresh on reconnect becomes optional for snapshot topics because the next event will refresh the slice anyway.

Disadvantages: payload size grows from "what changed" to "current state"; per-recipient computation is needed because the visible slice depends on the recipient's authorizations (`NodeTreeChanged` carries different content for each user); some events have no natural snapshot form because the state being conveyed is "gone" (e.g., "your invitation was withdrawn", "your account was anonymised by an administrator") — these are inherently command-shaped.

**Option C: hybrid.** Use snapshot events for *state-shaped* resources and command events for *action-shaped* notifications.

Trawhile's anticipated event types fall naturally into these two camps:

- *State-shaped* (snapshot fits): the live tracking record, the visible node tree, the recipient's effective authorizations, the profile.
- *Action-shaped* (command fits): "invitation withdrawn", "account anonymised by admin", "new advisory affecting the running version".

Disadvantages of the hybrid: two payload conventions exist on the same channel; the client's typed event dispatcher must know which event types are snapshots and which are commands; the convention must be documented and applied consistently as new event types are added.

Constraints from the deployment context:

- The deployment target is single-VPS Docker Compose (UR-00-C12), so total event throughput is small and per-event bandwidth is not a binding constraint at the expected scale.
- The visible-tree payload size for a typical small-company instance (estimated low hundreds of nodes) is on the order of tens of kilobytes per event when serialised; well within what SSE handles cleanly over a same-origin TLS connection.
- The architecture already commits to OpenAPI as the typed contract between backend and frontend; reusing REST response types as SSE payloads directly leverages that contract.
- ADR 0007 already accepts that reconnecting clients may briefly re-fetch state through REST, which is naturally compatible with snapshot semantics.

## Decision

Adopt **Option C**: hybrid SSE event payload shape.

State-shaped resources are pushed as snapshot events whose payload is the response type of the corresponding REST query for the recipient's visible slice. The same backend service method computes the value and serves it on both channels (REST query and SSE push). The current state-shaped event types are:

- `TrackingChanged` — payload is the recipient's current `TimeRecord` (or `null`).
- `NodeTreeChanged` — payload is the recipient's full visible node subtree.
- `AuthorizationChanged` — payload is the recipient's full effective-authorizations list.
- `AccountChanged` — payload is the recipient's profile snapshot.

Action-shaped notifications remain command events whose payload describes the action and identifies the affected entities. Examples include: invitation lifecycle events the recipient cannot derive from current state (withdrawn, expired), account-termination notifications, and security-advisory notifications.

When a new event type is introduced, it is classified at design time. If a single REST query exists that returns the resource's current state in the same shape the client needs, the event is a snapshot. Otherwise it is a command.

## Consequences

Snapshot events are idempotent on receipt: replaying a snapshot produces no state divergence, and missed intermediate snapshots are harmless because the next snapshot re-establishes truth. The "delete-then-recreate with the same identifier between events" failure mode is removed for snapshot topics.

Backend code reuses one service method per state-shaped topic for both REST responses and SSE event payloads. The same OpenAPI type names the payload of both channels; the contract is verifiable at build time.

The REST refresh on reconnect, mandated by ADR 0007, remains the explicit recovery for command topics and the initial-load mechanism for snapshot topics, but is no longer required for snapshot topics to *remain* current once a fresh event has arrived.

Per-recipient computation is required for snapshot events whose visible content depends on authorization (notably `NodeTreeChanged` and `AuthorizationChanged`). At the expected scale this is acceptable; if event frequency rises, the optimisation is to compute the base resource once per change and apply per-recipient filtering on emit rather than per-recipient query.

Frontend NgRx effects subscribing to snapshot events are simpler: receive event, dispatch a single `*Loaded` action carrying the payload, reducer replaces the slice. The same `*Loaded` action handles both initial REST load and SSE-driven updates, so no separate delta-application path exists in reducers.

Two payload conventions now coexist on the SSE channel. Adding a new event type requires the design step of classifying it as snapshot or command and matching the snapshot ones to an existing or new REST query shape. The frontend's typed event dispatcher must distinguish the two kinds in its handler dispatch.

Payload size increases for state-shaped events relative to delta encoding. At the deployment scale fixed by UR-00-C12 this is not a binding constraint; if a future deployment context tightens it, the snapshot strategy can be re-evaluated per event type without affecting the rest of the architecture.
