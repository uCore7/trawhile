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

Make the failing cross-cutting tests pass. Complete OIDC auth flows, HTTP security headers, CSRF, SSE dispatch, bucket4j rate limiting, Prometheus metrics, and startup config validation.

## Read first (in order)

1. `docs/requirements-sr.md` — SR-F067.F01, SR-F067.F02, SR-F060.F02, SR-C002.F01, SR-C011.C01, SR-C012.C01, SR-C013.C01, SR-F068.F01, SR-F059.F01, SR-F059.F02, SR-F059.F03, SR-F050.F05, SR-F065.F01
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
| `src/main/java/com/trawhile/config/SecurityConfig.java` | All 5 headers (SR-C012.C01); CSRF cookie (SR-C013.C01); permit-all for `/api/v1/auth/providers` and `/api/v1/about` |
| `src/main/java/com/trawhile/config/RateLimitFilter.java` | 429 on breach; calls `SecurityEventService.log()` (SR-C011.C01) |
| `src/main/java/com/trawhile/sse/SseDispatcher.java` | All event types (SR-F068.F01); sends `synchronized(emitter)` |
| `src/main/java/com/trawhile/config/TrawhileConfig.java` | All `@AssertTrue` validators (SR-F050.F05/SR-F065.F01) |
| `src/main/java/com/trawhile/config/StartupValidator.java` | No-provider check (SR-F065.F01) |

## Acceptance criteria

`./scripts/mvn-local.sh -Dtest=AuthFlowIT,SecurityHeadersIT,RateLimitIT,SseIT,MetricsIT,AuthControllerIT,TrawhileConfigTest,StartupValidatorTest,MonitoringArtifactsTest test` passes. Do not modify test files.

## Watch out for

- **SR-C012.C01 Referrer-Policy**: exact value must be `no-referrer`
- **SR-C013.C01**: `GET`/`HEAD` do not require CSRF token; `POST`/`PUT`/`PATCH`/`DELETE` do
- **SSE sends**: `synchronized(emitter)` prevents concurrent writes; dead emitters removed immediately
- **Management port**: use `@LocalManagementPort` in tests — not `@LocalServerPort`
- **`TE-F059.F03-*`**: reads files from `Paths.get("monitoring/...")` — project root, not classpath
- **`TrawhileConfigTest`**: use `Validation.buildDefaultValidatorFactory().getValidator()` directly — no Spring context needed
