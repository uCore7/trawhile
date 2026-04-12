# Test task tests/10 — Cross-cutting concerns

## Role

You are a **test writer**. Derive all test logic from the spec. Do not read `src/main/java/`.

## Prerequisites

`tasks/00-base-it.md` merged — `BaseIT`, `TestFixtures`, and `TestSecurityHelper` must exist at `src/test/java/com/trawhile/` before this task begins.

## Guardrails

- **Do not touch `src/main/`** — never create, edit, delete, or rename any file under `src/main/`.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.

## Read (in order)

1. `docs/schema.sql` — `security_events` table; `event_type` enum (for RATE_LIMIT_BREACH)
2. `docs/requirements-sr.md` — SR-057, SR-057a, SR-058, SR-059, SR-060, SR-061, SR-062, SR-064, SR-064a, SR-064b, SR-085, SR-088, SR-089
3. `docs/openapi.yaml` — `/auth/callback`, `/auth/acknowledge-gdpr`, `/auth/providers`, `/metrics`, `/sse` paths
4. `docs/test-plan.md` — TE-057-*, TE-057A-*, TE-058-*, TE-059-*, TE-060-*, TE-061-*, TE-062-*, TE-064-*, TE-064A-*, TE-064B-*, TE-085-*, TE-088-*, TE-089-*
5. `src/test/java/com/trawhile/BaseIT.java`
6. `src/test/java/com/trawhile/TestFixtures.java`
7. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```
src/test/java/com/trawhile/
  AuthFlowIT.java            — TE-057-01, TE-057A-01/02, TE-058-01
  RateLimitIT.java           — TE-059-01, TE-059-02
  SecurityHeadersIT.java     — TE-060-01/02/03/04/05, TE-061-01
  SseIT.java                 — TE-062-01/02/03
  MetricsIT.java             — TE-064-01, TE-064A-01/02
  MonitoringArtifactsTest.java — TE-064B-01/02/03   (plain JUnit 5, no Spring)
  AuthControllerIT.java      — TE-085-01
  TrawhileConfigTest.java    — TE-088-01/02/03/04, TE-089-02/03
  StartupValidatorTest.java  — TE-089-01
```

## Rules for every test method

Annotate with `@Tag("TE-xxx-nn")`. Write real assertions. No empty bodies.

`MonitoringArtifactsTest`, `TrawhileConfigTest`, and `StartupValidatorTest` are plain JUnit 5 (no `@SpringBootTest`, no Testcontainers).

## Key assertions per TE

| TE | What to assert |
|---|---|
| TE-057-01 | Simulate OIDC callback for a user who already has a `user_oauth_providers` row; `HttpSession` is created; response redirects to `/`; no new DB rows inserted |
| TE-057A-01 | Call `POST /api/v1/auth/acknowledge-gdpr` with a session that carries a pending invitation context (set via `TestSecurityHelper`): one `user_profile` row inserted, one `user_oauth_providers` row inserted, `pending_invitations` row deleted — all in a single transaction (verify by inserting rows then calling the endpoint); 400 when no pending session |
| TE-057A-02 | Withdraw the invitation between the OIDC callback and the acknowledge call (delete the `pending_invitations` row); call `POST /api/v1/auth/acknowledge-gdpr`; response redirects to the "not invited" error page |
| TE-058-01 | Simulate OIDC callback for a subject with no matching `pending_invitations` row; response redirects to login error page; response body does not distinguish "not found" from "expired" |
| TE-059-01 | Send requests at a rate within the configured limit; all return 2xx; then send requests exceeding the limit; expect 429 |
| TE-059-02 | Trigger a rate-limit breach (exceed limit); `SELECT COUNT(*) FROM security_events WHERE event_type = 'RATE_LIMIT_BREACH'` increases by 1 |
| TE-060-01 | Issue any request; response has `Content-Security-Policy` header with a non-empty value |
| TE-060-02 | Issue any request; response has `Strict-Transport-Security` header |
| TE-060-03 | Issue any request; response has `X-Frame-Options: DENY` |
| TE-060-04 | Issue any request; response has `X-Content-Type-Options: nosniff` |
| TE-060-05 | Issue any request; response has `Referrer-Policy: no-referrer` |
| TE-061-01 | `POST` to any mutating endpoint with a valid CSRF token (via `SecurityMockMvcRequestPostProcessors.csrf()`): request proceeds; same POST without the CSRF token: 403 |
| TE-062-01 | Open an SSE connection as user A; trigger a tracking state change for user A from a second session; the SSE stream for the first session receives a tracking event |
| TE-062-02 | Open an SSE connection as user A who has `view` auth on node X; update node X's name; the SSE stream receives a node-update event; a user without `view` on node X does not receive it |
| TE-062-03 | Open an SSE connection as user A; an admin changes user A's authorization; the SSE stream receives an authorization-change event |
| TE-064-01 | `GET /actuator/prometheus` on the management port: HTTP 200, `Content-Type` starts with `text/plain`, body contains `jvm_memory_used_bytes`; same path on the main port: 404 |
| TE-064A-01 | `GET /actuator/prometheus` on the management port: body contains all ten metric names defined in SR-064a (assert each by name) |
| TE-064A-02 | Run the activity purge job; re-fetch `/actuator/prometheus`; `trawhile_purge_job_last_completed_seconds{job_type="activity"}` is present and its value is greater than 0 |
| TE-064B-01 | Plain JUnit 5: load `monitoring/prometheus.yml` from the classpath (or project root); assert file exists; parse as YAML; assert no parse error |
| TE-064B-02 | Plain JUnit 5: load `monitoring/alerting-rules.yml`; assert it contains alert names: `PurgeJobStale`, `DatabaseErrors`, `HighErrorRate`, `InstanceDown` |
| TE-064B-03 | Plain JUnit 5: load `monitoring/grafana-dashboard.json`; parse as JSON; assert all 10 metric names from SR-064a appear in the JSON string |
| TE-085-01 | `GET /api/v1/auth/providers` with no authentication: HTTP 200; response body lists the configured OAuth2 provider IDs (e.g., `["github","google"]`); no sensitive data (client secrets) in response |
| TE-088-01 | Construct a `TrawhileConfig` with all valid values; run Bean Validation (`Validator.validate()`); constraint violations set is empty |
| TE-088-02 | `TrawhileConfig` with `retentionYears = 1` (below minimum of 2): validation produces at least one violation on `retentionYears` |
| TE-088-03 | `TrawhileConfig` with `freezeOffsetYears > retentionYears`: validation produces at least one cross-field violation |
| TE-088-04 | `TrawhileConfig` with `nodeRetentionExtraYears = -1`: validation violation |
| TE-089-01 | `StartupValidator` with at least one provider configured: `validate()` completes without throwing; with zero providers configured: throws `IllegalStateException` |
| TE-089-02 | `TrawhileConfig` with `timezone = "Not/ATimezone"` (invalid IANA zone): validation violation |
| TE-089-03 | `TrawhileConfig` with `privacyNoticeUrl = "not-a-url"` (malformed URL): validation violation |
