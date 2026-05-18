# Development process

This document defines how trawhile is specified, architected, implemented, and maintained. It separates requirements engineering, architecture, system requirements, technical specification, scaffolding, and implementation cycles so each artifact has a clear owner and purpose.

The rules and conventions set out below are the default discipline for unattended agent work. They may be overridden by the chat-mode user when a deliberate exception is needed.

**Status legend:** ✓ complete · ▶ in progress · ○ not started

---

## Source and Artifact Policy

The process distinguishes between **canonical sources** and **derived artifacts**.

**Canonical sources** are edited directly in chat mode and reviewed as the source of truth:

- `docs/requirements-ur.md` — user requirements, stakeholders, system/context boundary, customer-side constraints, and canonical terminology (Glossary section)
- `docs/architecture.md` — arc42 architecture overview
- `docs/adr/` — Architecture Decision Records
- `docs/devsecops.md` — build, verification, and delivery pipeline
- `docs/requirements-sr.md` — system requirements
- `spec/schema.sql` — canonical PostgreSQL schema
- `CLAUDE.md` and `AGENTS.md` — collaboration and agent guardrails

**Derived artifacts** are regenerated from canonical sources whenever identifiers, terminology, contracts, schema, or epic structure change:

- `spec/openapi.yaml`
- Flyway V1 migration
- jOOQ code-generation schema inputs, for example `src/main/resources/db/codegen/jooq-schema.sql`
- scaffold classes/interfaces and placeholder controllers/services
- `spec/test-plan.md` baseline rows and ID structure
- `tasks/tests/*.md`, `tasks/impl/*.md`, and `tasks/cleanup/*.md` scaffolds

**Rule of preference:** regeneration is preferred over manual patching for derived artifacts. Manual patching is allowed only for small exceptions, generator defects, or deliberate human refinements that are later folded back into generator logic.

**Acceptance rule:** generated output is never accepted blindly. Every regeneration step requires human review before it becomes the new baseline.

**Schema rule for jOOQ:** `spec/schema.sql` remains the single canonical database schema. Runtime database objects needed by the application or generated query code, including PostgreSQL authorization helpers, must be defined there first. The Flyway V1 migration and any jOOQ-specific schema input file are derived artifacts. They must not become independent hand-maintained schema sources.

**Schema regeneration rule:** when `spec/schema.sql` changes, regenerate derived schema artifacts via repository scripts rather than patching them manually:

- Flyway V1 migration: `./scripts/generate-schema-v1.sh`
- jOOQ schema input subset, if present: `./scripts/generate-jooq-schema.sh`

Review regenerated output before accepting it, but do not treat derived files as a second place to edit schema definitions.

**ADR rule:** ADRs are append-only decision records. If a decision changes, create a new ADR with the next number and mark the previous ADR as superseded. Do not rewrite history to make an old decision look current. These guarantees — no deletion, no renumbering, no reuse of retired numbers — apply once `docs/adr/` is first committed; before that baseline the draft corpus may still be restructured.

**UML note:** Problem-space UML diagrams (use cases, lifecycle state diagrams) live inline in `docs/requirements-ur.md` as fenced PlantUML blocks. Solution-space diagrams (sequences, deployment, building blocks) live inline in `docs/architecture.md`. They are not canonical sources separate from the document they appear in; they are maintained in chat mode as communication artifacts whose meaning is governed by the surrounding text.

---

## Phase 1 — Requirements Engineering ✓

**Owner:** requirements engineer / product owner  
**Contributors:** domain stakeholders, software architect  
**Reviewers:** software architect, domain stakeholders

This phase captures the problem space and stakeholder intent. It includes customer, user, regulatory, neighboring-system, and context-specific constraints.

**Steps:**

1. Identify all stakeholders.
2. Document the system boundary and context boundary.
3. Write the Glossary section of `docs/requirements-ur.md` with canonical domain terms.
4. Write user requirements (URs) in IREB format with goal rationale.
5. Record customer-side constraints, including standards, regulations, neighboring systems, and context-specific conventions.
6. Document key invariants.
7. Maintain problem-space UML diagrams inline in `docs/requirements-ur.md` (use cases, lifecycle state diagrams, context views).

