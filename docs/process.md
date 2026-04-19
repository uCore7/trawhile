# Development process

Template for how trawhile is built and maintained. Each phase has a status, a checklist of steps, the conventions that apply, the tool to use, and the outputs it produces. Use this as a guide when starting or resuming a phase.

**Status legend:** ✓ complete · ▶ in progress · ○ not started

---

## Derived artifacts policy

The process distinguishes between **canonical sources** and **derived artifacts**.

**Canonical sources** are edited directly in chat mode and reviewed as the source of truth:
- `docs/requirements-ur.md`
- `docs/requirements-sr.md`
- `docs/glossary.md`
- `docs/schema.sql`
- `CLAUDE.md`
- `AGENTS.md`
- narrative design documents such as `docs/decisions.md` and `docs/architecture.md`

**Derived artifacts** are regenerated from canonical sources whenever identifiers, terminology, contracts, or epic structure change:
- `spec/openapi.yaml`
- Flyway V1 migration
- scaffold classes/interfaces and placeholder controllers/services
- `spec/test-plan.md` baseline rows and ID structure
- `tasks/tests/*.md` and `tasks/impl/*.md` scaffolds

**Rule of preference:** regeneration is preferred over manual patching for derived artifacts. Manual patching is allowed only for small exceptions, generator defects, or deliberate human refinements that are then folded back into the generator logic later.

**Acceptance rule:** generated output is never accepted blindly. Every regeneration step requires human review before it becomes the new baseline.

**UML note:** UML files in `docs/uml/` are not canonical sources and are not treated as fully generated artifacts. They are maintained in chat mode as consistency and communication artifacts derived from the canonical documents, with human judgment deciding what to visualise and how much detail to include.

---

## Phase 1 — Problem space ✓

**Steps:**
1. Identify all stakeholders and document the system boundary and context boundary
2. Write `docs/glossary.md` — canonical definitions of all domain terms
3. Write user requirements (URs) in IREB format with goal rationale; include key invariants as a closing section
4. Maintain `docs/uml/` — consistency and communication diagrams derived from the domain model

**Conventions:**
Follow the IREB standard throughout. Produce the typical IREB results: stakeholder analysis, system context, glossary, and URs. Project-specific notes:
- No user story format ("As a user…") — rationale in `docs/decisions.md`

**Requirement types (URs):**

| UR type | Meaning | Must have SR children? |
|---|---|---|
| Functional (`F`) | A stakeholder capability | Yes (≥ 1 SR of any type) |
| Quality (`Q`) | A non-functional property required by a stakeholder | Yes (≥ 1 SR of any type) |
| Constraint (`C`) | A design-time condition at stakeholder level (GDPR, CRA, architectural decisions); satisfied by construction, verified by review | Optional (0 or more SRs of any type) |

**Identifier schemes:**
- `ST-1`, `ST-2`, … — stakeholders
- `UR-F012`, `UR-Q003`, `UR-C007` — user requirements; type-prefixed three-digit zero-padded sequential; no letter suffixes; each type has its own sequence
- Named external regulations (GDPR, CRA, OWASP) are cited in the rationale field of the UR or SR, never used as standalone identifiers

**ID sequence rule:** Each ID sequence (UR-F, UR-Q, UR-C, ST) is strictly increasing. New IDs must be higher than all currently assigned IDs in the same sequence. Gaps must not be filled and retired IDs must not be reused.

**Tool:** Chat mode. No agents.

**Outputs:** `docs/requirements-ur.md`, `docs/glossary.md`, `docs/uml/`

---

## Phase 2 — Solution space ✓

**Steps:**
1. Derive system requirements (SRs) from URs with full traceability
2. Verify traceability: every UR-F and UR-Q has at least one SR child; every SR cites its parent UR
3. Design the PostgreSQL schema (`docs/schema.sql`) — evaluate every data field against the GDPR necessity principle before adding it
4. Write `CLAUDE.md` and `AGENTS.md` — the manually controlled collaboration and agent guardrail documents for the repository

**Conventions:**
- Every SR cites its parent UR in the rationale field; a single UR may yield multiple SRs
- The type of a UR and the type of its derived SRs are independent — a UR of any type may derive SRs of any type

