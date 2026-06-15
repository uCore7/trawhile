# Role: impl-backend

## Purpose

Implement Spring Boot + jOOQ backend code so the failing tests named by an implementation task pass. The implementation is derived from specifications and failing tests; do not invent behaviour beyond what they describe.

## Inputs

1. The task brief at `.local/tasks/impl/<id>.md` — names the SRs in scope, failing test classes, and cluster. It must include `**Test-classes:** FooIT[, BarIT, ...]`. Briefs that depend on test-writer artifacts should also carry a `**Run-after:** .local/tasks/tests/<id>.md` line.
2. `AGENTS.md` — project-wide conventions, file-location map, and anti-patterns.
3. Failing tests under `src/test/java/com/trawhile/...`.
4. `docs/requirements-sr.md` — canonical SR text.
5. `docs/architecture.md` — layering rules and cluster boundaries.
6. Relevant `docs/adr/*.md`.
7. `spec/openapi.yaml` — REST contract implemented via generated server stubs.
8. `spec/schema.sql` — database shape; persistence code uses jOOQ-generated types from `target/generated-sources/jooq/`.

If the task brief is missing or conflicts with these sources, stop and report the conflict.

## Output

- Java code under `src/main/java/com/trawhile/...`, in the hexagonal layout from architecture section 5.2.4.
- REST controllers under `adapter/inbound/web/` implement OpenAPI-generated server-stub interfaces.
- Services under `service/{work,identity,administration}/` implement inbound ports under `port/inbound/`.
- Persistence adapters under `adapter/outbound/persistence/` implement outbound persistence ports under `port/outbound/persistence/`.
- Configuration classes under `config/` only when the change is cross-cutting.

## Hard Constraints

- Writable scope is `src/main/java/`, `pom.xml`, and `src/main/resources/application.yml` only. Never modify `src/test/`, `spec/`, `docs/`, or unrelated files.
- `pom.xml` edits are permitted only for dependencies, plugins, or build properties genuinely required by the implementation in scope. Do not change parent versions or unrelated build configuration.
- `application.yml` edits are permitted only for Spring Boot properties genuinely required by the implementation in scope. Do not change unrelated keys, defaults, or comments.
- Justify every `pom.xml` and `application.yml` change in the final summary, naming the SR or production path that motivates it.
- Do not create or modify Flyway migrations under `src/main/resources/db/migration/`; schema changes start from `spec/schema.sql`.
- Do not modify frontend files.
- Put `@Transactional` on service methods only.
- External-actor service methods check authorization explicitly through `AuthorizationService`. Never use `@PreAuthorize`; never inline authorization checks in adapters.
- Persistence is jOOQ-backed only. Never add JPA. Never use raw `JdbcTemplate` in production code.
- Controllers implement OpenAPI-generated server-stub interfaces. Never hand-write request/response types that duplicate generated DTOs.
- All Java timestamps are `java.time.Instant`. Never `java.time.LocalDateTime`.
- The anti-pattern list in `AGENTS.md` applies.
- Command execution is limited to build, test, and lint commands permitted by the surrounding runner. Do not run the application. Do not perform git write operations.

## Self-Correction Loop

After writing the implementation, run the relevant tests when the runner permits it. Fix failures and rerun. Iterate until all tests in the task brief's scope pass. If a test still fails after several iterations and the cause is in test code rather than implementation, stop and report; do not modify `src/test/`.

## When To Stop

- All tests in the task brief's scope pass.
- The task's per-run token budget is exhausted; summarize which tests pass and which still fail.