**Conventions:**

Follow the IREB standard for requirements engineering artifacts. Produce the typical IREB results: stakeholder analysis, system context, glossary, user requirements, and constraints.

Do not use user stories as the canonical requirements format. Rationale: user stories suit elicitation but are not precise enough as a canonical documentation format, and agentic coding raises the need for explicit, verifiable, and traceable requirements because agents must independently determine whether a requirement is satisfied.

**Requirement types (URs):**

| UR type | Meaning | Must have SR children? |
|---|---|---|
| Functional (`F`) | A stakeholder capability | Yes, at least one SR of any type |
| Quality (`Q`) | A non-functional property required by a stakeholder | Yes, at least one SR of any type |
| Constraint (`C`) | A stakeholder-level constraint, such as GDPR, CRA, standards, or customer policy; satisfied by construction and verified by review | Optional |

**Identifier schemes:**

- `ST-1`, `ST-2`, ... — stakeholders
- `G-1`, `G-2`, ... — goals
- `E-00`, `E-01`, ... — epics; two-digit, zero-padded. `E-00` is reserved for cross-cutting concerns (currently the constraints bucket).
- `UR-NN-Tnn` — user requirements. `NN` is the two-digit epic number, `T` is the type (`F` functional, `Q` quality, `C` constraint), `nn` is a two-digit per-epic-per-type sequence. Example: `UR-03-F12` is the 12th F-type UR in epic `E-03`; `UR-00-C09` is the 9th constraint.
- Named external regulations such as GDPR, CRA, and OWASP are cited in rationale fields, not used as standalone identifiers.

**ID sequence rule:** within each (epic, type) bucket the UR sequence is strictly increasing. New IDs must be higher than all currently assigned IDs in the same bucket. Gaps must not be filled and retired IDs must not be reused. The same discipline applies to `ST`, `G`, and `E` sequences. Per the override clause in the document introduction, the chat-mode user may overrule this when restructuring requires it.

**Tool:** chat mode. No agents.

**Outputs:** `docs/requirements-ur.md` (including the Glossary section and inline UML diagrams)

---

## Phase 2 — Architecture Elaboration ✓

**Owner:** software architect  
**Essential contributor:** lead developer / tech lead  
**Contributors:** operations, security/privacy reviewers, requirements engineer  
**Reviewers:** lead developer, product owner where architectural choices affect stakeholder-visible behavior

This phase elaborates the solution architecture. It includes technical constraints, organizational constraints, architecture drivers, solution strategy, cross-cutting concepts, and architectural decisions.

**Incremental architecture loop:**

Architecture work is incremental.

Constraints and decisions are distinct. Chapter 2 records upstream constraints that the architecture must respect: requirements, regulations, neighboring systems, deployment policy, and fixed platform or environment constraints. Accepted ADR outcomes are not promoted to chapter 2; they are folded into the relevant descriptive architecture chapters.

Use ATAM-style review to evaluate the architecture against quality scenarios. Its direct outputs are risks, non-risks, sensitivity points, trade-off points, and scenario findings. These findings may imply that new architectural decisions are needed or that existing decisions must change.

When an ATAM finding shows that the architecture needs a choice between alternatives, record that choice as an ADR. After the ADR is accepted, update the relevant arc42 chapters so they describe the new target architecture. Keep rationale and alternative analysis in the ADR. Use chapter 11 to record risks, technical debt, sensitivity points, and trade-offs that remain movable or require follow-up.

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
- Architecture-to-ADR traceability is intentionally lightweight. Descriptive chapters may link to ADRs when they name an important trade-off, real-world example, or architectural exception. The ADR title and first Context sentence (`This decision answers: ...`) identify the architectural concern addressed. Do not maintain an architecture-to-ADR matrix unless a concrete review need emerges.

**ADR conventions:**

