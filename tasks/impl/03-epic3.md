# Task impl/03 — Epic 3: Time tracking

## Prerequisites

- `tasks/00-base-it.md` merged
- `tasks/tests/03-epic3.md` merged (test classes exist and are failing)

## Guardrails

- **Do not touch `src/test/`** — never create, edit, delete, or rename any file under `src/test/`. If a test appears wrong, report it in your output and stop; do not fix the test.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.
- **No Flyway migrations** — never create or modify files under `src/main/resources/db/migration/`. Schema changes are applied separately in chat mode.
- **Only `src/main/java/`** — never create or modify files under `src/main/frontend/`, `src/main/resources/db/migration/`, or `src/test/`.

## Scope

Make the failing Epic 3 tests pass. Implement tracking start/switch/stop, recent record list with overlap/gap flags, quick-access management, and manual record CRUD.

## Read first (in order)

1. `docs/schema.sql` — `time_records`, `quick_access` tables
2. `docs/requirements-sr.md` — SR-F024.F01–SR-F034.F01; SR-F027.F01, SR-F030.F01
3. `spec/openapi.yaml` — `/tracking`, `/time-records`, `/quick-access` paths
4. `docs/architecture.md` — §Transaction boundaries, §SSE dispatch
5. `src/main/java/com/trawhile/config/TrawhileConfig.java` — `freezeOffsetYears()`
6. The failing tests:
   - `src/test/java/com/trawhile/TrackingIT.java`
   - `src/test/java/com/trawhile/QuickAccessIT.java`
   - `src/test/java/com/trawhile/TimeRecordIT.java`

## Modify (production code only)

| File | What to implement |
|---|---|
| `src/main/java/com/trawhile/service/TrackingService.java` | `getStatus()`, `getRecentEntries()`, `startTracking()`, `switchTracking()`, `stopTracking()` |
| `src/main/java/com/trawhile/service/TimeRecordService.java` | `createRetroactive()`, `editRecord()`, `deleteRecord()`, `duplicateRecord()` |
| `src/main/java/com/trawhile/web/TrackingController.java` | SR-F024.F01–SR-F029.F01 endpoints |
| `src/main/java/com/trawhile/web/QuickAccessController.java` | SR-F027.F01 (list), SR-F030.F01 (add/remove/reorder) endpoints |
| `src/main/java/com/trawhile/web/TimeRecordController.java` | SR-F031.F01–SR-F034.F01 endpoints |

## Acceptance criteria

`mvn test -Dtest=TrackingIT,QuickAccessIT,TimeRecordIT` passes. Do not modify test files.

## Watch out for

- **SR-F026.F01 leaf check**: `is_active = true` AND no children with `is_active = true` — one query, not two
- **SR-F028.F01 atomicity**: `switchTracking` is a single service method with one `@Transactional` boundary — not two separate calls from the controller
- **SR-F030.F01 max 9**: 10th add returns 409 with code `QUICK_ACCESS_FULL`
- **SR-F027.F01 non-trackable flag**: computed at read time — node is non-trackable when `is_active = false` OR has active children
- **SR-F032.F01/SR-F033.F01 freeze cutoff**: applies to `started_at`, not `ended_at`; sourced from `TrawhileConfig.freezeOffsetYears()`, not hardcoded
- **SR-F025.F01 overlap**: both records in an overlapping pair are flagged; overlap = `a.started_at < b.ended_at AND b.started_at < a.ended_at`
- **SSE**: dispatch `TRACKING` to all sessions of the user after every start/switch/stop
