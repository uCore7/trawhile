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
2. `docs/requirements-sr.md` — SR-F053.F01, SR-F054.F01, SR-F053.F02, SR-F055.F01, SR-F056.F01, SR-F057.F01, SR-F069.F01
3. `spec/openapi.yaml` — `/mcp/tokens`, `/mcp/tools` paths
4. `spec/test-plan.md` — TE-F053.F01-* through TE-F069.F01-*
5. `src/test/java/com/trawhile/BaseIT.java`
6. `src/test/java/com/trawhile/TestFixtures.java`
7. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```
src/test/java/com/trawhile/
  McpTokenIT.java    — TE-F053.F01-01, TE-F054.F01-01, TE-F053.F02-01/02/03,
                       TE-F055.F01-01, TE-F056.F01-01, TE-F057.F01-01
  McpToolIT.java     — TE-F069.F01-01/02/03/04/05
```

## Rules for every test method

Annotate with `@Tag("TE-Fxxx.Fxx-nn")`. Write real assertions. No empty bodies.

## Key assertions per TE

| TE | What to assert |
|---|---|
| TE-F053.F01-01 | `POST /api/v1/mcp/tokens`: response body contains the raw token; `SELECT token_hash FROM mcp_tokens WHERE user_id = {id}` equals `SHA-256(rawToken)` (hex-encoded); no raw token stored in any column; call again and verify the second call returns a different token |
| TE-F054.F01-01 | `GET /api/v1/mcp/tokens`: insert one active and one revoked token for the same user; response list contains only the active token; revoked token absent |
| TE-F053.F02-01 | Authenticate an MCP API request using a valid Bearer token; request succeeds (2xx); `SELECT last_used_at FROM mcp_tokens WHERE id = {id}` is non-null and recent |
| TE-F053.F02-02 | Send an MCP request with a token string that does not match any `token_hash`; expect 401 |
| TE-F053.F02-03 | Insert a token row with `revoked_at IS NOT NULL`; use its raw value as Bearer; expect 401 |
| TE-F055.F01-01 | `DELETE /api/v1/mcp/tokens/{id}` for own token: `SELECT revoked_at FROM mcp_tokens WHERE id = {id}` is non-null; `SELECT COUNT(*) FROM security_events WHERE event_type = 'MCP_TOKEN_REVOKED'` increases by 1 |
| TE-F056.F01-01 | `GET /api/v1/admin/mcp/tokens` as root admin: response includes tokens from multiple users, each entry has owner `name`; 403 for non-admin |
| TE-F057.F01-01 | `DELETE /api/v1/admin/mcp/tokens/{id}` as root admin for another user's token: `revoked_at` set; `SELECT COUNT(*) FROM security_events WHERE event_type = 'MCP_TOKEN_REVOKED'` increases by 1; 403 for non-admin |
| TE-F069.F01-01 | MCP tool `getNodeTree` with a valid token: response contains only nodes visible to the token owner; `GET` with an expired token (`expires_at` in the past) returns 401 |
| TE-F069.F01-02 | MCP tool `getTimeRecords` for own user: response includes the user's own records with full detail |
| TE-F069.F01-03 | MCP tool `getTimeRecords` querying another user's records when the token owner has `view` auth on the node: response contains aggregated daily totals only; no individual `startedAt`/`endedAt` fields in the JSON body |
| TE-F069.F01-04 | MCP tool `getTrackingStatus`: response matches current tracking state (active record or empty) |
| TE-F069.F01-05 | MCP tool `getMemberSummaries`: response has bucketed totals per member; no individual record detail fields |
