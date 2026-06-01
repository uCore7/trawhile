# 0010. Handle OIDC login and registration flows

## Status

- Accepted, 2026-05-15

## Context

This decision answers: given that authentication is constrained to OAuth2/OIDC providers by UR-C002, where is the OIDC callback interpreted and how is it routed into the application's login, registration, bootstrap, rejection, and provider-linking flows?

### Callback flow options

OIDC callbacks share provider protocol handling, but they can lead to different application flows: normal login, first-admin bootstrap, invited-user registration, rejected login, or provider linking for an already authenticated user. These flows have different business rules, persistence effects, temporary state, and redirects.

One option is to let the frontend or separate controllers interpret callback outcomes after Spring Security has authenticated the provider identity. That would spread callback semantics across web routes and require duplicated handling for provider identity, temporary registration data, and error redirects.

Another option is to treat the Spring Security OIDC callback handling as the single server-side classification point. The OIDC user service and authentication success handling can resolve the provider identity, classify the flow, store only temporary flow state in the session where needed, and redirect to the next application step.

## Decision

Keep OIDC callback interpretation server-side and centralized in Spring Security OIDC handling.

Use the OIDC user service and authentication success handling to classify the callback into the required application flow.

Use session state only for transient flow intent or registration data that must survive redirects before the flow is completed.

Keep durable user creation and flow-specific persistence effects in the application flow that follows callback classification.

## Consequences

The OIDC callback remains server-owned and has one architectural owner.

Provider protocol handling and provider identity extraction are not duplicated across controllers or frontend routes.

Provider linking can reuse Spring Security's OAuth2 redirect and callback machinery.

Bootstrap and invitation registration can carry temporary state through the GDPR notice step before durable user creation.

Session attributes used for flow control must be cleared after use.
