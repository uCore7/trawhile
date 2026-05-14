# Development process

This document defines how trawhile is specified, architected, implemented, and maintained. It separates requirements engineering, architecture, system requirements, technical specification, scaffolding, and implementation cycles so each artifact has a clear owner and purpose.

**Status legend:** ✓ complete · ▶ in progress · ○ not started

---

## Source and Artifact Policy

The process distinguishes between **canonical sources** and **derived artifacts**.

**Canonical sources** are edited directly in chat mode and reviewed as the source of truth:

- `docs/requirements-ur.md` — user requirements, stakeholders, system/context boundary, customer-side constraints
- `docs/glossary.md` — canonical terminology
- `docs/architecture.md` — arc42 architecture overview
- `docs/adr/` — Architecture Decision Records
- `docs/requirements-sr.md` — system requirements
- `docs/schema.sql` — canonical PostgreSQL schema
- `CLAUDE.md` and `AGENTS.md` — collaboration and agent guardrails

**Derived artifacts** are regenerated from canonical sources whenever identifiers, terminology, contracts, schema, or epic structure change:

- `spec/openapi.yaml`
- Flyway V1 migration
- jOOQ code-generation schema inputs, for example `src/main/resources/db/codegen/jooq-schema.sql`
- scaffold classes/interfaces and placeholder controllers/services
- `spec/test-plan.md` baseline rows and ID structure
- `tasks/tests/*.md` and `tasks/impl/*.md` scaffolds

**Rule of preference:** regeneration is preferred over manual patching for derived artifacts. Manual patching is allowed only for small exceptions, generator defects, or deliberate human refinements that are later folded back into generator logic.

**Acceptance rule:** generated output is never accepted blindly. Every regeneration step requires human review before it becomes the new baseline.

**Schema rule for jOOQ:** `docs/schema.sql` remains the single canonical database schema. Runtime database objects needed by the application or generated query code, including PostgreSQL authorization helpers, must be defined there first. The Flyway V1 migration and any jOOQ-specific schema input file are derived artifacts. They must not become independent hand-maintained schema sources.

**Schema regeneration rule:** when `docs/schema.sql` changes, regenerate derived schema artifacts via repository scripts rather than patching them manually:

- Flyway V1 migration: `./scripts/generate-schema-v1.sh`
- jOOQ schema input subset, if present: `./scripts/generate-jooq-schema.sh`

Review regenerated output before accepting it, but do not treat derived files as a second place to edit schema definitions.

**ADR rule:** ADRs are append-only decision records. If a decision changes, create a new ADR with the next number and mark the previous ADR as superseded. Do not rewrite history to make an old decision look current.

**UML note:** UML files in `docs/uml/` are not canonical sources and are not treated as fully generated artifacts. They are maintained in chat mode as communication artifacts derived from the canonical documents, with human judgment deciding what to visualize and how much detail to include.

---

## Phase 1 — Requirements Engineering ✓

**Owner:** requirements engineer / product owner  
**Contributors:** domain stakeholders, software architect  
**Reviewers:** software architect, domain stakeholders

This phase captures the problem space and stakeholder intent. It includes customer, user, regulatory, neighboring-system, and context-specific constraints.

**Steps:**

1. Identify all stakeholders.
2. Document the system boundary and context boundary.
3. Write `docs/glossary.md` with canonical domain terms.
4. Write user requirements (URs) in IREB format with goal rationale.
5. Record customer-side constraints, including standards, regulations, neighboring systems, and context-specific conventions.
6. Document key invariants.
7. Maintain problem-space UML diagrams in `docs/uml/`, such as context, domain, stakeholder, or process diagrams.

**Conventions:**

Follow the IREB standard for requirements engineering artifacts. Produce the typical IREB results: stakeholder analysis, system context, glossary, user requirements, and constraints.

Do not use user stories as the canonical requirements format. Rationale: [ADR 0012](adr/0012-use-ireb-requirements-instead-of-user-stories.md).

**Requirement types (URs):**

| UR type | Meaning | Must have SR children? |
|---|---|---|
| Functional (`F`) | A stakeholder capability | Yes, at least one SR of any type |
| Quality (`Q`) | A non-functional property required by a stakeholder | Yes, at least one SR of any type |
| Constraint (`C`) | A stakeholder-level constraint, such as GDPR, CRA, standards, or customer policy; satisfied by construction and verified by review | Optional |

