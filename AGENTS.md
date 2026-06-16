# Agent instructions — trawhile

Tool-agnostic entry point for agents. `docs/process.md` owns the full development process; this file is the standing system-prompt extension loaded into every agent run. Read it before any task brief.

## Project at a Glance

trawhile is a small-company self-hosted time-tracking system.

- **Backend:** Spring Boot 4.x, jOOQ, PostgreSQL, Redis (sessions only).
- **Frontend:** Angular 21.x, NgRx, PrimeNG (with Chart.js), Tailwind 4.x, ngx-translate. PDF export via `jsPDF` + `jsPDF-AutoTable`.
- **Architecture:** hexagonal / ports-and-adapters (ADR 0001), three service clusters — Work, Identity and access, Administration (architecture §5.2.2).
- **Authentication:** OIDC only; no passwords. API keys for delegated programmatic access (UR-08).
- **Deployment:** Docker Compose on a single VPS; no orchestration platform (UR-00-C12).
- **Live updates:** SSE for browser, webhook outbox for API consumers (ADR 0017, ADR 0018).

## Start Here

- Start with the assigned task file under `.local/tasks/`.
- The task file defines the role, scope, prerequisites, and concrete documents to read.
- Read the assigned task file first, then `docs/process.md`, then the upstream canonical documents named by the task.
- If there is no assigned task file, read only the canonical documents that are upstream of the work being requested. Do not use downstream artifacts as sources for upstream work.
- Keep changes scoped to the assigned task.

## Upstream Discipline

- Requirements-engineering work uses problem-space sources only, such as `docs/glossary.md` and `docs/requirements-ur.md`.
- Architecture work may use requirements and glossary sources; ADR work also uses `docs/architecture.md` and `docs/adr/`.
- System-requirements work may use requirements, architecture, and ADRs.
- Technical-specification work may use requirements, architecture, ADRs, and system requirements before writing spec artifacts.
- Test, implementation, and cleanup work follow the read list in the assigned task file.
- If a task, requirement, architecture document, ADR, or implementation detail conflicts, stop and report the conflict.

## File-Location Quick Map

| Concern | Path |
|---|---|
| User requirements (UR) | `docs/requirements-ur.md` |
| System requirements (SR) | `docs/requirements-sr.md` |
| Architecture (arc42) | `docs/architecture.md` |
| Architecture Decision Records | `docs/adr/*.md` |
| Glossary | `docs/glossary.md` |
| Development process | `docs/process.md` |
| Database schema (authoritative) | `spec/schema.sql` |
| REST API contract (authoritative) | `spec/openapi.yaml` |
| Test plan (UR → SR → TE traceability) | `spec/test-plan.md` |
| Backend inbound adapters (REST controllers, SSE, etc.) | `src/main/java/.../adapter/inbound/` |
| Backend outbound adapters (persistence, metrics, event) | `src/main/java/.../adapter/outbound/` |
| Backend services (business logic, by cluster) | `src/main/java/.../service/{work,identity,administration}/` |
| Backend port interfaces | `src/main/java/.../port/{inbound,outbound}/` |
| Backend tests | `src/test/java/...` |
| Frontend Angular app | `src/app/` |
| Frontend translations (en-GB, de-DE, fr-FR, es-ES) | `src/assets/i18n/*.json` |
| Operator tooling (Prometheus, Grafana, AlertManager) | `deploy/monitoring/` |
| Docker Compose deployment artifacts | `deploy/` |
| Agent task briefs | `.local/tasks/{tests,impl,cleanup}/` |
| Traceability checker | `scripts/check-traceability.py` |

## Core Conventions

