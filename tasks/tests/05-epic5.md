# Test task tests/05 — Epic 5: Requests

## Role

You are a **test writer**. Derive all test logic from the spec. Do not read `src/main/java/`.

## Prerequisites

`tasks/00-base-it.md` merged — `BaseIT`, `TestFixtures`, and `TestSecurityHelper` must exist at `src/test/java/com/trawhile/` before this task begins.

## Guardrails

- **Do not touch `src/main/`** — never create, edit, delete, or rename any file under `src/main/`.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.

## Read (in order)

1. `docs/schema.sql` — `requests` table
2. `docs/requirements-sr.md` — SR-F039.F01, SR-F041.F01, SR-F042.F01
3. `spec/openapi.yaml` — `/requests` paths
4. `spec/test-plan.md` — TE-F039.F01-* through TE-F042.F01-*
5. `src/test/java/com/trawhile/BaseIT.java`
6. `src/test/java/com/trawhile/TestFixtures.java`
7. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```
src/test/java/com/trawhile/
  RequestIT.java    — TE-F039.F01-01, TE-F041.F01-01, TE-F042.F01-01, TE-F042.F01-02
```

## Rules for every test method

Annotate with `@Tag("TE-Fxxx.Fxx-nn")`. Write real assertions. No empty bodies.

## Key assertions per TE

| TE | What to assert |
|---|---|
| TE-F039.F01-01 | `POST /api/v1/requests` by user with `view` on the node: `SELECT COUNT(*) FROM requests WHERE node_id = {id}` increases by 1; 403 without `view` |
| TE-F041.F01-01 | `GET /api/v1/requests?nodeId={id}` by user with `view`: response includes both open and closed requests; 403 without `view` |
| TE-F042.F01-01 | `POST /api/v1/requests/{id}/close` by node admin: `SELECT status, resolved_at, resolved_by FROM requests WHERE id = {id}` has `status = 'closed'`, non-null `resolved_at`, correct `resolved_by`; 403 for non-admin |
| TE-F042.F01-02 | Close an already-closed request: 409 |
