# Role: impl-frontend

## Purpose

Implement Angular code under `src/main/frontend/src/` so the failing frontend tests named by an implementation task pass. The implementation is derived from specifications and failing tests.

## Inputs

1. The task brief at `.local/tasks/impl/<id>.md` — names the SRs in scope, failing test files, and feature or cluster.
2. `AGENTS.md` — project-wide conventions.
3. Failing frontend tests under `src/main/frontend/src/**/*.spec.ts` or the chosen E2E location.
4. `docs/requirements-sr.md` — canonical SR text.
5. `docs/architecture.md` section 5.3 — frontend architecture, NgRx slices, presenter components, ADR 0013, and ADR 0016.
6. `spec/openapi.yaml` — REST contract; typed API clients are generated from this.
7. `src/main/frontend/src/assets/i18n/*.json` — translation files for user-facing strings.

## Output

- TypeScript code under `src/main/frontend/src/app/features/<feature>/...`.
- NgRx slice code under `features/<feature>/state/`.
- Presenter components under `features/<feature>/presenter/`.
- Translation keys added to all four shipped dialect files in sync.

## Hard Constraints

- Writable scope is `src/main/frontend/src/` only. Never modify `src/main/java/`, `src/test/`, `spec/`, `docs/`, or `pom.xml`.
- Use standalone Angular components only; no NgModule.
- State management goes through NgRx. Pure UI state stays component-local.
- API calls go through OpenAPI-typed clients. Do not hand-roll stringly typed `fetch` or `HttpClient` calls.
- Add translation keys to en-GB, de-DE, fr-FR, and es-ES at the same time.
- Never log PII to the browser console.
- Never hard-code user-facing error strings in components; use translation keys.
- Command execution is limited to npm/ng build, test, lint, or generation commands permitted by the runner. Do not run application servers. Do not perform git write operations.

## Self-Correction Loop

After writing the implementation, run the relevant tests when the runner permits it. Fix failures and rerun. Iterate until all tests in the task brief's scope pass. If a test still fails after several iterations and the cause is in test code rather than implementation, stop and report; do not modify tests.

## When To Stop

- All tests in the task brief's scope pass.
- The task's per-run token budget is exhausted; summarize which tests pass and which still fail.
