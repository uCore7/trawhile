# Task impl/09 — Epic 9: MCP integration

## Prerequisites

- `tasks/00-base-it.md` merged
- `tasks/impl/07-epic7.md` merged (`SecurityEventService.log()` exists)
- `tasks/tests/09-epic9.md` merged (test classes exist and are failing)

## Guardrails

- **Do not touch `src/test/`** — never create, edit, delete, or rename any file under `src/test/`. If a test appears wrong, report it in your output and stop; do not fix the test.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.
- **No Flyway migrations** — never create or modify files under `src/main/resources/db/migration/`. Schema changes are applied separately in chat mode.
- **Only `src/main/java/`** — never create or modify files under `src/main/frontend/`, `src/main/resources/db/migration/`, or `src/test/`.

## Scope

Make the failing Epic 9 tests pass. Implement MCP token lifecycle and the MCP tool server.

## Read first (in order)

1. `docs/schema.sql` — `mcp_tokens`
2. `docs/requirements-sr.md` — SR-F053.F01, SR-F054.F01, SR-F053.F02, SR-F055.F01, SR-F056.F01, SR-F057.F01, SR-F069.F01
3. `spec/openapi.yaml` — `/account/mcp-tokens`, `/admin/mcp-tokens`, `/mcp` paths
4. The failing tests:
   - `src/test/java/com/trawhile/McpTokenIT.java`
   - `src/test/java/com/trawhile/McpToolIT.java`

## Modify / create (production code only)

| File | What to implement |
|---|---|
| `src/main/java/com/trawhile/service/McpTokenService.java` | `generateToken()`, `listOwnTokens()`, `revokeOwn()`, `listAllTokens()`, `adminRevoke()`, `authenticate()` |
| `src/main/java/com/trawhile/web/AccountController.java` | MCP token endpoints |
| `src/main/java/com/trawhile/web/McpTokenController.java` | Admin MCP token endpoints |
| `src/main/java/com/trawhile/web/McpServerController.java` _(create)_ | `POST /mcp` — JSON-RPC dispatcher |

## Acceptance criteria

`./scripts/mvn-local.sh -Dtest=McpTokenIT,McpToolIT test` passes. Do not modify test files.

## Watch out for

- **Raw token never stored**: store only `SHA-256(rawToken)` as hex; raw token returned once in response only
- **SR-F053.F02 authenticate()**: compute hash, query by `token_hash`, check `revoked_at IS NULL` and `expires_at` — return 401 without distinguishing which check failed; update `last_used_at` on success
- **SR-F056.F01 admin**: requires effective `admin` on root — `authorizationService.requireAdmin(userId, ROOT_NODE_ID)`
- **SR-F069.F01 cross-user**: if `user_id ≠ token owner`, must have at least `view` on relevant nodes AND return aggregated daily totals only — reuse `ReportService.getMemberSummaries()` semantics
- **Security events**: log `MCP_TOKEN_GENERATED`, `MCP_TOKEN_REVOKED`, `MCP_TOOL_INVOKED` (one per call)
- **Bearer extraction**: `Authorization: Bearer <token>` header; 401 if absent or malformed
