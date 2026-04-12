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
2. `docs/requirements-sr.md` — SR-001, SR-002, SR-005–SR-012
3. `docs/openapi.yaml` — `/users`, `/invitations`, `/auth/gdpr-notice`, `/settings` paths
4. `docs/architecture.md` — §4 OAuth2 flows, §5 Authorization checks
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
| `src/main/java/com/trawhile/security/TrawhileOidcUserService.java` | Bootstrap detection (SR-001); pending invitation match (SR-008); set `PENDING_GDPR` session attribute; email not persisted (SR-002) |
| `src/main/java/com/trawhile/security/OAuth2LoginSuccessHandler.java` | Already scaffolded — verify `LINK_COMPLETE` / `PENDING_GDPR` routing |
| `src/main/java/com/trawhile/web/AuthController.java` | `POST /auth/gdpr-notice` — SR-057a |
| `src/main/java/com/trawhile/service/AccountService.java` | `completeRegistration(SessionData)` — SR-057a |
| `src/main/java/com/trawhile/service/UserService.java` | `listUsers()`, `listInvitations()`, `createInvitation()`, `resendInvitation()`, `withdrawInvitation()`, `expireInvitations()`, `removeUser()`, `getUserAuthorizations()`, private `scrubUser()` |
| `src/main/java/com/trawhile/web/UserController.java` | `GET /users`, `GET /users/{id}/authorizations`, `POST /users/{id}/remove` |
| `src/main/java/com/trawhile/web/InvitationController.java` | `GET /invitations`, `POST /invitations`, `POST /invitations/{id}/resend`, `DELETE /invitations/{id}` |
| `src/main/java/com/trawhile/web/SettingsController.java` | `GET /settings` |

## Acceptance criteria

`mvn test -Dtest=BootstrapIT,DataMinimizationIT,UserManagementIT,InvitationIT,UserScrubbingIT,AuthFlowIT,SettingsIT` passes with all tests green. Do not modify test files.

## Watch out for

- **SR-007**: check both `pending_invitations` AND `user_oauth_providers` before creating; 409 if either matches
- **SR-008**: email never written to DB; session stores `pending_invitations.id`, not the email
- **SR-009b** called by four SRs — implement once as `private scrubUser(UUID)` in `UserService`; `AccountService.anonymizeAccount()` (impl/06) delegates here too
- **SR-009b stub retention**: delete `users` row only if NO `time_entries` AND NO `requests` reference it
- **SR-009b** must set `ended_at = NOW()` on active `time_entries` before deleting `user_profile`
- **SR-057a bootstrap path**: no pre-existing `users` row for the first admin — insert it inside `completeRegistration`
- **SR-057a race**: if `pending_invitations` withdrawn between callback and GDPR ack, abort and redirect to `/login?error=not_invited`
- **SR-011 path annotation**: full ancestor path via recursive CTE — use `AuthorizationQueries`; not post-processed in Java