**Identifier schemes:**

- `ST-1`, `ST-2`, ... — stakeholders
- `UR-F012`, `UR-Q003`, `UR-C007` — user requirements; type-prefixed, three-digit, zero-padded sequence
- Named external regulations such as GDPR, CRA, and OWASP are cited in rationale fields, not used as standalone identifiers

**ID sequence rule:** each ID sequence (`UR-F`, `UR-Q`, `UR-C`, `ST`) is strictly increasing. New IDs must be higher than all currently assigned IDs in the same sequence. Gaps must not be filled and retired IDs must not be reused.

**Tool:** chat mode. No agents.

**Outputs:** `docs/requirements-ur.md`, `docs/glossary.md`, `docs/uml/`

---

## Phase 2 — Architecture Elaboration ✓

**Owner:** software architect  
**Essential contributor:** lead developer / tech lead  
**Contributors:** operations, security/privacy reviewers, requirements engineer  
**Reviewers:** lead developer, product owner where architectural choices affect stakeholder-visible behavior

This phase elaborates the solution architecture. It includes technical constraints, organizational constraints, architecture drivers, solution strategy, cross-cutting concepts, and architectural decisions.

Architecture work is iterative. ADRs are written while architecture is being elaborated, not after architecture is finished.

**Steps:**

1. Identify technical and organizational constraints.
2. Define the solution strategy.
3. Write `docs/architecture.md` in arc42 format.
4. Elaborate building blocks, runtime view, deployment view, cross-cutting concepts, architecture diagrams, risks, and technical debt.
5. Record significant architectural decisions as ADRs under `docs/adr/`.
6. Keep `docs/architecture.md` descriptive; move rationale into ADRs.

**arc42 and ADR split:**

- `docs/architecture.md` follows arc42 and describes the current architecture.
- `docs/adr/` implements arc42 chapter 9: architecture decisions and their rationale.

**ADR conventions:**

- Use `docs/adr/NNNN-short-kebab-title.md`.
- Use four-digit, monotonically increasing numbers.
- Never renumber ADRs.
- Use Nygard sections: `Status`, `Context`, `Decision`, `Consequences`.
- Valid statuses: `Proposed`, `Accepted`, `Rejected`, `Deprecated`, `Superseded`.
- When replacing a decision, create a new ADR and mark the old one as superseded.

**arc42 conventions:**

- `docs/architecture.md` follows arc42 chapters 1-12.
- Chapters overlapping with canonical requirements should refer to the relevant requirements document instead of duplicating it.
- Chapter 12 refers to `docs/glossary.md`.

**Tool:** chat mode. No agents.

**Outputs:** `docs/architecture.md`, `docs/adr/*.md`

---

## Phase 3 — System Requirements Elaboration ✓

**Owner:** software architect  
**Essential contributor:** lead developer / tech lead  
**Reviewers:** requirements engineer / product owner, test architect

This phase elaborates system requirements from user requirements, constraints, and architectural decisions. The software architect is accountable for architectural consistency and solution-level correctness. The lead developer provides feasibility feedback during elaboration, including implementation consequences, package/API realism, persistence implications, operational implications, and hidden technical gaps. The requirements engineer or product owner reviews that stakeholder intent remains intact. The test architect reviews that requirements are verifiable and traceable.

**Steps:**

1. Derive system requirements (SRs) from URs, constraints, and ADRs.
2. Verify traceability: every UR-F and UR-Q has at least one SR child.
3. Ensure every SR cites its parent UR in the rationale field.
4. Classify each SR as functional, quality, or constraint.
5. Review feasibility and implementability with the lead developer.
6. Review verifiability and testability with the test architect.

**Conventions:**

- Every SR cites its parent UR in the rationale field.
- A single UR may yield multiple SRs.
- The type of a UR and the type of its derived SRs are independent.
- A UR of any type may derive SRs of any type.

**Requirement types (SRs):**

| SR type | Meaning | Must have TEs? |
|---|---|---|
| Functional (`F`) | A system behavior that implements a stakeholder capability | Yes, at least one |
| Quality (`Q`) | A system property that realizes a non-functional requirement | Yes, at least one |
| Constraint (`C`) | A technical constraint at system level; satisfied by construction and verified by review | No |

