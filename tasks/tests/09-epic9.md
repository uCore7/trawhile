# Test task tests/09 — Epic 9: MCP integration

## Role

You are a **test writer**. Derive all test logic from the spec. Do not read `src/main/java/`.

## Prerequisites

`tasks/00-base-it.md` merged — `BaseIT`, `TestFixtures`, and `TestSecurityHelper` must exist at `src/test/java/com/trawhile/` before this task begins.

## Guardrails

- **Do not touch `src/main/`** — never create, edit, delete, or rename any file under `src/main/`.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.

## Read (in order)

1. `docs/schema.sql` — `mcp_tokens` table (token_hash, revoked_at, expires_at, last_used_at)
2. `docs/requirements-sr.md` — SR-065, SR-065a, SR-066, SR-067, SR-068, SR-069, SR-070
3. `docs/openapi.yaml` — `/mcp/tokens`, `/mcp/tools` paths
4. `docs/test-plan.md` — TE-065-* through TE-070-*
5. `src/test/java/com/trawhile/BaseIT.java`
6. `src/test/java/com/trawhile/TestFixtures.java`
7. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```
src/test/java/com/trawhile/
  McpTokenIT.java    — TE-065-01, TE-065A-01, TE-066-01/02/03,
                       TE-067-01, TE-068-01, TE-069-01
  McpToolIT.java     — TE-070-01/02/03/04/05
```

## Rules for every test method

Annotate with `@Tag("TE-xxx-nn")`. Write real assertions. No empty bodies.

## Key assertions per TE

| TE | What to assert |
|---|---|
| TE-065-01 | `POST /api/v1/mcp/tokens`: response body contains the raw token; `SELECT token_hash FROM mcp_tokens WHERE user_id = {id}` equals `SHA-256(rawToken)` (hex-encoded); no raw token stored in any column; call again and verify the second call returns a different token |
| TE-065A-01 | `GET /api/v1/mcp/tokens`: insert one active and one revoked token for the same user; response list contains only the active token; revoked token absent |
| TE-066-01 | Authenticate an MCP API request using a valid Bearer token; request succeeds (2xx); `SELECT last_used_at FROM mcp_tokens WHERE id = {id}` is non-null and recent |
| TE-066-02 | Send an MCP request with a token string that does not match any `token_hash`; expect 401 |
| TE-066-03 | Insert a token row with `revoked_at IS NOT NULL`; use its raw value as Bearer; expect 401 |
| TE-067-01 | `DELETE /api/v1/mcp/tokens/{id}` for own token: `SELECT revoked_at FROM mcp_tokens WHERE id = {id}` is non-null; `SELECT COUNT(*) FROM security_events WHERE event_type = 'MCP_TOKEN_REVOKED'` increases by 1 |
| TE-068-01 | `GET /api/v1/admin/mcp/tokens` as root admin: response includes tokens from multiple users, each entry has owner `name`; 403 for non-admin |
| TE-069-01 | `DELETE /api/v1/admin/mcp/tokens/{id}` as root admin for another user's token: `revoked_at` set; `SELECT COUNT(*) FROM security_events WHERE event_type = 'MCP_TOKEN_REVOKED'` increases by 1; 403 for non-admin |
| TE-070-01 | MCP tool `getNodeTree` with a valid token: response contains only nodes visible to the token owner; `GET` with an expired token (`expires_at` in the past) returns 401 |
| TE-070-02 | MCP tool `getTimeEntries` for own user: response includes the user's own entries with full detail |
| TE-070-03 | MCP tool `getTimeEntries` querying another user's entries when the token owner has `view` auth on the node: response contains aggregated daily totals only; no individual `startedAt`/`endedAt` fields in the JSON body |
| TE-070-04 | MCP tool `getTrackingStatus`: response matches current tracking state (active entry or empty) |
| TE-070-05 | MCP tool `getMemberSummaries`: response has bucketed totals per member; no individual entry detail fields |
