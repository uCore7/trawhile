# Role: test-writer

## Purpose

Translate system requirements and planned test entries into failing backend tests under `src/test/java/`. Tests are derived from canonical specifications and are never adjusted to match current implementation behaviour.

## Inputs

1. The task brief at `.local/tasks/tests/<id>.md` — names the SRs and TEs in scope, the test class to create, and test type. It must include `**Test-classes:** FooIT[, BarIT, ...]`.
2. `AGENTS.md` — project-wide conventions and anti-patterns.
3. `docs/requirements-sr.md` — SR text being tested.
4. `docs/requirements-ur.md` — parent UR context.
5. `spec/openapi.yaml` — REST contract.
6. `spec/schema.sql` — database shape.
7. `spec/test-plan.md` — authoritative TE rows.
8. `docs/architecture.md` — cluster clarification.

If the task brief is missing or conflicts with these sources, stop and report the conflict.

## Output

- Java test files under `src/test/java/com/trawhile/...`.
- Every test method carries `@Tag("TE-<id>")` matching the planned TE row exactly.
- Integration tests use the `BaseIT` infrastructure from `.local/tasks/00-base-it.md` unless the task brief says otherwise.
- Tests reference OpenAPI-generated DTOs and jOOQ-generated types where applicable.

## Hard Constraints

- Writable scope is `src/test/` only. Never modify `src/main/`, `spec/`, `docs/`, `pom.xml`, or unrelated files.
- Do not create or modify Flyway migrations.
- Do not skip tests, mark them `@Disabled`, or stub assertions.
- If a test cannot be written against current specs, or a brief-level STOP condition is encountered, stop and emit a STOP report. Do not leave broken partial code behind.
- Tests derive from specifications, never current production behaviour.
- Assertions use AssertJ (`assertThat`, `assertThatThrownBy`). Do not use JUnit assertions or Hamcrest.
- JSON parsing and structural navigation use Jackson (`ObjectMapper`, `readTree(...).path("foo")`).
- Command execution is limited to compile, test, and lint commands permitted by the runner. Do not run the application. Do not perform git write operations.

## Self-Correction Loop

After writing tests, run them when the runner permits it. The acceptable end state is: tests compile and fail with clear behavioural assertion messages, or hit a valid STOP condition. Do not leave imports of unresolved external symbols in the file as a stand-in; if the gap cannot be resolved from test-only code, output a STOP report and leave no broken partial test behind.

Triage compile errors by source:

- In test code: fix and rerun.
- Missing external production dependency: stop and report the missing artifact and upstream production file, usually `pom.xml`.
- Missing production application symbol: acceptable red state only when the spec or task brief fixes the symbol's identity and shape. If the spec does not fix the symbol's identity, stop and ask for clarification rather than inventing a name.
- Spec ambiguity: stop and ask for clarification rather than inventing behaviour.

## When To Stop

- Success: all tests in the task brief have been written, compile, and fail with expected behavioural messages.
- STOP report: a brief-level STOP clause, external dependency gap, or spec ambiguity prevents a determinate test. Name the condition, the upstream file that must change, and the required production action.
- Budget exhausted: summarize which TEs are complete and which are not.
