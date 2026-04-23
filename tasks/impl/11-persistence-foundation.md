# Task impl/11 — Persistence foundation: `visible_nodes()` + jOOQ + node read queries

## Prerequisites

- `tasks/00-base-it.md` merged
- `tasks/tests/11-persistence-foundation.md` merged (tests exist and are failing)
- A separate chat-mode schema change has already added the PostgreSQL function `visible_nodes(user_id)` to the canonical schema / runtime database setup

## Guardrails

- **Do not touch `src/test/`** — never create, edit, delete, or rename any file under `src/test/`. If a test appears wrong, report it and stop.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, or `git branch -D`.
- **No Flyway migrations** — never create or modify files under `src/main/resources/db/migration/`. Schema and function DDL are applied separately in chat mode.
- **No frontend files** — do not create or modify files under `src/main/frontend/`.
- **Sandbox note for jOOQ setup** — this task likely introduces new Maven/plugin dependencies and a code-generation step. If `./scripts/mvn-local.sh ...` fails because the sandbox blocks dependency download, plugin resolution, Docker, or DB sockets, request escalation for the exact Maven command instead of treating the failure as an application bug.

## Scope

Lay the technical foundation for the new persistence model:

- integrate jOOQ with code generation into the Maven build
- introduce the initial `repository/authz/` and `repository/read/` packages
- add the first jOOQ-backed node read query layer built on `visible_nodes(user_id)`
- migrate `NodeService` read paths to the new query layer

Do **not** migrate node mutations yet. This task is read-side foundation only.

## Read first (in order)

1. `docs/architecture.md` — persistence direction under data access
2. `docs/decisions.md` — persistence-layer decision and authorization model
3. `docs/schema.sql` — nodes, node authorizations, Q1–Q4
4. `pom.xml`
5. `src/main/java/com/trawhile/service/NodeService.java`
6. `src/main/java/com/trawhile/service/AuthorizationService.java`
7. `src/main/java/com/trawhile/repository/AuthorizationQueries.java`
8. The failing tests:
   - `src/test/java/com/trawhile/repository/authz/AuthorizationFunctionIT.java`
   - `src/test/java/com/trawhile/repository/read/NodeReadQueriesIT.java`

## Create / modify

| File | What to do |
|---|---|
| `pom.xml` | Add jOOQ runtime/codegen integration for PostgreSQL. Use generated sources in the build; do not commit generated classes under `target/`. |
| `src/main/java/com/trawhile/config/` | Add jOOQ configuration as needed (`DSLContext`, converter bindings, etc.) without breaking existing JDBC usage. |
| `src/main/java/com/trawhile/repository/authz/AuthorizationFunctions.java` | Thin wrapper(s) over the shared DB authorization functions where useful. |
| `src/main/java/com/trawhile/repository/read/NodeReadQueries.java` | Define the node read contract. |
| `src/main/java/com/trawhile/repository/read/JooqNodeReadQueries.java` | Implement the contract with jOOQ, joining `visible_nodes(...)` directly in SQL. |
| `src/main/java/com/trawhile/service/NodeService.java` | Replace generic repository-based read paths (`getRootNode`, `getNode`, `getLogo`) with the new query layer. Keep write paths unchanged in this task. |

## Expected read model split

Use a lightweight summary/content split:

- `NodeSummaryRow` for regular node metadata
- `NodeContentRow` for heavier payloads such as logo bytes + MIME type

Prefer repository methods shaped like:

- `findVisibleRootNodeSummary(UUID actingUserId)`
- `findVisibleNodeSummary(UUID actingUserId, UUID nodeId)`
- `findVisibleChildren(UUID actingUserId, UUID parentId)`
- `findVisibleNodeContent(UUID actingUserId, UUID nodeId)`

## Acceptance criteria

`./scripts/mvn-local.sh -Dtest=AuthorizationFunctionIT,NodeReadQueriesIT,NodeIT test` passes.

## Watch out for

- The goal is **authorized SQL, not SQL then filter**. The new node reads must not call `findAll()` and trim in Java afterward.
- `visible_nodes(user_id)` is the shared primitive; do not duplicate its recursive logic inline unless a test proves it is unavoidable.
- Keep generated jOOQ artifacts build-generated. Do not add hand-maintained copies of generated schema classes to source control.
- Preserve existing endpoint behavior where possible; this task is a persistence refactor, not an API redesign.
- If jOOQ code generation needs a live PostgreSQL instance, start the dev DB via the documented path and request escalation if Docker or local DB access is sandbox-blocked.