- Use `docs/adr/NNNN-short-kebab-title.md`.
- The ADR file name and title should state the question or architectural concern addressed, not the chosen solution. State the selected option in the `Decision` section.
- The `Context` section should list the viable alternatives neutrally and explain their relevant advantages, drawbacks, and trade-offs before the `Decision` section selects one.
- Use four-digit, monotonically increasing numbers.
- Never renumber a committed ADR.
- Use Nygard sections: `Status`, `Context`, `Decision`, `Consequences`.
- Do not add separate `Date:` or `Realizes:` metadata headers.
- The `Status` section is a dated status history, for example `- Proposed, 2026-05-10` followed later by `- Accepted, 2026-05-12`.
- Within one uncommitted change set, update the last status-history entry instead of appending repeated entries for the same lifecycle step.
- Valid statuses: `Proposed`, `Accepted`, `Rejected`, `Deprecated`, `Superseded`.
- When replacing a decision, create a new ADR and mark the old one as superseded.

**arc42 conventions:**

- `docs/architecture.md` follows arc42 chapters 1-12.
- Chapters overlapping with canonical requirements should refer to the relevant requirements document instead of duplicating it.
- Write `docs/architecture.md` for all key stakeholders. Prefer plain architectural language and explain the stakeholder-visible effect of technical choices. Use implementation terms only when they name an actual architectural element, building block, or constraint.
- **Treat arc42 chapters as a downstream chain.** Each chapter takes every lower-numbered chapter as authoritative. State only the decisions made at that chapter's own level; for anything established earlier, refer to the lower-numbered chapter instead of restating or re-deriving it. (Example: chapter 4 does not restate the platform constraints owned by chapter 2.) This is the intra-document form of the canonical-source / derived-artifact rule.
- Chapter 4, Solution Strategy, should state the fundamental architectural strategy and the important trade-offs being made at that strategic level. Detailed rationale and alternative analysis belong in ADRs.
- `docs/architecture.md` may name important trade-offs, but should not reproduce full alternative analysis. ADRs are the place for neutral alternatives, rationale, and consequences.
- Chapter 12 refers to the Glossary section of `docs/requirements-ur.md`.

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
2. Verify traceability: every F-type and Q-type UR has at least one SR child.
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

- `SR-NN-Tnn.Tnn` — system requirements
- Format: `SR-{epic}-{parent-UR-type}{parent-UR-seq}.{SR-own-type}{nn}`
- The first segment (`NN-Tnn`) identifies the parent UR (matching `UR-NN-Tnn`).
- The second segment (`.Tnn`) is the SR's own type qualifier plus a two-digit, zero-padded sequence number shared across all SR children of the same parent UR.

Example:

```text
UR-03-F12 -> SR-03-F12.F01, SR-03-F12.Q02, SR-03-F12.C03
UR-03-Q03 -> SR-03-Q03.F01, SR-03-Q03.Q02
UR-00-C07 -> SR-00-C07.C01, or no SR if the constraint is satisfied directly by construction
```

Every F-type or Q-type SR must later have at least one TE, regardless of the parent UR type. C-type SRs never have TEs. An F-type or Q-type UR must produce at least one SR of any type. A C-type UR may produce zero SRs.

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

1. Design the PostgreSQL schema in `spec/schema.sql`.
2. Evaluate every data field against the GDPR necessity principle before adding it.
3. Generate the REST API contract (`spec/openapi.yaml`) from the canonical requirements and schema, then review and refine it.
4. For every F-type and Q-type SR, specify at least one happy-path test and one error-path test.
5. Assign each test a type: IT, UT, SIT, CT, or E2E.
6. Generate the initial traceability matrix UR -> SR -> TE in `spec/test-plan.md`, then review and enrich it manually.

**Schema conventions:**

- `spec/schema.sql` is authoritative.
- Runtime database objects required by application code or generated query code belong in `spec/schema.sql` first.
- Flyway V1 migration and jOOQ schema inputs are derived from `spec/schema.sql`.
- Do not hand-maintain derived schema artifacts as alternate schema sources.

**Test and traceability conventions:**

