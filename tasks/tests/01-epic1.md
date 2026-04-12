# Test task tests/01 — Epic 1: System administration

## Role

You are a **test writer**. Your job is to write failing tests derived from the specification. You do not implement production code.

## Prerequisites

`tasks/00-base-it.md` merged — `BaseIT`, `TestFixtures`, and `TestSecurityHelper` must exist at `src/test/java/com/trawhile/` before this task begins.

## Guardrails

- **Do not touch `src/main/`** — never create, edit, delete, or rename any file under `src/main/`. Derive all test logic from the spec documents and the test plan. If the spec is ambiguous, test what it says, not what seems convenient.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.

## Read (in order)

1. `docs/schema.sql` — `users`, `pending_invitations`, `user_profile`, `user_oauth_providers`, `node_authorizations`, `mcp_tokens` tables and FK/cascade rules
2. `docs/requirements-sr.md` — SR-001, SR-002, SR-005, SR-006, SR-007, SR-007a, SR-008, SR-009, SR-009a, SR-009b, SR-010, SR-011, SR-012
3. `docs/openapi.yaml` — `/users`, `/invitations`, `/auth/gdpr-notice`, `/settings` paths; exact request/response shapes
4. `docs/test-plan.md` — TE-001-* through TE-012-*, TE-057-*, TE-057A-*, TE-058-*
5. `src/test/java/com/trawhile/BaseIT.java`
6. `src/test/java/com/trawhile/TestFixtures.java`
7. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```
src/test/java/com/trawhile/
  BootstrapIT.java          — TE-001-01, TE-001-02
  DataMinimizationIT.java   — TE-002-01
  UserManagementIT.java     — TE-005-01, TE-010-01, TE-011-01
  InvitationIT.java         — TE-006-01, TE-007-01, TE-007A-01,
                               TE-008-01, TE-009-01, TE-009A-01
  UserScrubbingIT.java      — TE-009B-01, TE-009B-02, TE-009B-03
  AuthFlowIT.java           — TE-057-01, TE-057A-01, TE-057A-02, TE-058-01
  SettingsIT.java           — TE-012-01
```

## Rules for every test method

- Annotate with `@Tag("TE-xxx-nn")`
- Write real assertions — `assertThat(...)`, `assertEquals(...)`, HTTP status checks, DB state queries via `jdbc`
- Do **not** write `fail("not implemented")` or empty bodies — the test must fail for the right reason (endpoint not found, wrong response) when no implementation exists
- Use `TestFixtures` to insert precondition data; use `TestSecurityHelper` for authentication
- Verify DB state after HTTP calls using `jdbc.queryForObject(...)` where the SR specifies what must be persisted

## Key assertions per TE

| TE | What to assert |
|---|---|
| TE-001-01 | After OIDC callback with bootstrap email and no admin: HTTP session contains `PENDING_GDPR` attribute; no new `user_profile` or `user_oauth_providers` row in DB |
| TE-001-02 | OIDC callback with non-matching email and no admin: redirect to `/login?error=not_invited` |
| TE-002-01 | After complete GDPR registration flow: `SELECT COUNT(*) FROM user_profile WHERE name LIKE '%@%'` = 0; no email string in any `text` or `varchar` column |
| TE-005-01 | `GET /api/v1/users` as root admin: response contains users with `status` = `active`/`pending`/`anonymised`; 403 when called without admin |
| TE-006-01 | `GET /api/v1/invitations` as root admin: each entry has `email`, `invitedBy`, `invitedAt`; 403 for non-admin |
| TE-007-01 | `POST /api/v1/invitations` with new email: `users` count +1, `pending_invitations` count +1, response body contains `mailto:` link with base URL and email; second POST with same email: 409 |
| TE-007A-01 | `POST /api/v1/invitations/{id}/resend`: `expires_at` in DB is after original value; `users` count unchanged; response contains new `mailto:` link |
| TE-008-01 | Simulate OIDC callback where email matches a `pending_invitations` row: session has `PENDING_GDPR`; no new `user_profile`, `user_oauth_providers`, or `users` row written |
| TE-009-01 | `DELETE /api/v1/invitations/{id}` as admin: `pending_invitations` row deleted; `node_authorizations` for that user deleted; 403 for non-admin |
| TE-009A-01 | Insert `pending_invitations` row with `expires_at = NOW() - INTERVAL '1 day'`; call `UserService.expireInvitations()`; `pending_invitations` row gone, `node_authorizations` for that user deleted |
| TE-009B-01 | User has an active `time_entries` row; trigger scrubbing; `ended_at` set on active entry; `user_profile` deleted; `users` row retained |
| TE-009B-02 | User has no `time_entries` and no `requests`; trigger scrubbing; `users` row deleted |
| TE-009B-03 | User has non-revoked `mcp_tokens`; trigger scrubbing; all tokens have `revoked_at IS NOT NULL` |
| TE-010-01 | `POST /api/v1/users/{id}/remove` as admin: SR-009b state applied; 403 for non-admin |
| TE-011-01 | `GET /api/v1/users/{id}/authorizations` as admin: each entry has `nodePath` array from root to granted node; 403 for non-admin |
| TE-012-01 | `GET /api/v1/settings` as authenticated user: response has `name`, `timezone`, `freezeOffsetYears`, `effectiveFreezeCutoff`, `retentionYears`, `nodeRetentionExtraYears`; 401 unauthenticated |
| TE-057-01 | OIDC callback for existing `user_oauth_providers` row: authenticated session established; redirect to `/` |
| TE-057A-01 | `POST /api/v1/auth/gdpr-notice` with valid session: `user_profile` inserted, `user_oauth_providers` inserted, `pending_invitations` row deleted — all in one transaction; 400 with no session |
| TE-057A-02 | `POST /api/v1/auth/gdpr-notice` when `pending_invitations` row no longer exists: redirect to `/login?error=not_invited` |
| TE-058-01 | OIDC callback with unknown provider/subject and no matching invitation: redirect to `/login?error=not_invited`; response does not differ between "not found" and "expired" |
