# Task impl/10 — Cross-cutting: auth, headers, CSRF, SSE, rate limiting, observability, config

## Prerequisites

- `tasks/00-base-it.md` merged
- `tasks/impl/01-epic1.md` merged (`AuthFlowIT` exists, extend it here)
- `tasks/tests/10-cross-cutting.md` merged (test classes exist and are failing)

## Guardrails

- **Do not touch `src/test/`** — never create, edit, delete, or rename any file under `src/test/`. If a test appears wrong, report it in your output and stop; do not fix the test.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.
- **No Flyway migrations** — never create or modify files under `src/main/resources/db/migration/`. Schema changes are applied separately in chat mode.
- **Only `src/main/java/`** — never create or modify files under `src/main/frontend/`, `src/main/resources/db/migration/`, or `src/test/`.

## Scope

Make the failing cross-cutting tests pass. Complete OAuth2 auth flows, HTTP security headers, CSRF, SSE dispatch, bucket4j rate limiting, Prometheus metrics, and startup config validation.

## Read first (in order)

1. `docs/requirements-sr.md` — SR-057, SR-057a, SR-058, SR-059, SR-060, SR-061, SR-062, SR-064, SR-064a, SR-064b, SR-085, SR-088, SR-089
2. `docs/architecture.md` — §2 SSE, §7 Spring Security
3. `src/main/java/com/trawhile/config/SecurityConfig.java`
4. `src/main/java/com/trawhile/config/TrawhileConfig.java`
5. `src/main/java/com/trawhile/config/StartupValidator.java`
6. `src/main/java/com/trawhile/sse/SseEmitterRegistry.java`, `SseDispatcher.java`
7. The failing tests:
   - `src/test/java/com/trawhile/AuthFlowIT.java`
   - `src/test/java/com/trawhile/SecurityHeadersIT.java`
   - `src/test/java/com/trawhile/RateLimitIT.java`
   - `src/test/java/com/trawhile/SseIT.java`
   - `src/test/java/com/trawhile/MetricsIT.java`
   - `src/test/java/com/trawhile/AuthControllerIT.java`
   - `src/test/java/com/trawhile/config/TrawhileConfigTest.java`
   - `src/test/java/com/trawhile/config/StartupValidatorTest.java`
   - `src/test/java/com/trawhile/monitoring/MonitoringArtifactsTest.java`

## Modify (production code only)

| File | What to verify / complete |
|---|---|
| `src/main/java/com/trawhile/config/SecurityConfig.java` | All 5 headers (SR-060); CSRF cookie (SR-061); permit-all for `/api/v1/auth/providers` and `/api/v1/about` |
| `src/main/java/com/trawhile/config/RateLimitFilter.java` | 429 on breach; calls `SecurityEventService.log()` (SR-059) |
| `src/main/java/com/trawhile/sse/SseDispatcher.java` | All event types (SR-062); sends `synchronized(emitter)` |
| `src/main/java/com/trawhile/config/TrawhileConfig.java` | All `@AssertTrue` validators (SR-088/089) |
| `src/main/java/com/trawhile/config/StartupValidator.java` | No-provider check (SR-089) |

## Acceptance criteria

`mvn test -Dtest=AuthFlowIT,SecurityHeadersIT,RateLimitIT,SseIT,MetricsIT,AuthControllerIT,TrawhileConfigTest,StartupValidatorTest,MonitoringArtifactsTest` passes. Do not modify test files.

## Watch out for

- **SR-060 Referrer-Policy**: exact value must be `no-referrer`
- **SR-061**: `GET`/`HEAD` do not require CSRF token; `POST`/`PUT`/`PATCH`/`DELETE` do
- **SSE sends**: `synchronized(emitter)` prevents concurrent writes; dead emitters removed immediately
- **Management port**: use `@LocalManagementPort` in tests — not `@LocalServerPort`
- **`TE-064B-*`**: reads files from `Paths.get("monitoring/...")` — project root, not classpath
- **`TrawhileConfigTest`**: use `Validation.buildDefaultValidatorFactory().getValidator()` directly — no Spring context needed
