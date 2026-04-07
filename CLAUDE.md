# Time tracker — agent instructions

Before starting any work, read the following files in order:

1. `docs/schema.sql` — PostgreSQL schema (13 tables), authorization queries
2. `docs/epics.md` — feature list, role model, key invariants
3. `docs/requirements-sr.md` — 62 system requirements (SR-001–SR-062)
4. `docs/requirements-ur.md` — 51 user requirements (UR-001–UR-051)

These are the authoritative source of truth. Code, tests, and migrations must be consistent
with them. If implementation forces a change to a requirement, update the relevant doc file
in the same commit.

## Stack

- Backend: Spring Boot + Spring Data JDBC + Flyway + Spring Security (OAuth2)
- Database: PostgreSQL
- Frontend: Angular SPA + PrimeNG + Tailwind CSS
- Deployment: Docker Compose + Caddy (reverse proxy + TLS)
- Identity: GitHub OAuth2 + Google OAuth2
- CI/CD: GitHub Actions

## Key design decisions

- One instance = one company. No multi-tenancy.
- No sessions table. Sessions managed by Spring HttpSession (implementation detail).
- No email stored for registered users (C-2). Email only in `pending_memberships`, deleted on
  first login match.
- Authorization is recursive: a grant on node N is effective on N and all descendants.
  Use recursive CTEs (Q1–Q4 in schema.sql) — do not flatten the tree.
- Freeze date: entries with `started_at < freeze_date` are immutable. No admin override.
- Anonymization: delete `user_profile` (cascades to personal tables); retain `users` stub and
  all `time_entries`. Irreversible.
- Purge jobs are idempotent. On startup, any `purge_jobs` row with `status = 'active'` is
  resumed using stored `cutoff_date`.
- SSE is a general UX principle — all visible state is live across all sessions of a user.

## Security requirements (non-negotiable)

- SpotBugs + Find Security Bugs in CI (SQL injection detection)
- OWASP Dependency Check (Maven)
- npm audit (Angular)
- CycloneDX SBOM generation (Maven + npm)
- bucket4j rate limiting on all OAuth2 and API endpoints
- Secure HTTP headers on all responses (see SR-060)
- CSRF protection on all mutating endpoints (SR-061)
- Build fails on high/critical vulnerabilities

## GDPR constraints

- Data minimization: collect only what is necessary
- No covert tracking: tracking is always user-initiated
- Right to erasure: anonymization available as self-service (SR-047)
- Security log retention: exactly 90 days (SR-051)
- Activity data retention: configurable, minimum 2 years (SR-052–053)
- Any new data field must be evaluated against GDPR necessity before adding
