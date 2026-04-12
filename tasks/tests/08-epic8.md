# Test task tests/08 — Epic 8: Data lifecycle

## Role

You are a **test writer**. Derive all test logic from the spec. Do not read `src/main/java/`.

## Prerequisites

`tasks/00-base-it.md` merged — `BaseIT`, `TestFixtures`, and `TestSecurityHelper` must exist at `src/test/java/com/trawhile/` before this task begins.

## Guardrails

- **Do not touch `src/main/`** — never create, edit, delete, or rename any file under `src/main/`.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.

## Read (in order)

1. `docs/schema.sql` — `purge_jobs` table, `time_entries`, `requests`, `nodes`; purge job status enum
2. `docs/requirements-sr.md` — SR-F050.F01, SR-F050.F02, SR-F050.F03, SR-F050.F04
3. `docs/openapi.yaml` — `/purge-jobs` path
4. `docs/test-plan.md` — TE-F050.F01-* through TE-F050.F04-*
5. `src/test/java/com/trawhile/BaseIT.java`
6. `src/test/java/com/trawhile/TestFixtures.java`
7. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```
src/test/java/com/trawhile/
  ActivityPurgeJobIT.java    — TE-F050.F01-01, TE-F050.F02-01, TE-F050.F02-02
  NodePurgeJobIT.java        — TE-F050.F03-01, TE-F050.F04-01, TE-F050.F04-02
```

## Rules for every test method

Annotate with `@Tag("TE-Fxxx.Fxx-nn")`. Write real assertions. No empty bodies.

## Key assertions per TE

| TE | What to assert |
|---|---|
| TE-F050.F01-01 | Trigger `ActivityPurgeJobService` (inject and call directly); `SELECT status, cutoff_date FROM purge_jobs WHERE job_type = 'activity'` shows `status = 'active'` and `cutoff_date` is `NOW() - retentionYears YEARS` (within a 1-second tolerance) |
| TE-F050.F02-01 | Insert a `time_entries` row with `started_at = NOW() - retentionYears YEARS - INTERVAL '1 day'` (before cutoff) and one with `started_at = NOW() - retentionYears YEARS + INTERVAL '1 day'` (after cutoff); also insert a `requests` row before the cutoff; run the activity purge job; before-cutoff rows deleted; after-cutoff row retained |
| TE-F050.F02-02 | Insert a `purge_jobs` row with `status = 'active'` and a specific `cutoff_date`; inject `ActivityPurgeJobService` and invoke the resume/restart path; verify the same `cutoff_date` is used (no new cutoff calculated); rows before that stored cutoff deleted; rows after retained |
| TE-F050.F03-01 | After `ActivityPurgeJobIT` has completed, trigger `NodePurgeJobService`; `SELECT status, cutoff_date FROM purge_jobs WHERE job_type = 'node'` shows `status = 'active'` and `cutoff_date` is the combined cutoff (activity cutoff minus `nodeRetentionExtraYears`) |
| TE-F050.F04-01 | Create a deactivated leaf node with `deactivated_at` before the node purge cutoff and no remaining `time_entries` or `requests`; run the node purge job; node deleted. Also create a deactivated leaf node with a remaining `time_entries` row; node retained after the job |
| TE-F050.F04-02 | Insert a `purge_jobs` row with `job_type = 'node'`, `status = 'active'`, and a specific `cutoff_date`; invoke the resume path; verify the same stored `cutoff_date` is used; verify idempotency by running the job twice with no state change on the second run |
