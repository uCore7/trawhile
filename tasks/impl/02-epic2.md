# Task impl/02 — Epic 2: Node administration

## Prerequisites

- `tasks/00-base-it.md` merged
- `tasks/tests/02-epic2.md` merged (test classes exist and are failing)

## Guardrails

- **Do not touch `src/test/`** — never create, edit, delete, or rename any file under `src/test/`. If a test appears wrong, report it in your output and stop; do not fix the test.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.
- **No Flyway migrations** — never create or modify files under `src/main/resources/db/migration/`. Schema changes are applied separately in chat mode.
- **Only `src/main/java/`** — never create or modify files under `src/main/frontend/`, `src/main/resources/db/migration/`, or `src/test/`.

## Scope

Make the failing Epic 2 tests pass. Implement node CRUD, logo management, deactivation/reactivation, move, and authorization grant/revoke/view.

## Read first (in order)

1. `docs/schema.sql` — `nodes`, `node_authorizations`; Q1–Q4 authorization CTEs
2. `docs/requirements-sr.md` — SR-F014.F01–SR-F023.F01
3. `docs/openapi.yaml` — `/nodes` and `/nodes/{nodeId}/authorizations` paths
4. `docs/architecture.md` — §1 Data access (recursive CTEs), §5 Authorization checks
5. The failing tests:
   - `src/test/java/com/trawhile/NodeIT.java`
   - `src/test/java/com/trawhile/AuthorizationIT.java`

## Modify (production code only)

| File | What to implement |
|---|---|
| `src/main/java/com/trawhile/service/NodeService.java` | All node operations: `getNode`, `createChild`, `updateNode`, `uploadLogo`, `getLogo`, `deleteLogo`, `reorderChildren`, `deactivateNode`, `reactivateNode`, `moveNode`, `grantAuthorization`, `revokeAuthorization`, `listAuthorizations` |
| `src/main/java/com/trawhile/web/NodeController.java` | All endpoints: SR-F014.F01–SR-F020.F01 |
| `src/main/java/com/trawhile/web/AuthorizationController.java` | SR-F021.F01, SR-F022.F01, SR-F023.F01 |

## Acceptance criteria

`mvn test -Dtest=NodeIT,AuthorizationIT` passes. Do not modify test files.

## Watch out for

- **Authorization is recursive**: use Q3 via `AuthorizationService` — not a direct `node_authorizations` lookup
- **SR-F018.F01**: active time record on the node itself does NOT block deactivation
- **SR-F020.F01 move**: three guards — admin on node, admin on destination, destination not in own subtree; check (c) via recursive CTE
- **SR-F021.F01 upsert**: `INSERT ... ON CONFLICT (user_id, node_id) DO UPDATE SET authorization = EXCLUDED.authorization`
- **SR-F022.F01 last-admin**: 409 with code `LAST_ADMIN` when deleting the last `admin` row on a node
- **Logo MIME**: validate `Content-Type` of the multipart part — not the file extension; allowed: `image/png`, `image/jpeg`, `image/svg+xml`, `image/webp`
- **SSE**: dispatch `NODE_CHANGE` after create, update, reorder, deactivate, reactivate, move — to all users with at least `view` on the affected node
- **SR-F023.F01 direct vs. inherited**: determined in the query, not post-processed in Java
