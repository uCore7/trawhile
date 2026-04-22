# Agent instructions — trawhile

This file is the tool-agnostic entry point. If you are Claude Code, read `CLAUDE.md` instead (it is a superset of this file).

## Authoritative documents — read before writing any code

| Document | Purpose |
|---|---|
| `docs/schema.sql` | PostgreSQL schema, FK constraints, authorization queries Q1–Q4 |
| `docs/requirements-ur.md` | Stakeholder capabilities, role model, key invariants |
| `docs/requirements-sr.md` | System behaviours, acceptance conditions |
| `docs/architecture.md` | Package layout, patterns, code examples |
| `spec/openapi.yaml` | REST API contract — request/response shapes |
| `spec/test-plan.md` | Traceability matrix UR → SR → TE-xxx-nn |
| `docs/process.md` | Development phases, conventions, current status |
| `docs/decisions.md` | Rationale behind non-obvious architectural and process choices |

## Task files

Each file in `tasks/` is a self-contained brief for one agent. Start from the file that matches your assigned task. Always complete `tasks/00-base-it.md` before any other task.

## Non-negotiable constraints

- `@Transactional` on service methods only — never on controllers or repositories
- Authorization checked via `AuthorizationService` at the top of each service method — no `@PreAuthorize`
- No JPA — Spring Data JDBC only; recursive CTEs via `AuthorizationQueries`
- SSE dispatch after every state mutation via `SseDispatcher`
- No email stored for registered users (C-2) — enforced by SR-002 / TE-002-01
- Freeze cutoff = `NOW() - trawhileConfig.freezeOffsetYears() * INTERVAL '1 year'` — no hardcoding
- Build fails on SpotBugs HIGH/CRITICAL and OWASP HIGH/CRITICAL

## Guardrails

These rules apply to every agent task, regardless of type:

- **Use the repo Maven wrapper script** — run Maven commands via `./scripts/mvn-local.sh ...`, not bare `mvn` or `./mvnw`, so wrapper and repository caches stay inside the project.
- **Native app startup path** — start PostgreSQL first (`make development-db`), then run `./scripts/mvn-local.sh spring-boot:run`. The wrapper auto-skips the frontend Maven plugin for `spring-boot:run`; Angular runs separately via `ng serve` in native dev.
- **Sandbox note for live app runs** — if the agent sandbox blocks Docker or localhost DB sockets, request escalation for `make development-db` and/or `./scripts/mvn-local.sh spring-boot:run` rather than treating startup failure as an application bug.
- **Sandbox note for database-backed tests** — if the agent sandbox blocks Docker or DB sockets, request escalation for `./scripts/mvn-local.sh test`. This applies to any test that needs PostgreSQL, regardless of whether the team labels it UT, IT, or otherwise.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or communicates with a remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are fine.
- **Test agents** (`tasks/tests/`): do not create or modify any file under `src/main/`.
- **Impl agents** (`tasks/impl/`): do not create or modify any file under `src/test/`. If a test appears wrong, report it and stop — do not fix it.
- **No Flyway migrations** — never create or modify files under `src/main/resources/db/migration/`. All schema changes are applied in chat mode, not by agents.
- **No frontend files** — impl agents must not create or modify any file under `src/main/frontend/`.
