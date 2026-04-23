# Test task tests/11 — Persistence foundation: `visible_nodes()` + jOOQ + node read queries

## Role

You are a **test writer**. Derive all test logic from the canonical docs and this task brief. Do not read `src/main/java/`.

## Prerequisites

- `tasks/00-base-it.md` merged
- The architecture direction in `docs/architecture.md` and `docs/decisions.md` is the source of truth for this refactor
- A separate chat-mode schema change has added the PostgreSQL authorization function `visible_nodes(user_id)` to `docs/schema.sql`
- The Flyway V1 migration has been regenerated from the canonical schema
- If a jOOQ code-generation schema input file still exists, it has been regenerated from the canonical schema and is not treated as a second handwritten schema
- Do not attempt to create or patch migrations in this task

## Guardrails

- **Do not touch `src/main/`** — never create, edit, delete, or rename any file under `src/main/`.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, or `git branch -D`.
- **Repository-layer tests only** — focus on PostgreSQL-backed contract tests for the new authorization function and the new node read query layer. Do not test jOOQ internals directly.

## Read (in order)

1. `docs/schema.sql` — node tree, node authorizations, root node semantics, Q1–Q4
2. `docs/requirements-ur.md` — UR-F014, UR-F016, UR-F021, key invariants on authorization inheritance
3. `docs/requirements-sr.md` — SR-F014.F01, SR-F016.F01, SR-F021.F01
4. `docs/architecture.md` — persistence direction under data access
5. `docs/decisions.md` — authorization model and persistence-layer decision
6. `src/test/java/com/trawhile/BaseIT.java`
7. `src/test/java/com/trawhile/TestFixtures.java`
8. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```text
src/test/java/com/trawhile/repository/authz/
  AuthorizationFunctionIT.java

src/test/java/com/trawhile/repository/read/
  NodeReadQueriesIT.java
```

## Rules for every test method

- Use PostgreSQL/Testcontainers-backed integration tests.
- Annotate each test method with `@Tag("persistence-foundation")`.
- Seed data explicitly; do not depend on unrelated fixtures beyond `BaseIT` helpers.
- Assert both positive access and non-disclosure of unauthorized rows.

## Expected repository contract

The implementation task will introduce:

- PostgreSQL function `visible_nodes(user_id)` as the foundational authorization primitive
- `NodeReadQueries` with methods equivalent to:
  - `findVisibleRootNodeSummary(UUID actingUserId)`
  - `findVisibleNodeSummary(UUID actingUserId, UUID nodeId)`
  - `findVisibleChildren(UUID actingUserId, UUID parentId)`
  - `findVisibleNodeContent(UUID actingUserId, UUID nodeId)`

Treat those names and semantics as the contract the production task must satisfy.

## Key assertions

### `AuthorizationFunctionIT`

- A direct authorization on a node makes that node visible.
- An authorization on an ancestor makes descendants visible.
- A user with no authorization sees no nodes.
- Visibility results contain no duplicates when multiple ancestor grants overlap.
- Root visibility follows the same recursive rule; if the root is granted, descendants are visible.

### `NodeReadQueriesIT`

- `findVisibleRootNodeSummary()` returns the root node only when the caller can see it.
- `findVisibleNodeSummary()` returns the requested node when visible and returns empty when not visible.
- `findVisibleChildren()` returns only direct children that are visible to the caller, ordered by `sort_order`.
- `findVisibleNodeContent()` returns logo payload and MIME type only when the node is visible and a logo exists.
- `findVisibleNodeContent()` returns empty both when the node is invisible and when the node is visible but has no content payload.
- The read layer does not require Java-side post-filtering to hide unauthorized children or logo payloads; the returned result set is already scoped.

## Acceptance criteria

`./scripts/mvn-local.sh -Dtest=AuthorizationFunctionIT,NodeReadQueriesIT test` passes after the implementation task is complete.

## Watch out for

- `visible_nodes(user_id)` is a set-returning SQL function intended for `SELECT` composition, not a procedure.
- Unauthorized access in this layer should usually surface as an empty result set, not as rows returned and filtered later in the test.
- Do not derive expected behavior from a hand-maintained `jooq-schema.sql` subset. The canonical runtime contract is `docs/schema.sql`.
- Keep the tests resilient to future DTO shaping: assert repository semantics, not web-controller JSON.
