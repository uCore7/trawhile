# Task impl/05 — Epic 5: Requests

## Prerequisites

- `tasks/00-base-it.md` merged
- `tasks/tests/05-epic5.md` merged (test classes exist and are failing)

## Guardrails

- **Do not touch `src/test/`** — never create, edit, delete, or rename any file under `src/test/`. If a test appears wrong, report it in your output and stop; do not fix the test.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.
- **No Flyway migrations** — never create or modify files under `src/main/resources/db/migration/`. Schema changes are applied separately in chat mode.
- **Only `src/main/java/`** — never create or modify files under `src/main/frontend/`, `src/main/resources/db/migration/`, or `src/test/`.

## Scope

Make the failing Epic 5 tests pass. Implement request submission, listing, and closure.

## Read first (in order)

1. `docs/schema.sql` — `requests` table
2. `docs/requirements-sr.md` — SR-040, SR-041, SR-042
3. `docs/openapi.yaml` — `/requests` paths
4. The failing tests:
   - `src/test/java/com/trawhile/RequestIT.java`

## Modify (production code only)

| File | What to implement |
|---|---|
| `src/main/java/com/trawhile/service/RequestService.java` | `submitRequest()`, `listRequests()`, `closeRequest()` |
| `src/main/java/com/trawhile/web/RequestController.java` | `POST /requests`, `GET /requests`, `POST /requests/{id}/close` |

## Acceptance criteria

`mvn test -Dtest=RequestIT` passes. Do not modify test files.

## Watch out for

- **SR-042 idempotency**: re-closing returns 409 with code `REQUEST_ALREADY_CLOSED`
- **SR-041 visibility**: at least `view` via recursive CTE — not a direct lookup
- **SR-042 admin**: effective `admin` on the node or any ancestor — Q3 CTE
- **SSE**: dispatch `REQUEST` event to all users with effective `admin` on the node and ancestors after submit and close
- **requester_id nullable**: handle NULL in list responses (anonymous placeholder)
