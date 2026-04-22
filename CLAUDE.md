# trawhile — agent instructions

Before starting any work, read the following files in order:

1. `docs/schema.sql` — PostgreSQL schema, authorization queries
2. `docs/requirements-ur.md` — user requirements; stakeholders, system boundary, context boundary, key invariants
3. `docs/requirements-sr.md` — system requirements
4. `docs/glossary.md` — canonical definitions of all domain terms
5. `docs/architecture.md` — package layout, key technical decisions, Docker Compose, CI/CD
6. `spec/openapi.yaml` — REST API contract
7. `docs/process.md` — development phases, conventions, and current status
8. `docs/decisions.md` — rationale behind non-obvious architectural and process choices

These are the authoritative source of truth. Code, tests, and migrations must be consistent with them. If implementation forces a change to a requirement, update the relevant doc file in the same commit.

Run Maven through `./scripts/mvn-local.sh ...`, not bare `mvn` or `./mvnw`, so wrapper and repository caches stay inside the project.
For native app startup, run `make development-db` first, then `./scripts/mvn-local.sh spring-boot:run`. The wrapper auto-skips the frontend Maven plugin for `spring-boot:run`; Angular runs separately via `ng serve`.
If the agent sandbox blocks Docker or localhost DB sockets, request escalation for `make development-db` and/or `./scripts/mvn-local.sh spring-boot:run` instead of treating the startup failure as an application bug.

## Stack

- Backend: Spring Boot + Spring Data JDBC + Flyway + Spring Security (OAuth2)
- Database: PostgreSQL
- Frontend: Angular SPA + PrimeNG + Tailwind CSS
- Deployment: Docker Compose + Caddy (reverse proxy + TLS)
- Identity: Google + Apple Sign In + Microsoft Entra ID + Keycloak (OIDC)
- CI/CD: GitHub Actions

## Key design decisions

- One instance = one company. No multi-tenancy.
- No sessions table. Sessions managed by Spring HttpSession (implementation detail).
- No email stored for registered users (UR-C006). Email only in `pending_invitations`, deleted on first login match.
- Authorization is recursive: a grant on node N is effective on N and all descendants. Use recursive CTEs (Q1–Q4 in schema.sql) — do not flatten the tree.
- Freeze cutoff: entries with `started_at < NOW() - freeze_offset_years * INTERVAL '1 year'` are immutable. The offset is read from `TrawhileConfig.freezeOffsetYears`. No admin override.
- Anonymization: delete `user_profile` (cascades to personal tables); retain `users` stub plus any `time_records`/`requests` until purge removes them. Irreversible.
- Purge jobs are idempotent. On startup, any `purge_jobs` row with `status = 'active'` is resumed using stored `cutoff_date`.
- SSE is a general UX principle — all visible state is live across all sessions of a user.

## Security requirements (non-negotiable)

- SpotBugs + Find Security Bugs in CI (SQL injection detection)
- OWASP Dependency Check (Maven)
- npm audit (Angular)
- CycloneDX SBOM generation (Maven + npm)
- bucket4j rate limiting on all OAuth2 and API endpoints
- Secure HTTP headers on all responses (SR-C012.C01)
- CSRF protection on all mutating endpoints (SR-C013.C01)
- Build fails on high/critical vulnerabilities

## GDPR constraints

- Data minimization: collect only what is necessary
- No covert tracking: tracking is always user-initiated
- Right to erasure: anonymization available as self-service (SR-F047.F01)
- Security log retention: exactly 90 days (SR-C007.F01)
- Activity data retention: configurable, minimum 2 years (SR-F050.F01/SR-F050.F02)
- Any new data field must be evaluated against GDPR necessity before adding
