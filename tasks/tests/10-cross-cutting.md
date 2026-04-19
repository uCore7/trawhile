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
2. `docs/requirements-sr.md` — SR-F067.F01, SR-F067.F02, SR-F060.F02, SR-C002.F01, SR-C011.C01, SR-C012.C01, SR-C013.C01, SR-F068.F01, SR-F059.F01, SR-F059.F02, SR-F059.F03, SR-F050.F05, SR-F065.F01
3. `spec/openapi.yaml` — `/auth/gdpr-notice`, `/auth/providers`, and `/events` paths
4. `spec/test-plan.md` — TE-F067.F01-*, TE-F060.F02-*, TE-C002.F01-*, TE-C011.C01-*, TE-C012.C01-*, TE-C013.C01-*, TE-F068.F01-*, TE-F059.F01-*, TE-F059.F02-*, TE-F059.F03-*, TE-F067.F02-*, TE-F050.F05-*, TE-F065.F01-*
5. `src/test/java/com/trawhile/BaseIT.java`
6. `src/test/java/com/trawhile/TestFixtures.java`
7. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```
src/test/java/com/trawhile/
  AuthFlowIT.java            — TE-F067.F01-01, TE-F060.F02-01/02, TE-C002.F01-01
  RateLimitIT.java           — TE-C011.C01-01, TE-C011.C01-02
  SecurityHeadersIT.java     — TE-C012.C01-01/02/03/04/05, TE-C013.C01-01
  SseIT.java                 — TE-F068.F01-01/02/03
  MetricsIT.java             — TE-F059.F01-01, TE-F059.F02-01/02
  MonitoringArtifactsTest.java — TE-F059.F03-01/02/03   (plain JUnit 5, no Spring)
  AuthControllerIT.java      — TE-F067.F02-01
  TrawhileConfigTest.java    — TE-F050.F05-01/02/03/04, TE-F065.F01-02/03
  StartupValidatorTest.java  — TE-F065.F01-01
```

## Rules for every test method

Annotate with `@Tag("TE-Fxxx.Fxx-nn")`. Write real assertions. No empty bodies.

`MonitoringArtifactsTest`, `TrawhileConfigTest`, and `StartupValidatorTest` are plain JUnit 5 (no `@SpringBootTest`, no Testcontainers).

## Key assertions per TE

| TE | What to assert |
|---|---|
| TE-F067.F01-01 | Simulate OIDC callback for a user who already has a `user_oauth_providers` row; `HttpSession` is created; response redirects to `/`; no new DB rows inserted |
| TE-F060.F02-01 | Call `POST /api/v1/auth/gdpr-notice` with a session that carries a pending invitation context (set via `TestSecurityHelper`): one `user_profile` row inserted, one `user_oauth_providers` row inserted, `pending_invitations` row deleted — all in a single transaction (verify by inserting rows then calling the endpoint); 400 when no pending session |
| TE-F060.F02-02 | Withdraw the invitation between the OIDC callback and the acknowledge call (delete the `pending_invitations` row); call `POST /api/v1/auth/gdpr-notice`; response redirects to the "not invited" error page |
| TE-C002.F01-01 | Simulate OIDC callback for a subject with no matching `pending_invitations` row; response redirects to login error page; response body does not distinguish "not found" from "expired" |
| TE-C011.C01-01 | Send requests at a rate within the configured limit; all return 2xx; then send requests exceeding the limit; expect 429 |
| TE-C011.C01-02 | Trigger a rate-limit breach (exceed limit); `SELECT COUNT(*) FROM security_events WHERE event_type = 'RATE_LIMIT_BREACH'` increases by 1 |
| TE-C012.C01-01 | Issue any request; response has `Content-Security-Policy` header with a non-empty value |
| TE-C012.C01-02 | Issue any request; response has `Strict-Transport-Security` header |
| TE-C012.C01-03 | Issue any request; response has `X-Frame-Options: DENY` |
| TE-C012.C01-04 | Issue any request; response has `X-Content-Type-Options: nosniff` |
| TE-C012.C01-05 | Issue any request; response has `Referrer-Policy: no-referrer` |
| TE-C013.C01-01 | `POST` to any mutating endpoint with a valid CSRF token (via `SecurityMockMvcRequestPostProcessors.csrf()`): request proceeds; same POST without the CSRF token: 403 |
| TE-F068.F01-01 | Open an SSE connection as user A; trigger a tracking state change for user A from a second session; the SSE stream for the first session receives a tracking event |
| TE-F068.F01-02 | Open an SSE connection as user A who has `view` auth on node X; update node X's name; the SSE stream receives a node-update event; a user without `view` on node X does not receive it |
| TE-F068.F01-03 | Open an SSE connection as user A; an admin changes user A's authorization; the SSE stream receives an authorization-change event |
| TE-F059.F01-01 | `GET /actuator/prometheus` on the management port: HTTP 200, `Content-Type` starts with `text/plain`, body contains `jvm_memory_used_bytes`; same path on the main port: 404 |
| TE-F059.F02-01 | `GET /actuator/prometheus` on the management port: body contains all ten metric names defined in SR-F059.F02 (assert each by name) |
| TE-F059.F02-02 | Run the activity purge job; re-fetch `/actuator/prometheus`; `trawhile_purge_job_last_completed_seconds{job_type="activity"}` is present and its value is greater than 0 |
| TE-F059.F03-01 | Plain JUnit 5: load `monitoring/prometheus.yml` from the classpath (or project root); assert file exists; parse as YAML; assert no parse error |
| TE-F059.F03-02 | Plain JUnit 5: load `monitoring/alerting-rules.yml`; assert it contains alert names: `PurgeJobStale`, `DatabaseErrors`, `HighErrorRate`, `InstanceDown` |
| TE-F059.F03-03 | Plain JUnit 5: load `monitoring/grafana-dashboard.json`; parse as JSON; assert all 10 metric names from SR-F059.F02 appear in the JSON string |
| TE-F067.F02-01 | `GET /api/v1/auth/providers` with no authentication: HTTP 200; response body contains a `providers` array listing the configured OIDC provider IDs (e.g., `["google","microsoft"]`); no sensitive data (client secrets) in response |
| TE-F050.F05-01 | Construct a `TrawhileConfig` with all valid values; run Bean Validation (`Validator.validate()`); constraint violations set is empty |
| TE-F050.F05-02 | `TrawhileConfig` with `retentionYears = 1` (below minimum of 2): validation produces at least one violation on `retentionYears` |
| TE-F050.F05-03 | `TrawhileConfig` with `freezeOffsetYears > retentionYears`: validation produces at least one cross-field violation |
| TE-F050.F05-04 | `TrawhileConfig` with `nodeRetentionExtraYears = -1`: validation violation |
| TE-F065.F01-01 | `StartupValidator` with at least one provider configured: `validate()` completes without throwing; with zero providers configured: throws `IllegalStateException` |
| TE-F065.F01-02 | `TrawhileConfig` with `timezone = "Not/ATimezone"` (invalid IANA zone): validation violation |
| TE-F065.F01-03 | `TrawhileConfig` with `privacyNoticeUrl = "not-a-url"` (malformed URL): validation violation |
