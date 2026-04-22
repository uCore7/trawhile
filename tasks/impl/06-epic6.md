# Task impl/06 — Epic 6: Account

## Prerequisites

- `tasks/00-base-it.md` merged
- `tasks/impl/01-epic1.md` merged (`UserService.scrubUser()` exists)
- `tasks/tests/06-epic6.md` merged (test classes exist and are failing)

## Guardrails

- **Do not touch `src/test/`** — never create, edit, delete, or rename any file under `src/test/`. If a test appears wrong, report it in your output and stop; do not fix the test.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.
- **No Flyway migrations** — never create or modify files under `src/main/resources/db/migration/`. Schema changes are applied separately in chat mode.
- **Only `src/main/java/`** — never create or modify files under `src/main/frontend/`, `src/main/resources/db/migration/`, or `src/test/`.

## Scope

Make the failing Epic 6 tests pass. Implement profile, provider link/unlink, own authorizations, anonymisation, report settings, and the About page.

## Read first (in order)

1. `docs/schema.sql` — `user_profile`, `user_oauth_providers`, `node_authorizations`
2. `docs/requirements-sr.md` — SR-F043.F01, SR-F066.F01, SR-F044.F01, SR-F045.F01, SR-F047.F01, SR-F047.F03, SR-F048.F01
3. `spec/openapi.yaml` — `/account`, `/account/providers`, `/account/authorizations`, `/account/anonymize`, `/about`
4. `docs/architecture.md` — §4 OIDC flows (provider linking path)
5. The failing tests:
   - `src/test/java/com/trawhile/AccountIT.java`
   - `src/test/java/com/trawhile/AboutIT.java`

## Modify (production code only)

| File | What to implement |
|---|---|
| `src/main/java/com/trawhile/service/AccountService.java` | `getProfile()`, `saveReportSettings()`, `linkProvider()`, `unlinkProvider()`, `getOwnAuthorizations()`, `anonymizeAccount()` |
| `src/main/java/com/trawhile/web/AccountController.java` | All `/account` endpoints |
| `src/main/java/com/trawhile/web/AboutController.java` | GDPR summary, privacy notice URL conditional, SBOM/OpenAPI links |
| `src/main/java/com/trawhile/security/TrawhileOidcUserService.java` | Provider linking path via `LINKING_PROVIDER` session attribute |

## Acceptance criteria

`./scripts/mvn-local.sh -Dtest=AccountIT,AboutIT test` passes. Do not modify test files.

## Watch out for

- **SR-F044.F01**: provider/subject unique across ALL users — 409 with code `PROVIDER_ALREADY_LINKED`
- **SR-F045.F01**: 409 with code `LAST_PROVIDER` when only one linked provider remains
- **SR-F047.F01**: delegates entirely to `UserService.scrubUser()` — no duplicated logic
- **SR-F048.F01 privacy notice**: shown only to authenticated users with at least one effective node authorization AND when `privacyNoticeUrl` is non-blank
- **Provider linking flow**: `LINKING_PROVIDER` session attr → `loadUser()` calls `linkProvider()` → sets `LINK_COMPLETE` → success handler redirects to `/account`
- **SR-F043.F01 own authorizations**: reuse the same path-annotating `AuthorizationQueries` method as SR-F009.F01; response also includes last report settings (SR-F066.F01)
