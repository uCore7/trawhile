# Task impl/12 — Sensitive read rollout: reports, requests, tracking, MCP

## Prerequisites

- `tasks/00-base-it.md` merged
- `tasks/impl/11-persistence-foundation.md` merged
- `tasks/tests/12-persistence-sensitive-reads.md` merged (tests exist and are failing)

## Guardrails

- **Do not touch `src/test/`** — never create, edit, delete, or rename any file under `src/test/`. If a test appears wrong, report it and stop.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, or `git branch -D`.
- **No Flyway migrations** — never create or modify files under `src/main/resources/db/migration/`.
- **No frontend files** — do not create or modify files under `src/main/frontend/`.

## Scope

Migrate the highest-risk sensitive read paths to the new jOOQ-based, authorization-shaped repository layer:

- requests
- tracking / quick access
- reports
- MCP read paths

Eliminate broad repository reads followed by Java-side filtering in these paths.

## Read first (in order)

1. `docs/architecture.md` — persistence direction
2. `docs/decisions.md` — persistence-layer decision
3. `src/main/java/com/trawhile/service/RequestService.java`
4. `src/main/java/com/trawhile/service/TrackingService.java`
5. `src/main/java/com/trawhile/service/ReportService.java`
6. `src/main/java/com/trawhile/web/McpServerController.java`
7. The failing tests:
   - `src/test/java/com/trawhile/repository/read/RequestReadQueriesIT.java`
   - `src/test/java/com/trawhile/repository/read/TrackingReadQueriesIT.java`
   - `src/test/java/com/trawhile/repository/read/ReportReadQueriesIT.java`
   - `src/test/java/com/trawhile/repository/read/McpReadQueriesIT.java`

## Create / modify

| Area | What to do |
|---|---|
| `src/main/java/com/trawhile/repository/read/RequestReadQueries*.java` | Introduce jOOQ-backed request reads built on authorization scope. |
| `src/main/java/com/trawhile/repository/read/TrackingReadQueries*.java` | Introduce owner-only tracking/history/quick-access reads. |
| `src/main/java/com/trawhile/repository/read/ReportReadQueries*.java` | Split owner-detailed reads from visible aggregate/member-summary reads. |
| `src/main/java/com/trawhile/repository/read/McpReadQueries*.java` | Introduce node-tree and MCP read queries with the same authorization semantics as REST. |
| `src/main/java/com/trawhile/service/RequestService.java` | Consume the new request read queries. |
| `src/main/java/com/trawhile/service/TrackingService.java` | Consume the new tracking read queries. |
| `src/main/java/com/trawhile/service/ReportService.java` | Consume the new report read queries and remove broad `findAll()` flows. |
| `src/main/java/com/trawhile/web/McpServerController.java` | Stop querying repositories directly for sensitive business reads; route through the new query layer or services that use it. |

## Design requirements

- Detailed `time_records` access must remain owner-only unless the requirement explicitly says otherwise.
- Team-visible reporting must use aggregate queries, never detail rows for other users.
- Node-scoped reads must encode `actingUserId` in the query contract.
- Avoid generic methods like `findAll()` or `findByNodeId(...)` for the migrated sensitive reads.

## Acceptance criteria

`./scripts/mvn-local.sh -Dtest=RequestReadQueriesIT,TrackingReadQueriesIT,ReportReadQueriesIT,McpReadQueriesIT,RequestIT,TrackingIT,ReportIT,McpToolIT test` passes.

## Watch out for

- `ReportService` is the highest-risk file: do not preserve “load broad data then filter” behavior there.
- `McpServerController` currently bypasses service boundaries for sensitive reads; bring it back under the authorization-shaped query model.
- Preserve the requirement distinction between owner detail and aggregate-only team visibility.
