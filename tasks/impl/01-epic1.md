# Task impl/01 — Epic 1: System administration

## Prerequisites

- `tasks/00-base-it.md` merged (`BaseIT`, `TestFixtures`, `TestSecurityHelper` exist)
- `tasks/tests/01-epic1.md` merged (test classes exist and are failing)

## Guardrails

- **Do not touch `src/test/`** — never create, edit, delete, or rename any file under `src/test/`. If a test appears wrong, report it in your output and stop; do not fix the test.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.
- **No Flyway migrations** — never create or modify files under `src/main/resources/db/migration/`. Schema changes are applied separately in chat mode.
- **Only `src/main/java/`** — never create or modify files under `src/main/frontend/`, `src/main/resources/db/migration/`, or `src/test/`.

## Scope

Make the failing Epic 1 tests pass. Implement user management, invitation lifecycle, bootstrap detection, GDPR registration, and system settings.

## Read first (in order)

1. `docs/schema.sql` — `users`, `pending_invitations`, `user_profile`, `user_oauth_providers`, `node_authorizations`, `mcp_tokens`
2. `docs/requirements-sr.md` — SR-F001.F01, SR-C006.C01, SR-F004.F01–SR-F011.F01
3. `spec/openapi.yaml` — `/users`, `/invitations`, `/auth/gdpr-notice`, `/settings` paths
4. `docs/architecture.md` — §4 OIDC flows, §5 Authorization checks
5. The failing tests (read to understand what is expected):
   - `src/test/java/com/trawhile/BootstrapIT.java`
   - `src/test/java/com/trawhile/DataMinimizationIT.java`
   - `src/test/java/com/trawhile/UserManagementIT.java`
   - `src/test/java/com/trawhile/InvitationIT.java`
   - `src/test/java/com/trawhile/UserScrubbingIT.java`
   - `src/test/java/com/trawhile/AuthFlowIT.java`
   - `src/test/java/com/trawhile/SettingsIT.java`

## Modify (production code only)

| File | What to implement |
|---|---|
| `src/main/java/com/trawhile/security/TrawhileOidcUserService.java` | Bootstrap detection (SR-F001.F01); pending invitation match (SR-F060.F01); set `PENDING_GDPR` session attribute; email not persisted (SR-C006.C01) |
| `src/main/java/com/trawhile/security/OidcLoginSuccessHandler.java` | Already scaffolded — verify `LINK_COMPLETE` / `PENDING_GDPR` routing |
| `src/main/java/com/trawhile/web/AuthController.java` | `POST /auth/gdpr-notice` — SR-F060.F02 |
| `src/main/java/com/trawhile/service/AccountService.java` | `completeRegistration(SessionData)` — SR-F060.F02 |
| `src/main/java/com/trawhile/service/UserService.java` | `listUsers()`, `listInvitations()`, `createInvitation()`, `resendInvitation()`, `withdrawInvitation()`, `expireInvitations()`, `removeUser()`, `getUserAuthorizations()`, private `scrubUser()` |
| `src/main/java/com/trawhile/web/UserController.java` | `GET /users`, `GET /users/{id}/authorizations`, `POST /users/{id}/remove` |
| `src/main/java/com/trawhile/web/InvitationController.java` | `GET /invitations`, `POST /invitations`, `POST /invitations/{id}/resend`, `DELETE /invitations/{id}` |
| `src/main/java/com/trawhile/web/SettingsController.java` | `GET /settings` |

## Acceptance criteria

`mvn test -Dtest=BootstrapIT,DataMinimizationIT,UserManagementIT,InvitationIT,UserScrubbingIT,AuthFlowIT,SettingsIT` passes with all tests green. Do not modify test files.

## Watch out for

- **SR-F006.F01**: check both `pending_invitations` AND `user_oauth_providers` before creating; 409 if either matches
- **SR-F060.F01**: email never written to DB; session stores `pending_invitations.id`, not the email
- **SR-F070.F01** is invoked by four SRs (SR-F007.F01, SR-C010.C01, SR-F008.F01, SR-F047.F01) — implement once as `private scrubUser(UUID)` in `UserService`; `AccountService.anonymizeAccount()` (impl/06) delegates here too
- **SR-F070.F01 stub retention**: delete `users` row only if NO `time_records` AND NO `requests` reference it
- **SR-F070.F01** must set `ended_at = NOW()` on active `time_records` before deleting `user_profile`
- **SR-F060.F02 bootstrap path**: no pre-existing `users` row for the first admin — insert it inside `completeRegistration`
- **SR-F060.F02 race**: if `pending_invitations` withdrawn between callback and GDPR ack, abort and redirect to `/login?error=not_invited`
- **SR-F009.F01 path annotation**: full ancestor path via recursive CTE — use `AuthorizationQueries`; not post-processed in Java
