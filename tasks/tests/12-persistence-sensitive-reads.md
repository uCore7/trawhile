# Test task tests/12 — Sensitive read rollout: reports, requests, tracking, MCP

## Role

You are a **test writer**. Derive the tests from the specs, the documented persistence direction, and this task brief. Do not read `src/main/java/`.

## Prerequisites

- `tasks/00-base-it.md` merged
- `tasks/tests/11-persistence-foundation.md` and `tasks/impl/11-persistence-foundation.md` merged

## Guardrails

- **Do not touch `src/main/`** — never create, edit, delete, or rename any file under `src/main/`.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, or `git branch -D`.
- **Repository and service-contract tests only** — focus on proving that the new read layer prevents broad unauthorized reads.

## Read (in order)

1. `docs/requirements-ur.md` — UR-F024, UR-F025, UR-F036, UR-F052, UR-F039, UR-F041, UR-F069
2. `docs/requirements-sr.md` — SR-F024.F01, SR-F025.F01, SR-F036.F01, SR-F052.F01, SR-F039.F01, SR-F041.F01, SR-F069.F01
3. `docs/schema.sql` — time records, requests, user profile, quick access, node authorizations
4. `docs/architecture.md` — persistence direction under data access
5. `src/test/java/com/trawhile/BaseIT.java`
6. `src/test/java/com/trawhile/TestFixtures.java`
7. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```text
src/test/java/com/trawhile/repository/read/
  RequestReadQueriesIT.java
  TrackingReadQueriesIT.java
  ReportReadQueriesIT.java
  McpReadQueriesIT.java
```

## Rules for every test method

- Use PostgreSQL/Testcontainers-backed integration tests.
- Annotate each test method with `@Tag("persistence-sensitive-reads")`.
- Prefer repository/query-level assertions over HTTP/controller assertions unless the task brief explicitly needs service semantics.

## Expected repository contracts

Assume the implementation task will introduce read queries shaped around caller scope, for example:

- `findVisibleRequests(UUID actingUserId, UUID nodeId)`
- `findOwnTrackingStatus(UUID actingUserId)`
- `findOwnTrackingHistory(UUID actingUserId, int limit, int offset)`
- `findOwnQuickAccess(UUID actingUserId)`
- `findOwnDetailedRecords(UUID actingUserId, ...)`
- `findVisibleMemberSummaries(UUID actingUserId, ...)`
- `findVisibleNodeTree(UUID actingUserId)`
- `findVisibleDailyTotalsForOtherUser(UUID actingUserId, UUID targetUserId, ...)`

## Key assertions

### `RequestReadQueriesIT`

- A caller sees requests only on nodes visible to them.
- Requests on invisible nodes are not returned.
- Node-subtree scoping is enforced in SQL, not by Java-side trimming.

### `TrackingReadQueriesIT`

- Tracking status and history are owner-only.
- Quick-access results include only the caller’s own list.
- Quick-access reads do not leak metadata for nodes the caller cannot see.

### `ReportReadQueriesIT`

- Detailed time-record reads are owner-only.
- Team/member report reads return aggregates only, never raw `time_records` rows for other users.
- Date range and subtree filters are applied without broad table scans leaking unauthorized rows into the result.
- The result set for member summaries contains only members visible within the caller’s authorized node scope.

### `McpReadQueriesIT`

- Visible node tree contains only nodes returned by the authorization scope.
- MCP detailed time-record access for the owner returns only the owner’s rows.
- MCP “other user” access returns aggregate totals only.

## Acceptance criteria

`./scripts/mvn-local.sh -Dtest=RequestReadQueriesIT,TrackingReadQueriesIT,ReportReadQueriesIT,McpReadQueriesIT test` passes after implementation.

## Watch out for

- The most important regression to block is broad reads followed by Java filtering.
- Reuse the same seeded tree shapes across tests where possible: root, authorized subtree, unauthorized sibling subtree.
- For report tests, assert both data shape and data absence. “Other user detail not visible” is as important as “aggregate visible”.
