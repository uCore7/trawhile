# Task impl/04 — Epic 4: Reporting & export

## Prerequisites

- `tasks/00-base-it.md` merged
- `tasks/tests/04-epic4.md` merged (test classes exist and are failing)

## Guardrails

- **Do not touch `src/test/`** — never create, edit, delete, or rename any file under `src/test/`. If a test appears wrong, report it in your output and stop; do not fix the test.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.
- **No Flyway migrations** — never create or modify files under `src/main/resources/db/migration/`. Schema changes are applied separately in chat mode.
- **Only `src/main/java/`** — never create or modify files under `src/main/frontend/`, `src/main/resources/db/migration/`, or `src/test/`.

## Scope

Make the failing Epic 4 tests pass. Implement time reports (summary and detailed), overlap/gap annotation, CSV export, and per-member summaries.

## Read first (in order)

1. `docs/schema.sql` — `time_entries`, `node_authorizations`; Q1 visible-subtree CTE
2. `docs/requirements-sr.md` — SR-F036.F01, SR-F036.F02, SR-F038.F01, SR-F052.F01
3. `docs/openapi.yaml` — `/reports`, `/reports/export`, `/reports/members` paths
4. `src/main/java/com/trawhile/config/TrawhileConfig.java` — `timezone()`
5. The failing tests:
   - `src/test/java/com/trawhile/ReportIT.java`
   - `src/test/java/com/trawhile/ReportServiceTest.java`

## Modify (production code only)

| File | What to implement |
|---|---|
| `src/main/java/com/trawhile/service/ReportService.java` | `getReport(filters, mode)` SR-F036.F01/F02; `getMemberSummaries(filters)` SR-F052.F01 |
| `src/main/java/com/trawhile/service/ReportExportService.java` | `exportCsv(filters)` SR-F038.F01 |
| `src/main/java/com/trawhile/web/ReportController.java` | `GET /reports`, `GET /reports/export`, `GET /reports/members` |

## Acceptance criteria

`mvn test -Dtest=ReportIT,ReportServiceTest` passes. Do not modify test files.

## Watch out for

- **Visibility is recursive**: use Q1 from `docs/schema.sql` for the visible-subtree filter
- **SR-F036.F01 node filter**: when supplied, include all visible nodes in that subtree
- **SR-F052.F01 granularity**: individual `started_at`/`ended_at` per entry must never appear in member summary responses
- **SR-F052.F01 bucketing**: use company timezone from `TrawhileConfig.timezone()` for interval boundaries
- **CSV**: prefer `StreamingResponseBody`; set headers before writing
- **Timezone conversion**: all timestamps stored UTC; convert to company timezone for display
