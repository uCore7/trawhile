# Test task tests/03 — Epic 3: Time tracking

## Role

You are a **test writer**. Derive all test logic from the spec. Do not read `src/main/java/`.

## Prerequisites

`tasks/00-base-it.md` merged — `BaseIT`, `TestFixtures`, and `TestSecurityHelper` must exist at `src/test/java/com/trawhile/` before this task begins.

## Guardrails

- **Do not touch `src/main/`** — never create, edit, delete, or rename any file under `src/main/`.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.

## Read (in order)

1. `docs/schema.sql` — `time_records`, `quick_access`; freeze cutoff definition
2. `docs/requirements-sr.md` — SR-F024.F01–SR-F034.F01; SR-F027.F01, SR-F030.F01
3. `docs/openapi.yaml` — `/tracking`, `/time-records`, `/quick-access` paths
4. `docs/test-plan.md` — TE-F024.F01-* through TE-F034.F01-*, TE-F027.F01-*, TE-F030.F01-*
5. `src/test/java/com/trawhile/BaseIT.java`
6. `src/test/java/com/trawhile/TestFixtures.java`
7. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```
src/test/java/com/trawhile/
  TrackingIT.java     — TE-F024.F01-01/02, TE-F025.F01-01/02, TE-F026.F01-01/02/03,
                        TE-F028.F01-01, TE-F029.F01-01
  QuickAccessIT.java  — TE-F027.F01-01,
                        TE-F030.F01-01/02/03
  TimeRecordIT.java   — TE-F031.F01-01/02/03, TE-F032.F01-01/02/03/04/05, TE-F033.F01-01, TE-F034.F01-01
```

## Rules for every test method

Annotate with `@Tag("TE-Fxxx.Fxx-nn")`. Write real assertions. No empty bodies.

## Key assertions per TE

| TE | What to assert |
|---|---|
| TE-F024.F01-01 | `GET /api/v1/tracking` while tracking: response has `nodeId`, `nodePath`, `startedAt`, `elapsedSeconds > 0` |
| TE-F024.F01-02 | `GET /api/v1/tracking` when not tracking: response indicates empty/no active record |
| TE-F025.F01-01 | Insert two overlapping `time_records`; `GET /api/v1/tracking/history` (recent): both records have `overlap: true` |
| TE-F025.F01-02 | Insert two consecutive records with a gap; `GET /api/v1/tracking/history` (recent): gap flag set on the pair |
| TE-F026.F01-01 | `POST /api/v1/tracking/start` on active leaf node with `track` auth: `SELECT COUNT(*) FROM time_records WHERE ended_at IS NULL AND user_id = {id}` = 1; 409 on inactive node |
| TE-F026.F01-02 | `POST /api/v1/tracking/start` on node with active children: 409 |
| TE-F026.F01-03 | `POST /api/v1/tracking/start` without `track` auth: 403 |
| TE-F028.F01-01 | `POST /api/v1/tracking/start` while already tracking a different node: old record has `ended_at IS NOT NULL`; new record has `ended_at IS NULL`; only 1 open record total |
| TE-F029.F01-01 | `POST /api/v1/tracking/stop` while tracking: record gets `ended_at IS NOT NULL`; `GET /api/v1/tracking` returns empty state; 400 when not tracking |
| TE-F027.F01-01 | `GET /api/v1/quick-access` when a listed node is deactivated: that entry has `nonTrackable: true`; entry is still in the list |
| TE-F030.F01-01 | `POST /api/v1/quick-access` adds a node: count increases; 9th entry accepted; 10th entry: 409 |
| TE-F030.F01-02 | `DELETE /api/v1/quick-access/{nodeId}`: count decreases |
| TE-F030.F01-03 | `PUT /api/v1/quick-access/order`: `SELECT node_id FROM quick_access WHERE user_id = {id} ORDER BY sort_order` matches submitted order |
| TE-F031.F01-01 | `POST /api/v1/time-records` with valid `started_at < ended_at`: row inserted with correct `timezone`; 400 when `started_at >= ended_at` |
| TE-F031.F01-02 | Create retroactive record on a node without `track` auth: 403 |
| TE-F031.F01-03 | Create retroactive record on inactive node: 409 |
| TE-F032.F01-01 | `PUT /api/v1/time-records/{id}` within freeze cutoff: DB row updated; 409 when `started_at` is before `NOW() - freeze_offset_years YEARS` |
| TE-F032.F01-02 | Edit with `started_at >= ended_at`: 400 |
| TE-F032.F01-03 | Reassign a record to an inactive node: 409 |
| TE-F032.F01-04 | Reassign a record to a node with active children: 409 |
| TE-F032.F01-05 | Reassign a record to a node without `track` authorization: 403 |
| TE-F033.F01-01 | `DELETE /api/v1/time-records/{id}` not frozen: row deleted; 409 when frozen |
| TE-F034.F01-01 | `POST /api/v1/time-records/{id}/duplicate` with new times: new row has same `description` as original, new `started_at`/`ended_at`; 400 when new `started_at >= ended_at` |
