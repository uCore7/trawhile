# Test task tests/04 — Epic 4: Reporting & export

## Role

You are a **test writer**. Derive all test logic from the spec. Do not read `src/main/java/`.

## Prerequisites

`tasks/00-base-it.md` merged — `BaseIT`, `TestFixtures`, and `TestSecurityHelper` must exist at `src/test/java/com/trawhile/` before this task begins.

## Guardrails

- **Do not touch `src/main/`** — never create, edit, delete, or rename any file under `src/main/`.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.

## Read (in order)

1. `docs/schema.sql` — `time_entries`, `node_authorizations`
2. `docs/requirements-sr.md` — SR-F036.F01, SR-F036.F02, SR-F038.F01, SR-F052.F01
3. `docs/openapi.yaml` — `/reports`, `/reports/export`, `/reports/members` paths
4. `docs/test-plan.md` — TE-F036.F01-* through TE-F038.F01-*, TE-F052.F01-*
5. `src/test/java/com/trawhile/BaseIT.java`
6. `src/test/java/com/trawhile/TestFixtures.java`
7. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```
src/test/java/com/trawhile/
  ReportIT.java          — TE-F036.F01-01/02/03, TE-F036.F02-01/02, TE-F038.F01-01,
                           TE-F052.F01-01/02/03
  ReportServiceTest.java — TE-F036.F01-04 (unit test, no Spring context)
```

## Rules for every test method

Annotate with `@Tag("TE-Fxxx.Fxx-nn")`. Write real assertions. No empty bodies.

## Key assertions per TE

| TE | What to assert |
|---|---|
| TE-F036.F01-01 | `GET /api/v1/reports?mode=summary`: response has aggregated totals per node; no individual entry rows |
| TE-F036.F01-02 | `GET /api/v1/reports?mode=detailed`: response contains individual entry rows with `startedAt`, `endedAt`, `nodeId` |
| TE-F036.F01-03 | User A has `view` on node X but not node Y; both have entries; report returns only entries for node X |
| TE-F036.F01-04 | Unit test: given an entry with UTC `started_at`, the display timestamp in the response matches the company timezone offset (use a timezone with known UTC offset, e.g. `Europe/Berlin` in summer = UTC+2) |
| TE-F036.F02-01 | Two entries for the same user with overlapping time ranges; `GET /api/v1/reports?mode=detailed`: both have `overlap: true` |
| TE-F036.F02-02 | Two consecutive entries with a gap; detailed mode: gap flag set |
| TE-F038.F01-01 | `GET /api/v1/reports/export`: response `Content-Type` contains `text/csv`; `Content-Disposition` contains `attachment`; body is non-empty |
| TE-F052.F01-01 | `GET /api/v1/reports/members`: response has per-member per-bucket totals; no `started_at` or `ended_at` fields per entry |
| TE-F052.F01-02 | Insert overlapping entries for a member; `GET /api/v1/reports/members`: that member's bucket has `hasDataQualityIssues: true` |
| TE-F052.F01-03 | `GET /api/v1/reports/members`: JSON body must not contain any field that would expose individual entry detail (assert using `assertThat(body).doesNotContain("startedAt")` etc.) |
