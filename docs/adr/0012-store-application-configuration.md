# 0012. Store application configuration

## Status

- Accepted, 2026-05-15

## Context

This decision answers: where are deployment-specific application settings stored and validated?

System settings such as retention periods, purge schedule, timezone, and OIDC provider availability are deployment configuration. They are needed at startup and should be validated before the application begins serving traffic.

A database settings table would add administrative UI, runtime mutation semantics, persistence migrations, validation rules, and audit requirements.

## Decision

Store system configuration in `application.yml` and environment variables.

Bind configuration through `TrawhileConfig` using Spring `@ConfigurationProperties`.

Validate required configuration at startup.

Do not add a database settings table.

## Consequences

Deployment configuration is explicit and versionable outside the application database.

Invalid configuration fails at startup.

Changing system settings is an operational action, not an in-app mutation.

Runtime settings changes require restart or redeploy unless a future ADR changes this decision.