**Requirement types (SRs):**

| SR type | Meaning | Must have TEs? |
|---|---|---|
| Functional (`F`) | A system behaviour that implements a stakeholder capability | Yes (≥ 1) |
| Quality (`Q`) | A system property that realises a non-functional requirement | Yes (≥ 1) |
| Constraint (`C`) | A technical constraint at system level; satisfied by construction, verified by review | No |

**Identifier schemes:**
- `SR-F012.F01`, `SR-F012.Q02`, `SR-F012.C03` — system requirements; format is `SR-{parent-UR-type}{parent-UR-number}.{SR-own-type}{nn}`; the left part (`F012`) identifies the parent UR; the right part is a type qualifier (`F`, `Q`, or `C`) plus a two-digit zero-padded sequence number that is shared across all SR children of the same parent UR (i.e. `F01`, `Q02`, `C03` … form one sequence — the type letter is a qualifier, not a separate sub-sequence)
- `TE-F012.F01-01`, `TE-F012.Q02-01` — test cases; format is `TE-{SR-id}-{nn}`; two-digit zero-padded sequence within each SR; only SR-F and SR-Q have TEs; SR-C has none

**Traceability chain:**

```
UR-F012  →  SR-F012.F01, SR-F012.Q02, SR-F012.C03, …  →  TE-F012.F01-01, …; TE-F012.Q02-01, …; (none for C)
UR-Q003  →  SR-Q003.F01, SR-Q003.Q02, …               →  TE-Q003.F01-01, …; TE-Q003.Q02-01, …
UR-C007  →  SR-C007.C01, …  (optional)                 →  (no TEs)
UR-C007  →  (nothing)                                   →  (no TEs)
```

Every SR of type F or Q must have at least one TE, regardless of the parent UR's type. SR-C entries never have TEs. A UR-F or UR-Q must produce at least one SR of any type. A UR-C may produce zero SRs.

**ID sequence rule:** SR and TE sequences are subordinate to their parent UR and restart per parent.

**Prerequisite before renaming:** Classify all URs as F, Q, or C first. Then derive SR and TE IDs. URs drive the numbering; SRs and TEs follow.

**Tool:** Chat mode. No agents.

**Outputs:** `docs/requirements-sr.md`, `docs/schema.sql`, `CLAUDE.md`, `AGENTS.md`

---

## Phase 3 — Architecture decisions ✓

**Steps:**
1. Write `docs/architecture.md` — the high-level system architecture, package layout, and key technical patterns derived from the requirements and schema
2. Finalise the stack choices and record the rationale in `docs/decisions.md`

**Tool:** Chat mode. No agents.

**Outputs:** `docs/architecture.md`, `docs/decisions.md`

---

## Phase 4 — API contract & test plan ✓

**Steps:**
1. Generate the REST API contract (`spec/openapi.yaml`) from the canonical requirements and schema, then review and refine it as needed
2. For every SR-F and SR-Q, specify at least one happy-path test and one error-path test
3. Assign each test a type: IT (Testcontainers + real DB), UT (plain JUnit 5), SIT (MockMvc, no DB), CT (Angular TestBed), E2E (Playwright)
4. Generate the initial traceability matrix UR → SR → TE in `spec/test-plan.md` from `docs/requirements-sr.md`, then review and enrich it manually
5. Defer frontend tests (CT, E2E) with IDs reserved
6. Set up the CI/CD pipeline (GitHub Actions): build, test, SpotBugs + Find Security Bugs, OWASP Dependency Check, CycloneDX SBOM, Docker image, SSH deploy

**Conventions:**
- Every SR-F and SR-Q has at least one TE. SR-C entries have no TEs.
- TE IDs and baseline matrix rows should be generator-produced from SR IDs where possible; test intent, path selection, and edge-case coverage remain a human review responsibility
- TE IDs are `TE-{SR-id}-{nn}` matching the parent SR exactly (e.g., `TE-F012.F01-01`, `TE-F012.Q01-02`). When an SR is renumbered, its TEs are renumbered to match in the same commit. Retired TEs are replaced by a one-line tombstone row in the test plan table.
- `@Tag("TE-F012.F01-01")` on every test method for CI filtering and traceability