**Identifier schemes:**

- `SR-F012.F01`, `SR-F012.Q02`, `SR-F012.C03` — system requirements
- Format: `SR-{parent-UR-type}{parent-UR-number}.{SR-own-type}{nn}`
- The left part identifies the parent UR.
- The right part is a type qualifier plus a two-digit, zero-padded sequence number shared across all SR children of the same parent UR.

Example:

```text
UR-F012 -> SR-F012.F01, SR-F012.Q02, SR-F012.C03
UR-Q003 -> SR-Q003.F01, SR-Q003.Q02
UR-C007 -> SR-C007.C01, or no SR if the constraint is satisfied directly by construction
```

Every SR of type F or Q must later have at least one TE, regardless of the parent UR type. SR-C entries never have TEs. A UR-F or UR-Q must produce at least one SR of any type. A UR-C may produce zero SRs.

**ID sequence rule:** SR sequences are subordinate to their parent UR and restart per parent. Classify all URs as F, Q, or C before deriving or renaming SR IDs. URs drive the numbering.

**Tool:** chat mode. No agents.

**Outputs:** `docs/requirements-sr.md`

---

## Phase 4 — Technical Specification ✓

**Owner:** lead developer / tech lead  
**Co-owner:** software architect  
**Essential contributor:** test architect  
**Reviewers:** requirements engineer / product owner where externally visible behavior changes

This phase derives technical contracts from the user requirements, system requirements, and architecture. It turns the system model into schema, API, traceability, and verification artifacts.

**Steps:**

1. Design the PostgreSQL schema in `docs/schema.sql`.
2. Evaluate every data field against the GDPR necessity principle before adding it.
3. Generate the REST API contract (`spec/openapi.yaml`) from the canonical requirements and schema, then review and refine it.
4. For every SR-F and SR-Q, specify at least one happy-path test and one error-path test.
5. Assign each test a type: IT, UT, SIT, CT, or E2E.
6. Generate the initial traceability matrix UR -> SR -> TE in `spec/test-plan.md`, then review and enrich it manually.

**Schema conventions:**

- `docs/schema.sql` is authoritative.
- Runtime database objects required by application code or generated query code belong in `docs/schema.sql` first.
- Flyway V1 migration and jOOQ schema inputs are derived from `docs/schema.sql`.
- Do not hand-maintain derived schema artifacts as alternate schema sources.

**Test and traceability conventions:**

- Every SR-F and SR-Q has at least one TE.
- SR-C entries have no TEs.
- TE IDs are `TE-{SR-id}-{nn}` matching the parent SR exactly, for example `TE-F012.F01-01`.
- TE sequence is two-digit, zero-padded, and restarts within each SR.
- When an SR is renumbered, its TEs are renumbered to match in the same change.
- Retired TEs are replaced by a one-line tombstone row in the test plan table.
- `@Tag("TE-F012.F01-01")` appears on every backend test method for CI filtering and traceability.
- Baseline matrix rows may be generated from SR IDs; test intent, path selection, and edge-case coverage remain a human review responsibility.

**Traceability chain:**

```text
UR-F012 -> SR-F012.F01, SR-F012.Q02, SR-F012.C03
         -> TE-F012.F01-01, TE-F012.Q02-01, none for SR-C
```

**Tool:** chat mode. No agents.

**Outputs:** `docs/schema.sql`, `spec/openapi.yaml`, `spec/test-plan.md`

---

## Phase 5 — Project Scaffolding ✓

**Owner:** lead developer / tech lead  
**Contributors:** senior developers, software architect  
**Reviewers:** software architect, test architect

This phase creates the executable baseline from the canonical architecture and technical specification.

**Steps:**

1. Generate the Spring Boot project skeleton from canonical specs, then refine manually where needed.
2. Generate the Flyway V1 migration from `docs/schema.sql` as a complete-schema migration.
3. Configure jOOQ runtime and code generation.
4. Configure Spring Security, OAuth2/OIDC, bucket4j rate limiting, CORS, CSRF, and HTTP security headers.
5. Generate or refine scaffold controllers, services, repositories, DTOs, configuration classes, and frontend baseline as needed.
6. Configure CI/CD checks for build, test, security gates, SBOM generation, traceability, container build, and deployment.

