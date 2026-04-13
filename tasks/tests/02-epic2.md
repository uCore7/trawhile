# Test task tests/02 — Epic 2: Node administration

## Role

You are a **test writer**. Derive all test logic from the spec. Do not read `src/main/java/`.

## Prerequisites

`tasks/00-base-it.md` merged — `BaseIT`, `TestFixtures`, and `TestSecurityHelper` must exist at `src/test/java/com/trawhile/` before this task begins.

## Guardrails

- **Do not touch `src/main/`** — never create, edit, delete, or rename any file under `src/main/`.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.

## Read (in order)

1. `docs/schema.sql` — `nodes`, `node_authorizations`; Q1–Q4 authorization CTEs
2. `docs/requirements-sr.md` — SR-F014.F01–SR-F023.F01
3. `docs/openapi.yaml` — `/nodes`, `/nodes/{nodeId}/authorizations` paths
4. `docs/test-plan.md` — TE-F014.F01-* through TE-F023.F01-*
5. `src/test/java/com/trawhile/BaseIT.java`
6. `src/test/java/com/trawhile/TestFixtures.java`
7. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```
src/test/java/com/trawhile/
  NodeIT.java              — TE-F014.F01-01, TE-F015.F01-01, TE-F016.F01-01, TE-F016.F01-02,
                             TE-F017.F01-01, TE-F018.F01-01, TE-F018.F01-02, TE-F019.F01-01,
                             TE-F020.F01-01, TE-F020.F01-02
  AuthorizationIT.java     — TE-F021.F01-01, TE-F022.F01-01, TE-F022.F01-02, TE-F023.F01-01
```

## Rules for every test method

Annotate with `@Tag("TE-Fxxx.Fxx-nn")`. Write real assertions. No empty bodies.

## Key assertions per TE

| TE | What to assert |
|---|---|
| TE-F014.F01-01 | `GET /api/v1/nodes/{id}` with `view` auth: response has node details and `children` array; 403 without auth |
| TE-F015.F01-01 | `POST /api/v1/nodes/{id}/children` as admin: `SELECT COUNT(*) FROM nodes WHERE parent_id = {id}` increases by 1; `sort_order` = previous max + 1; 403 for non-admin |
| TE-F016.F01-01 | `PATCH /api/v1/nodes/{id}` with new name: `SELECT name FROM nodes WHERE id = {id}` matches; 400 when logo payload exceeds 256 KB |
| TE-F016.F01-02 | `PUT /api/v1/nodes/{id}/logo` with valid PNG: `GET /api/v1/nodes/{id}/logo` returns 200 with `Content-Type: image/png`; 400 when `Content-Type: image/gif` |
| TE-F017.F01-01 | `PUT /api/v1/nodes/{id}/children/order` as admin: `SELECT sort_order FROM nodes WHERE parent_id = {id} ORDER BY id` matches submitted order; 403 for non-admin |
| TE-F018.F01-01 | `POST /api/v1/nodes/{id}/deactivate` when node has no active children: `is_active = false`, `deactivated_at IS NOT NULL`; 409 when active children exist |
| TE-F018.F01-02 | `POST /api/v1/nodes/{id}/deactivate` when node has an active `time_records` row: returns 2xx — active record does not block deactivation |
| TE-F019.F01-01 | `POST /api/v1/nodes/{id}/reactivate`: `is_active = true`, `deactivated_at IS NULL`; 403 for non-admin |
| TE-F020.F01-01 | `POST /api/v1/nodes/{id}/move` as admin of both node and destination: `SELECT parent_id FROM nodes WHERE id = {id}` equals destination; `sort_order` appended after existing siblings |
| TE-F020.F01-02 | Move where destination is own descendant: 409; move without admin on destination: 403 |
| TE-F021.F01-01 | `POST /api/v1/nodes/{id}/authorizations` granting `view` to a user: `node_authorizations` row exists with correct level; second call with `admin` updates existing row (upsert); 403 for non-admin |
| TE-F022.F01-01 | `DELETE /api/v1/nodes/{id}/authorizations/{userId}` as admin: row deleted; 409 when deleting the last `admin` row on the node |
| TE-F022.F01-02 | Same delete by non-admin: 403 |
| TE-F023.F01-01 | `GET /api/v1/nodes/{id}/authorizations` as admin: response distinguishes `direct` (same node) from `inherited` (ancestor node); 403 for non-admin |
