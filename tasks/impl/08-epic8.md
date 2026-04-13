# Task impl/08 — Epic 8: Data lifecycle

## Prerequisites

- `tasks/00-base-it.md` merged
- `tasks/tests/08-epic8.md` merged (test classes exist and are failing)

## Guardrails

- **Do not touch `src/test/`** — never create, edit, delete, or rename any file under `src/test/`. If a test appears wrong, report it in your output and stop; do not fix the test.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.
- **No Flyway migrations** — never create or modify files under `src/main/resources/db/migration/`. Schema changes are applied separately in chat mode.
- **Only `src/main/java/`** — never create or modify files under `src/main/frontend/`, `src/main/resources/db/migration/`, or `src/test/`.

## Scope

Make the failing Epic 8 tests pass. Implement the activity purge job on the configured purge schedule and the node deletion job.

## Read first (in order)

1. `docs/schema.sql` — `purge_jobs`, `time_records`, `requests`, `nodes`
2. `docs/requirements-sr.md` — SR-F050.F01, SR-F050.F02, SR-F050.F03, SR-F050.F04
3. `docs/architecture.md` — §3 Purge jobs (chunked transactions, startup resume)
4. `src/main/java/com/trawhile/lifecycle/ActivityPurgeJob.java`
5. `src/main/java/com/trawhile/lifecycle/NodeDeletionJob.java`
6. `src/main/java/com/trawhile/lifecycle/PurgeJobCoordinator.java`
7. The failing tests:
   - `src/test/java/com/trawhile/ActivityPurgeJobIT.java`
   - `src/test/java/com/trawhile/NodePurgeJobIT.java`

## Modify (production code only)

| File | What to implement |
|---|---|
| `src/main/java/com/trawhile/lifecycle/ActivityPurgeJob.java` | `runNightly()` SR-F050.F01; `resume()` SR-F050.F02 — chunked delete with `REQUIRES_NEW` per batch |
| `src/main/java/com/trawhile/lifecycle/NodeDeletionJob.java` | `runAfterActivityPurge()` SR-F050.F03; `resume()` SR-F050.F04 — bottom-up iterative delete |
| `src/main/java/com/trawhile/lifecycle/PurgeJobCoordinator.java` | `run()` — resume active jobs on startup |

## Acceptance criteria

`mvn test -Dtest=ActivityPurgeJobIT,NodePurgeJobIT` passes. Do not modify test files.

## Watch out for

- **Chunked transaction**: outer loop is NOT `@Transactional`; each batch calls a `REQUIRES_NEW` method
- **Cutoff stored, not recomputed**: read `cutoff_date` from `purge_jobs` row on resume — never recompute
- **SR-F050.F04 bottom-up**: only delete current leaf nodes; loop until none qualify; do NOT try to process the whole tree in one query
- **SR-F050.F04 subtree checks**: enforce that no `time_records` or `requests` remain anywhere in the candidate subtree before deletion
- **Prometheus metrics**: update `trawhile_purge_job_last_completed_seconds`, increment `trawhile_purge_job_deleted_total` and (on exception) `trawhile_purge_job_failures_total`
- **Security event**: log one `security_events` row per completed job run via `SecurityEventService.log()`
