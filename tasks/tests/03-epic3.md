# Test task tests/03 — Epic 3: Time tracking

## Role

You are a **test writer**. Derive all test logic from the spec. Do not read `src/main/java/`.

## Prerequisites

`tasks/00-base-it.md` merged — `BaseIT`, `TestFixtures`, and `TestSecurityHelper` must exist at `src/test/java/com/trawhile/` before this task begins.

## Guardrails

- **Do not touch `src/main/`** — never create, edit, delete, or rename any file under `src/main/`.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.

## Read (in order)

1. `docs/schema.sql` — `time_entries`, `quick_access`; freeze cutoff definition
2. `docs/requirements-sr.md` — SR-026–SR-035
3. `docs/openapi.yaml` — `/tracking`, `/time-entries`, `/quick-access` paths
4. `docs/test-plan.md` — TE-026-* through TE-035-*
5. `src/test/java/com/trawhile/BaseIT.java`
6. `src/test/java/com/trawhile/TestFixtures.java`
7. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```
src/test/java/com/trawhile/
  TrackingIT.java     — TE-026-01/02, TE-027-01/02, TE-028-01/02/03,
                        TE-029-01, TE-030-01
  QuickAccessIT.java  — TE-031-01/02/03/04
  TimeEntryIT.java    — TE-032-01/02/03, TE-033-01/02, TE-034-01, TE-035-01
```

## Rules for every test method

Annotate with `@Tag("TE-xxx-nn")`. Write real assertions. No empty bodies.

## Key assertions per TE

| TE | What to assert |
|---|---|
| TE-026-01 | `GET /api/v1/tracking` while tracking: response has `nodeId`, `nodePath`, `startedAt`, `elapsedSeconds > 0` |
| TE-026-02 | `GET /api/v1/tracking` when not tracking: response indicates empty/no active entry |
| TE-027-01 | Insert two overlapping `time_entries`; `GET /api/v1/time-entries` (recent): both entries have `overlap: true` |
| TE-027-02 | Insert two consecutive entries with a gap; `GET /api/v1/time-entries` (recent): gap flag set on the pair |
| TE-028-01 | `POST /api/v1/tracking` on active leaf node with `track` auth: `SELECT COUNT(*) FROM time_entries WHERE ended_at IS NULL AND user_id = {id}` = 1; 409 on inactive node |
| TE-028-02 | `POST /api/v1/tracking` on node with active children: 409 |
| TE-028-03 | `POST /api/v1/tracking` without `track` auth: 403 |
| TE-029-01 | `POST /api/v1/tracking` while already tracking a different node: old entry has `ended_at IS NOT NULL`; new entry has `ended_at IS NULL`; only 1 open entry total |
| TE-030-01 | `DELETE /api/v1/tracking` while tracking: entry gets `ended_at IS NOT NULL`; `GET /api/v1/tracking` returns empty state; 400 when not tracking |
| TE-031-01 | `POST /api/v1/quick-access` adds a node: count increases; 9th entry accepted; 10th entry: 409 |
| TE-031-02 | `DELETE /api/v1/quick-access/{nodeId}`: count decreases |
| TE-031-03 | `PUT /api/v1/quick-access/order`: `SELECT node_id FROM quick_access WHERE user_id = {id} ORDER BY sort_order` matches submitted order |
| TE-031-04 | `GET /api/v1/quick-access` when a listed node is deactivated: that entry has `nonTrackable: true`; entry is still in the list |
| TE-032-01 | `POST /api/v1/time-entries` with valid `started_at < ended_at`: row inserted with correct `timezone`; 400 when `started_at >= ended_at` |
| TE-032-02 | Create retroactive entry on a node without `track` auth: 403 |
| TE-032-03 | Create retroactive entry on inactive node: 409 |
| TE-033-01 | `PATCH /api/v1/time-entries/{id}` within freeze cutoff: DB row updated; 409 when `started_at` is before `NOW() - freeze_offset_years YEARS` |
| TE-033-02 | Edit with `started_at >= ended_at`: 400 |
| TE-034-01 | `DELETE /api/v1/time-entries/{id}` not frozen: row deleted; 409 when frozen |
| TE-035-01 | `POST /api/v1/time-entries/{id}/duplicate` with new times: new row has same `description` as original, new `started_at`/`ended_at`; 400 when new `started_at >= ended_at` |
