# 0016. Structure responsive browser UI

## Status

- Accepted, 2026-05-15

## Context

This decision answers: how does the Angular frontend realize the responsive browser UI constraint across desktop and mobile browser contexts?

UR-C014 states that the planned frontend delivery channel is a responsive browser-based application and that no separate native mobile application is planned at this time. Architecture constraints already fix the frontend platform. Native application delivery and component or styling framework selection are therefore context for this decision, not alternatives decided here.

The responsive UI decision is separate from frontend state management. ADR 0013 decides NgRx, effects, selectors, and presenter/container state orchestration. This decision concerns the visual and interaction structure used to make the browser UI work across desktop and mobile contexts.

The main alternatives are:

- a single responsive Angular UI using the fixed frontend stack
- separate desktop and mobile browser UI structures in the Angular application

Separate desktop and mobile browser UI structures would allow viewport-specific interaction design, but they would split routes, components, state integration, tests, and long-term maintenance inside the same web application.

A single responsive Angular UI keeps one frontend codebase and one browser delivery channel. Its trade-off is that presenters and layouts must be designed deliberately for desktop and mobile browser contexts rather than optimized for only one viewport class.

## Decision

Use one responsive Angular browser UI for desktop and mobile browser contexts.

Keep responsive rendering concerns in presenter components where possible. Container components connect routing and NgRx state, while presenters own layout and interaction rendering for the view model they receive.

Do not introduce separate desktop/mobile browser UI structures for the planned product scope.

## Consequences

The frontend has one browser delivery channel and one component/test surface for desktop and mobile contexts.

Presenter components must be reviewed for responsive behavior, touch/mouse usability, and text/layout fit.

Mobile-browser-specific behavior is implemented inside the responsive browser UI unless a future requirement changes the planned delivery channel.