**Tool:** Chat mode. No agents.

**Outputs:** `spec/openapi.yaml`, `spec/test-plan.md`, `.github/workflows/ci.yml`

---

## Phase 5 — Project scaffolding ✓

**Steps:**
1. Generate the Spring Boot project skeleton from canonical specs, then refine manually where needed
2. Generate the Flyway V1 migration from `docs/schema.sql` as a complete-schema migration (not incremental)
3. Configure Spring Security (OAuth2/OIDC), bucket4j rate limiting, CORS, CSRF, HTTP security headers

**Conventions:**
- `docs/schema.sql` is authoritative; the Flyway V1 migration is generated from it
- Scaffolds and the Flyway V1 migration are derived artifacts; when schema or contract terminology changes, regenerate first and patch manually only if needed
- Schema changes go through chat mode and update both `docs/schema.sql` and a new numbered migration file in the same commit — never via agents
- `@Transactional` on service methods only; never on controllers or repositories
- Authorization checked via `AuthorizationService` at the top of every service method; no `@PreAuthorize`

**Tool:** Chat mode. No agents.

**Outputs:** full project skeleton in `src/main/`

---

## Phase 6 — Agentic coding setup ✓

**Steps:**
1. Create `tasks/00-base-it.md` specifying the shared test infrastructure
2. Generate baseline `tasks/tests/NN-epicN.md` files from the requirements, test plan, and contracts; then review and refine them in chat mode
3. Generate baseline `tasks/impl/NN-epicN.md` files from the requirements, test plan, and contracts; then review and refine them in chat mode
4. Add guardrails to every task file (no git writes, file-path boundaries, no migrations)
5. Add CI boundary check (`.github/workflows/agent-guardrails.yml`)

**Conventions:**
- Test-writer agents: read spec only; no access to `src/main/`; produce failing tests
- Implementation agents: read failing tests; no access to `src/test/`; no Flyway migrations; no frontend files
- Task briefs are derived artifacts; regenerate them when SRs, TEs, contracts, or epic groupings change, then review the result before use
- Rationale for the two-phase design: `docs/decisions.md`

**Tool:** Chat mode. No agents.

**Outputs:** `tasks/00-base-it.md`, `tasks/tests/01–10.md`, `tasks/impl/01–10.md`, `.github/workflows/agent-guardrails.yml`

---

## Phase 7 — Agent execution ▶

**Steps, in order:**

| Step | Task | Constraint |
|---|---|---|
| 1 | `tasks/00-base-it.md` | Must complete before all others |
| 2 | `tasks/tests/01–10.md` | Parallelisable in any order |
| 3 | `tasks/impl/07-epic7.md` | Run first — `SecurityEventService.log()` is used by impl/01, 06, 09, 10 |
| 4 | `tasks/impl/01-epic1.md` | Run second — `UserService.scrubUser()` is used by impl/06 |
| 5 | `tasks/impl/02–06, 08–10` | Parallelisable after steps 3 and 4 are merged |

**Between each agent run (human responsibility):**
- Review the PR diff — verify the agent only touched files within its permitted scope
- If a test appears wrong: fix the test task file in chat mode, re-run the test agent — never let an impl agent fix a test
- After each epic goes green: run the full test suite to catch cross-epic regressions
- Schema changes: always in chat mode; update `docs/schema.sql` and a new migration in the same commit

**Tool:** Agents for implementation; chat mode for corrections and schema changes.

---

## Phase 8 — Frontend ○

**Steps:**
1. Create `tasks/tests/1x-frontend.md` for each frontend epic — CT and E2E tests from spec
2. Create `tasks/impl/1x-frontend.md` for each frontend epic — implement Angular components to pass the tests
3. Adapt guardrails for `src/main/frontend/` paths

**Note:** TE IDs for frontend tests are already reserved in `spec/test-plan.md`. The same two-phase agent pattern applies as in phases 6–7.

**Tool:** Agents; same two-phase pattern as backend.
