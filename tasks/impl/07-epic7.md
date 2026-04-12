# Task impl/07 — Epic 7: Security & audit

## Prerequisites

- `tasks/00-base-it.md` merged
- `tasks/tests/07-epic7.md` merged (test classes exist and are failing)

**Complete this task early** — `SecurityEventService.log()` is called by impl/01, impl/06, impl/09, and impl/10. Its API must be defined and merged before those tasks can pass their tests.

## Guardrails

- **Do not touch `src/test/`** — never create, edit, delete, or rename any file under `src/test/`. If a test appears wrong, report it in your output and stop; do not fix the test.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.
- **No Flyway migrations** — never create or modify files under `src/main/resources/db/migration/`. Schema changes are applied separately in chat mode.
- **Only `src/main/java/`** — never create or modify files under `src/main/frontend/`, `src/main/resources/db/migration/`, or `src/test/`.

## Scope

Make the failing Epic 7 tests pass. Implement security event logging, the security event log endpoint, and the 90-day retention purge.

## Read first (in order)

1. `docs/schema.sql` — `security_events` table, `event_type` enum
2. `docs/requirements-sr.md` — SR-F049.F01, SR-F049.F02, SR-C007.F01
3. `docs/openapi.yaml` — `/security-events` path
4. The failing tests:
   - `src/test/java/com/trawhile/SecurityEventIT.java`

## Modify (production code only)

| File | What to implement |
|---|---|
| `src/main/java/com/trawhile/service/SecurityEventService.java` | `log(type, userId, metadata)`, `listEvents(filters)`, `deleteOldEvents()` |
| `src/main/java/com/trawhile/web/SecurityEventController.java` | `GET /security-events` |

## Acceptance criteria

`mvn test -Dtest=SecurityEventIT` passes. Do not modify test files.

## Watch out for

- **`log()` propagation**: use `@Transactional(propagation = REQUIRES_NEW)` so a rollback in the caller does not suppress the event
- **SR-C007.F01**: retention is exactly 90 days — `INTERVAL '90 days'` — not from `trawhileConfig`
- **SR-F049.F02**: requires effective `admin` on root specifically — `authorizationService.requireAdmin(userId, ROOT_NODE_ID)`
- **`log()` userId**: may be null for failed logins where no user is identified yet
