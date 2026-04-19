# Test task tests/07 — Epic 7: Security & audit

## Role

You are a **test writer**. Derive all test logic from the spec. Do not read `src/main/java/`.

## Prerequisites

`tasks/00-base-it.md` merged — `BaseIT`, `TestFixtures`, and `TestSecurityHelper` must exist at `src/test/java/com/trawhile/` before this task begins.

## Guardrails

- **Do not touch `src/main/`** — never create, edit, delete, or rename any file under `src/main/`.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.

## Read (in order)

1. `docs/schema.sql` — `security_events` table, `event_type` enum values
2. `docs/requirements-sr.md` — SR-F049.F01, SR-F049.F02, SR-C007.F01
3. `spec/openapi.yaml` — `/security-events` path
4. `spec/test-plan.md` — TE-F049.F01-* through TE-F049.F02-*, TE-C007.F01-*
5. `src/test/java/com/trawhile/BaseIT.java`
6. `src/test/java/com/trawhile/TestFixtures.java`
7. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```
src/test/java/com/trawhile/
  SecurityEventIT.java    — TE-F049.F01-01/02/03/04, TE-F049.F02-01, TE-C007.F01-01
```

## Rules for every test method

Annotate with `@Tag("TE-Fxxx.Fxx-nn")`. Write real assertions. No empty bodies.

## Key assertions per TE

| TE | What to assert |
|---|---|
| TE-F049.F01-01 | Trigger a successful login flow; `SELECT COUNT(*) FROM security_events WHERE event_type = 'LOGIN_SUCCESS'` increases by 1 |
| TE-F049.F01-02 | Perform a grant authorization operation; `SELECT COUNT(*) FROM security_events WHERE event_type = 'AUTH_GRANT'` increases by 1 |
| TE-F049.F01-03 | Generate an MCP token; `SELECT COUNT(*) FROM security_events WHERE event_type = 'MCP_TOKEN_GENERATED'` increases by 1 |
| TE-F049.F01-04 | Revoke an MCP token; `SELECT COUNT(*) FROM security_events WHERE event_type = 'MCP_TOKEN_REVOKED'` increases by 1 |
| TE-F049.F02-01 | `GET /api/v1/security-events` as root admin with filter params: response is a list; filtering by `event_type` returns only matching rows; 403 for non-admin |
| TE-C007.F01-01 | Insert a `security_events` row with `occurred_at = NOW() - INTERVAL '91 days'` and one with `occurred_at = NOW() - INTERVAL '89 days'`; run the cleanup method; 91-day row deleted; 89-day row retained |