**Conventions:**

- Scaffolds and Flyway V1 are derived artifacts.
- When schema or contract terminology changes, regenerate first and patch manually only if needed.
- `@Transactional` belongs on service methods only.
- Controllers and repositories are never transactional.
- Authorization is checked through `AuthorizationService` at the top of service methods.
- Do not use `@PreAuthorize`.

**Tool:** chat mode. No agents.

**Outputs:** full project skeleton in `src/main/`, generated schema artifacts, build configuration, `.github/workflows/ci.yml`

---

## Phase 6 — Agentic Coding Setup ✓

**Owner:** test architect / tech lead  
**Contributors:** software architect, lead developer  
**Reviewers:** software architect

This phase prepares controlled agent execution. The test architect owns test strategy, traceability, and test-task briefs. The tech lead owns implementation-task boundaries, sequencing, and guardrails. The same person may perform both roles, but the responsibilities remain distinct.

**Steps:**

1. Create `tasks/00-base-it.md` specifying shared test infrastructure.
2. Generate baseline `tasks/tests/*.md` files from requirements, test plan, schema, and API contracts; review and refine them in chat mode.
3. Generate baseline `tasks/impl/*.md` files from requirements, test plan, schema, API contracts, and architecture; review and refine them in chat mode.
4. Add guardrails to every task file.
5. Add CI boundary checks such as `.github/workflows/agent-guardrails.yml`.

**Conventions:**

- Test-writer agents read specifications only and must not modify `src/main/`.
- Implementation agents read failing tests and canonical docs, but must not modify `src/test/`.
- Implementation agents must not create or modify Flyway migrations.
- Backend implementation agents must not modify frontend files unless explicitly assigned a frontend task.
- Task briefs are derived artifacts; regenerate them when SRs, TEs, contracts, schema, or epic groupings change, then review before use.
- Rationale for the two-phase design: [ADR 0011](adr/0011-use-two-phase-agentic-coding.md).

**Tool:** chat mode. No agents.

**Outputs:** `tasks/00-base-it.md`, `tasks/tests/*.md`, `tasks/impl/*.md`, `.github/workflows/agent-guardrails.yml`

---

## Phase 7 — Implementation Cycles ▶

**Owner:** development team  
**Contributors:** test agents, implementation agents, lead developer  
**Reviewers:** tech lead, test architect, software architect as needed

This phase executes the two-phase implementation cycle: tests first, implementation second, with human review between agent runs.

**Steps, in order:**

| Step | Task | Constraint |
|---|---|---|
| 1 | `tasks/00-base-it.md` | Must complete before all others |
| 2 | `tasks/tests/*.md` | Parallelizable where task dependencies allow |
| 3 | dependency-critical implementation tasks | Run first when later work depends on their services |
| 4 | remaining implementation tasks | Parallelizable after prerequisites are merged |

**Between each agent run, human responsibility:**

- Review the PR diff and verify the agent only touched files within its permitted scope.
- If a test appears wrong, fix the test task file in chat mode and re-run the test agent; do not let an implementation agent fix tests.
- After each epic goes green, run the full relevant test suite to catch cross-epic regressions.
- Schema changes happen in chat mode: update `docs/schema.sql`, regenerate derived schema artifacts, and review the generated diff.

**Tool:** agents for implementation; chat mode for corrections, schema changes, and task changes.

---

## Phase 8 — Frontend Implementation ○

**Owner:** frontend lead / tech lead  
**Contributors:** frontend agents, test architect, software architect  
**Reviewers:** product owner for user-facing behavior, software architect for architecture consistency

This phase applies the same two-phase agent pattern to frontend work.

**Steps:**

1. Create `tasks/tests/1x-frontend.md` files for frontend epics, covering CT and E2E tests from the test plan.
2. Create `tasks/impl/1x-frontend.md` files for frontend epics.
3. Adapt guardrails for `src/main/frontend/` paths.
4. Run frontend test-agent and implementation-agent cycles.

**Note:** TE IDs for frontend tests are reserved in `spec/test-plan.md`. The same traceability rules apply as backend tests.

**Tool:** agents; same two-phase pattern as backend.
