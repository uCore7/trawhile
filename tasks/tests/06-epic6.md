# Test task tests/06 — Epic 6: Account

## Role

You are a **test writer**. Derive all test logic from the spec. Do not read `src/main/java/`.

## Prerequisites

`tasks/00-base-it.md` merged — `BaseIT`, `TestFixtures`, and `TestSecurityHelper` must exist at `src/test/java/com/trawhile/` before this task begins.

## Guardrails

- **Do not touch `src/main/`** — never create, edit, delete, or rename any file under `src/main/`.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.

## Read (in order)

1. `docs/schema.sql` — `user_profile`, `user_oauth_providers`, `node_authorizations`
2. `docs/requirements-sr.md` — SR-043, SR-043b, SR-044, SR-045, SR-046, SR-047, SR-048
3. `docs/openapi.yaml` — `/account`, `/account/providers`, `/account/authorizations`, `/account/anonymize`, `/about`
4. `docs/test-plan.md` — TE-043-* through TE-048-*
5. `src/test/java/com/trawhile/BaseIT.java`
6. `src/test/java/com/trawhile/TestFixtures.java`
7. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```
src/test/java/com/trawhile/
  AccountIT.java    — TE-043-01, TE-043B-01, TE-044-01, TE-045-01,
                      TE-046-01, TE-047-01, TE-047-02
  AboutIT.java      — TE-048-01, TE-048-02
```

## Rules for every test method

Annotate with `@Tag("TE-xxx-nn")`. Write real assertions. No empty bodies.

## Key assertions per TE

| TE | What to assert |
|---|---|
| TE-043-01 | `GET /api/v1/account`: response has `name`, `providers` array; 401 unauthenticated |
| TE-043B-01 | `PUT /api/v1/account/report-settings` with a filter object: subsequent `GET /api/v1/account` returns same filter in `lastReportSettings` |
| TE-044-01 | `POST /api/v1/account/providers` linking a new provider: `SELECT COUNT(*) FROM user_oauth_providers WHERE user_id = {id}` increases; 409 when same provider/subject already linked to another user |
| TE-045-01 | `DELETE /api/v1/account/providers/{provider}` when 2 providers linked: `user_oauth_providers` count decreases by 1; 409 when only 1 provider remains |
| TE-046-01 | `GET /api/v1/account/authorizations`: each entry has `nodePath` from root to granted node |
| TE-047-01 | `POST /api/v1/account/anonymize` for user with `time_entries`: `user_profile` deleted; `users` row retained; `SELECT revoked_at FROM mcp_tokens WHERE user_id = {id}` all non-null |
| TE-047-02 | Same for user with no `time_entries` and no `requests`: `users` row deleted |
| TE-048-01 | `GET /api/v1/about` unauthenticated: 200; response has `version`, link to SBOM, link to OpenAPI spec |
| TE-048-02 | `GET /api/v1/about` as user with node auth and `privacyNoticeUrl` configured: response includes privacy notice URL; same request by user without any node auth: privacy notice URL absent |