- **Time.** All timestamps in Java are `java.time.Instant` (UTC). Never `LocalDateTime`. Storage and API wire format are UTC (UR-00-C10); user-local time-window semantics are the frontend's responsibility — it converts and supplies an IANA TZ in report requests (ADR 0019).

  The database clock is the single canonical source for "now" in production code. Reconciling this with the hexagonal mandate that business invariants live in services: **services name the invariant in domain vocabulary at the port surface; the adapter implements it using the DB clock.** Method names like `findNonExpired`, `existsActive`, `purgeExpired`, `existsRecentlyCreated` belong on the port; the adapter expresses them as `WHERE expires_at > NOW()`, `WHERE created_at < NOW() - INTERVAL …`, etc. Columns that record "now" on write (`invited_at`, `expires_at`, `granted_at`, `created_at`, …) use the database's `NOW()` in the SQL statement; the schema's `DEFAULT NOW()` expressions are the canonical pattern.

  Corollary: **do not cross a port boundary with a clock**. Ports take no `Instant` parameter that means "now" (parameters that mean a specific user-supplied instant — a report's `from` / `to` — are fine). Do NOT introduce a `Clock` port; the clock stays implicit in the adapter.

  DB-enforced integrity constraints (FK, UNIQUE, partial indexes) are *integrity* invariants and cooperate with — they do not replace — service-owned business invariants. Both layers may express the same invariant from different angles; this is intentional defence-in-depth, not duplication.

  Production-code uses of `Instant.now()` are restricted to framework-owned timestamps (Logback, Micrometer) and response payloads that literally surface "server current time" (e.g. health checks). Tests are unconstrained — observing the system from outside via `Instant.now()` brackets is correct.
- **Audit events.** Emitted to the application log stream, never to the database (architecture §8.5). Vocabulary in SR-06-F01.F01.
- **Authorization.** Service methods carry `@Transactional` and call `AuthorizationService.check(...)` explicitly. Never `@PreAuthorize`; never inline authz in adapters. The recursive grant rule lives in PostgreSQL functions (architecture §8.2).
- **Persistence.** jOOQ-backed outbound persistence adapter only. Never JPA, never `JdbcTemplate`. The schema is `spec/schema.sql`; Flyway migrations and jOOQ types are generated from it.
- **REST contracts.** `spec/openapi.yaml` is the source of truth. Backend controllers implement OpenAPI-generated server stubs; never roll a controller signature by hand.
- **Localisation.** Four shipped dialects (en-GB default, de-DE, fr-FR, es-ES) per UR-00-C18. Backend emits no localised user-facing text — it returns stable error codes via the OpenAPI `Problem` shape; the frontend renders the translation.
- **Logging.** Loki + Promtail via the `log-pipeline` service (ADR 0018). No PII in logs per UR-00-C14 — redact at emit. Correlation identifiers per UR-00-C16.
- **Live updates.** SSE for browser sessions; outbound webhook + PostgreSQL outbox for API consumers (ADR 0017). Payload shape is hybrid: snapshot for state-shaped events, command for action-shaped.
- **Configuration.** Externalised under the `trawhile:` namespace; no database settings table.

Test-code conventions live in `docs/agent-roles/test-writer.md`.

## Anti-Patterns / Footguns

- **Don't use `java.time.LocalDateTime`.** Use `Instant`.
- **Don't write authorization checks inline in adapters or controllers.** They go through `AuthorizationService` from the service layer.
- **Don't log raw API keys, OAuth secrets, emails, or other PII.** Redact at emission per SR-00-C14.F01. The audit log carries pseudonymous identifiers; the System Admin resolves them via UR-06-F05.
- **Don't modify Flyway migrations directly.** Schema starts in `spec/schema.sql`; migrations are generated.
- **Don't bypass the OpenAPI-generated server stub** when adding a controller. The stub is the contract.
- **Don't store passwords or session tokens in the database.** OIDC-only authentication; sessions live in Redis only.
- **Don't return user-facing error strings from the backend.** Return locale-neutral error codes via `Problem`; the frontend translates.
- **Don't expose individual `time_records` rows in reports.** Reports are aggregations only; per-record detail is the tracking history surface (UR-03-F02 / UR-04-F01 / UR-04-F04).
- **Don't delete an Anonymised Account Stub** (`users.anonymised_at IS NOT NULL`). The stub persists for the deployment lifetime as the FK target for retained historical records (SR-07-F01.C01).
- **Don't mutate `api_keys` columns other than `revoked_at` and `last_used_at`.** Rotate by revoke-and-reissue per SR-08-F03.C02. The persistence port deliberately exposes no update method.
- **Don't allow a user to CRUD their own node-authorization grants** (key invariant). Self-anonymisation is the only path to removing one's own rights without a peer admin.
- **Don't add a new outbound network connection** beyond OIDC token-exchange and user-configured webhook delivery without updating SR-05-F06.F01 `outboundConnections`. The application makes no advisory check; advisory awareness is operator-driven via GitHub subscription (UR-06-F03).

## Execution Guardrails

- Use `./scripts/mvn-local.sh ...`, not bare `mvn` or `./mvnw`.
- For native app startup, start PostgreSQL first with `make development-db`, then run `./scripts/mvn-local.sh spring-boot:run`.
- If the sandbox blocks Docker, local DB sockets, Redis, or required test containers, request escalation for the exact command instead of treating the failure as an application bug.
- Do not run git write operations: `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or commands that modify git state or communicate with a remote.
- Test agents under `.local/tasks/tests/` must not modify `src/main/`.
- Implementation and cleanup agents under `.local/tasks/impl/` or `.local/tasks/cleanup/` must not modify `src/test/`.
- Verifier agents must not modify any code; their output is a structured critique.
- Implementation and cleanup agents must not modify frontend files unless explicitly assigned a frontend task.
- Do not create or modify Flyway migrations under `src/main/resources/db/migration/`; schema changes start in chat mode from `spec/schema.sql`.

## Implementation Guardrails

- Put `@Transactional` on service methods only.
- External-actor service methods check authorization explicitly through `AuthorizationService`.
- Do not use `@PreAuthorize`.
- Do not add JPA.
- New persistence work follows the jOOQ-backed outbound persistence adapter structure from the architecture and ADRs.
- Controllers implement OpenAPI-generated server-stub interfaces; do not hand-write request/response types that duplicate generated DTOs.
- Frontend state in NgRx slices; pure-UI state stays component-local (ADR 0013). Presenter components own layout; effects own backend calls (ADR 0016).

## Pointers to Canonical Documents

- For "what should the system do?" → `docs/requirements-ur.md` (user requirements) and `docs/requirements-sr.md` (system requirements).
- For "how is the system structured?" → `docs/architecture.md`.
- For "why was X decided this way?" → `docs/adr/*.md`.
- For "what's the database shape?" → `spec/schema.sql`.
- For "what's the REST API surface?" → `spec/openapi.yaml`.
- For "which tests cover this requirement?" → `spec/test-plan.md`.
- For "how does this term work?" → `docs/glossary.md`.
- For "what process phase am I in?" → `docs/process.md`.