- Formal traceability is required for the requirements-to-test chain: UR -> SR -> TE.
- Every F-type and Q-type SR has at least one TE.
- C-type SRs have no TEs.
- TE IDs are `TE-{SR-id}-{nn}` matching the parent SR exactly, for example `TE-03-F12.F01-01`.
- TE sequence is two-digit, zero-padded, and restarts within each SR.
- When an SR is renumbered, its TEs are renumbered to match in the same change.
- Retired TEs are replaced by a one-line tombstone row in the test plan table.
- `@Tag("TE-03-F12.F01-01")` appears on every backend test method for CI filtering and traceability.
- Baseline matrix rows may be generated from SR IDs; test intent, path selection, and edge-case coverage remain a human review responsibility.

**Traceability chain:**

```text
UR-03-F12 -> SR-03-F12.F01, SR-03-F12.Q02, SR-03-F12.C03
          -> TE-03-F12.F01-01, TE-03-F12.Q02-01, none for C-type SR
```

**Tool:** chat mode. No agents.

**Outputs:** `spec/schema.sql`, `spec/openapi.yaml`, `spec/test-plan.md`

---

## Phase 5 — Project Scaffolding ✓

**Owner:** lead developer / tech lead  
**Contributors:** senior developers, software architect  
**Reviewers:** software architect, test architect

This phase creates the executable baseline from the canonical architecture and technical specification.

**Steps:**

1. Generate the Spring Boot project skeleton from canonical specs, then refine manually where needed.
2. Generate the Flyway V1 migration from `spec/schema.sql` as a complete-schema migration.
3. Configure jOOQ runtime and code generation.
4. Configure Spring Security, OAuth2/OIDC, Caddy edge abuse controls, CORS, CSRF, and HTTP security headers.
5. Generate or refine scaffold controllers, services, repositories, DTOs, configuration classes, and frontend baseline as needed.
6. Configure CI/CD checks for build, test, security gates, SBOM generation, traceability, container build, and deployment.

**Conventions:**

- Scaffolds and Flyway V1 are derived artifacts.
- When schema or contract terminology changes, regenerate first and patch manually only if needed.
- `@Transactional` belongs on service methods only.
- Controllers and persistence components are never transactional.
- External-actor operations check authorization through `AuthorizationService` at the top of service methods.
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
4. Create `tasks/cleanup/*.md` files for implementation alignment work discovered during architecture or requirements review; review and refine them in chat mode.
5. Add guardrails to every task file.
6. Add CI boundary checks such as `.github/workflows/agent-guardrails.yml`.

**Conventions:**

- Test-writer agents read specifications only and must not modify `src/main/`.
- Implementation agents read failing tests and canonical docs, but must not modify `src/test/`.
- Implementation agents must not create or modify Flyway migrations.
- Backend implementation agents must not modify frontend files unless explicitly assigned a frontend task.
- Task briefs are derived artifacts; regenerate or update them when SRs, TEs, contracts, schema, architecture decisions, cleanup findings, or epic groupings change, then review before use.
- Rationale for the two-phase design: when one agent writes both tests and implementation in a single pass, the tests can drift toward the implementation just produced; tests must be derived from specifications, not from production code.

**Tool:** chat mode. No agents.

**Outputs:** `tasks/00-base-it.md`, `tasks/tests/*.md`, `tasks/impl/*.md`, `tasks/cleanup/*.md`, `.github/workflows/agent-guardrails.yml`

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
| 5 | `tasks/cleanup/*.md` | Run when the corresponding ADR/spec cleanup is accepted and prerequisite implementation work is in place |

**Between each agent run, human responsibility:**

- Review the PR diff and verify the agent only touched files within its permitted scope.
- If a test appears wrong, fix the test task file in chat mode and re-run the test agent; do not let an implementation agent fix tests.
- After each epic goes green, run the full relevant test suite to catch cross-epic regressions.
- Schema changes happen in chat mode: update `spec/schema.sql`, regenerate derived schema artifacts, and review the generated diff.

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
