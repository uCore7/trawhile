# 0013. Manage frontend state

## Status

- Accepted, 2026-05-15

## Context

This decision answers: how is frontend application state managed so the Angular SPA reacts consistently to user interaction, API results, and server-visible state changes?

Durable business state is owned by the backend. The Angular SPA still needs browser-local state for interaction, in-progress forms, route/view selection, loading/error state, cached read models, and SSE-updated views.

This decision is about state and reactivity. Responsive layout and visual component choices are separate frontend UI decisions.

### State-management alternatives

Plain Angular services with signals are lightweight and idiomatic for local feature state. They keep dependencies low and are easy to understand for small flows. Their drawback is that cross-feature state transitions, SSE event application, loading/error conventions, and derived view models remain local design choices. As the SPA grows, this can make state changes harder to trace and compare across features.

NgRx introduces a structured model of actions, reducers, effects, selectors, and store devtools. Its advantages are explicit state transitions, a single place for shared application state, testable side effects, and consistent handling of server-derived read models and SSE updates. Its drawbacks are additional dependencies, boilerplate, and a stronger frontend architecture that must be applied consistently to avoid accidental complexity.

Keeping all logic in smart components is simple at first, but it couples rendering, routing, API calls, store access, and view-model construction. A container/presenter split adds more files and naming discipline, but separates state orchestration from rendering. Presenters can render view models and emit user intents without depending on NgRx, routing, or backend APIs; containers can connect routing and NgRx state.

Purely local component state remains useful for ephemeral interaction details such as an open menu, focused control, drag state, or unsaved form text that does not need cross-component coordination, URL persistence, or server persistence.

## Decision

Use NgRx for shared frontend application state, including server-derived read models, current-user/session state, loading/error state that spans components, and SSE-driven updates.

Use effects for asynchronous application API calls and SSE event handling.

Use selectors to derive view models for components.

Adopt the presenter pattern for feature UI: container components connect routing and NgRx selectors/actions; presenter components receive data through inputs and emit user intents through outputs.

Keep purely local, ephemeral interaction state in component-local state when it does not need cross-component coordination, URL persistence, or server persistence.

## Consequences

Frontend state changes become explicit through NgRx actions, effects, reducers, and selectors.

Presenter components are easier to test and reuse because they do not depend on NgRx or backend services.

The SPA has more frontend structure and boilerplate than a services-only approach.

Developers must decide deliberately whether state belongs in NgRx, the URL, component-local state, or the backend.
