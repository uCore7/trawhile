# Development process

Template for how trawhile is built and maintained. Each phase has a status, a checklist of steps, the conventions that apply, the tool to use, and the outputs it produces. Use this as a guide when starting or resuming a phase.

**Status legend:** ✓ complete · ▶ in progress · ○ not started

---

## Phase 1 — Requirements engineering ✓

**Steps:**
1. Identify all stakeholders and document the system boundary and context boundary
2. Write `docs/glossary.md` — canonical definitions of all domain terms
3. Write user requirements (URs) in IREB format with goal rationale; include key invariants as a closing section
4. Derive system requirements (SRs) from URs with full traceability
5. Design the PostgreSQL schema (`docs/schema.sql`) — evaluate every data field against the GDPR necessity principle before adding it
6. Author the REST API contract (`docs/openapi.yaml`)

**Conventions:**
Follow the IREB standard throughout. Produce the typical IREB results: stakeholder analysis, system context, glossary, and a requirements document containing URs and SRs with full traceability. Project-specific notes:
- No user story format ("As a user…") — rationale in `docs/decisions.md`
- Every SR cites its parent UR in the rationale field; a single UR may yield multiple SRs

**Requirement types:**
Both URs and SRs belong to one of three types. The type of a UR and the type of its derived SRs are independent — a UR of any type may derive SRs of any type.

UR rules:

| UR type | Meaning | Must have SR children? |
|---|---|---|
| Functional (`F`) | A stakeholder capability | Yes (≥ 1 SR of any type) |
| Quality (`Q`) | A non-functional property required by a stakeholder | Yes (≥ 1 SR of any type) |
| Constraint (`C`) | A design-time condition at stakeholder level (GDPR, CRA, architectural decisions); satisfied by construction, verified by review | Optional (0 or more SRs of any type) |

SR rules:

| SR type | Meaning | Must have TEs? |
|---|---|---|
| Functional (`F`) | A system behaviour that implements a stakeholder capability | Yes (≥ 1) |
| Quality (`Q`) | A system property that realises a non-functional requirement | Yes (≥ 1) |
| Constraint (`C`) | A technical constraint at system level; satisfied by construction, verified by review | No |

**Identifier schemes:**
- `ST-1`, `ST-2`, … — stakeholders
- `UR-F012`, `UR-Q003`, `UR-C007` — user requirements; type-prefixed three-digit zero-padded sequential; no letter suffixes; each type has its own sequence
- `SR-F012.F01`, `SR-F012.Q02`, `SR-F012.C03` — system requirements; format is `SR-{parent-UR-type}{parent-UR-number}.{SR-own-type}{nn}`; the left part (`F012`) identifies the parent UR; the right part is a type qualifier (`F`, `Q`, or `C`) plus a two-digit zero-padded sequence number that is shared across all SR children of the same parent UR (i.e. `F01`, `Q02`, `C03` … form one sequence — the type letter is a qualifier, not a separate sub-sequence)
- `TE-F012.F01-01`, `TE-F012.Q02-01` — test cases; format is `TE-{SR-id}-{nn}`; two-digit zero-padded sequence within each SR; only SR-F and SR-Q have TEs; SR-C has none
- Named external regulations (GDPR, CRA, OWASP) are cited in the rationale field of the UR or SR, never used as standalone identifiers

**Traceability chain:**

```
UR-F012  →  SR-F012.F01, SR-F012.Q02, SR-F012.C03, …  →  TE-F012.F01-01, …; TE-F012.Q02-01, …; (none for C)
UR-Q003  →  SR-Q003.F01, SR-Q003.Q02, …               →  TE-Q003.F01-01, …; TE-Q003.Q02-01, …
UR-C007  →  SR-C007.C01, …  (optional)                 →  (no TEs)
UR-C007  →  (nothing)                                   →  (no TEs)
```

Every SR of type F or Q must have at least one TE, regardless of the parent UR's type. SR-C entries never have TEs. A UR-F or UR-Q must produce at least one SR of any type. A UR-C may produce zero SRs.

**ID sequence rule:** Each ID sequence (UR-F, UR-Q, UR-C, ST) is strictly increasing. New IDs must be higher than all currently assigned IDs in the same sequence. Gaps must not be filled and retired IDs must not be reused. SR and TE sequences are subordinate to their parent UR and restart per parent.

**Prerequisite before renaming:** Classify all URs as F, Q, or C first. Then derive SR and TE IDs. URs drive the numbering; SRs and TEs follow.

**Tool:** Chat mode. No agents.

**Outputs:** `docs/requirements-ur.md`, `docs/requirements-sr.md`, `docs/schema.sql`, `docs/openapi.yaml`, `docs/glossary.md`

---

## Phase 2 — Architecture & scaffolding ✓

**Steps:**
- Finalise the stack and record the rationale in `docs/decisions.md`
- Create the Spring Boot project skeleton with all packages and scaffolded controllers/services
- Write the Flyway V1 migration containing the complete schema (not incremental)
- Configure Spring Security (OAuth2/OIDC), bucket4j rate limiting, CORS, CSRF, HTTP security headers
- Set up the CI/CD pipeline (GitHub Actions): build, test, SpotBugs + Find Security Bugs, OWASP Dependency Check, CycloneDX SBOM, Docker image, SSH deploy
- Write `docs/architecture.md` and `CLAUDE.md`

**Conventions:**
- `docs/schema.sql` is authoritative; the Flyway V1 migration is generated from it
- Schema changes go through chat mode and update both `docs/schema.sql` and a new numbered migration file in the same commit — never via agents
- `@Transactional` on service methods only; never on controllers or repositories
- Authorization checked via `AuthorizationService` at the top of every service method; no `@PreAuthorize`

**Tool:** Chat mode. No agents.

**Outputs:** Full project skeleton in `src/main/`, `CLAUDE.md`, `AGENTS.md`, `docs/architecture.md`, `.github/workflows/ci.yml`

---

## Phase 3 — Test planning ✓

**Steps:**
- For every SR-F and SR-Q, specify at least one happy-path test and one error-path test
- Assign each test a type: IT (Testcontainers + real DB), UT (plain JUnit 5), SIT (MockMvc, no DB), CT (Angular TestBed), E2E (Playwright)
- Produce the traceability matrix UR → SR → TE in `docs/test-plan.md`
- Defer frontend tests (CT, E2E) with IDs reserved

**Conventions:**
- Every SR-F and SR-Q has at least one TE. SR-C entries have no TEs.
- TE IDs are `TE-{SR-id}-{nn}` matching the parent SR exactly (e.g., `TE-F012.F01-01`, `TE-F012.Q01-02`). When an SR is renumbered, its TEs are renumbered to match in the same commit. Retired TEs are replaced by a one-line tombstone row in the test plan table.
- `@Tag("TE-F012.F01-01")` on every test method for CI filtering and traceability

**Tool:** Chat mode. No agents.

**Outputs:** `docs/test-plan.md`

---

## Phase 4 — Agentic coding setup ✓

**Steps:**
- Create `AGENTS.md` as a tool-agnostic entry point
- Create `tasks/00-base-it.md` specifying the shared test infrastructure
- For each epic, create `tasks/tests/NN-epicN.md` (test-writer agent brief)
- For each epic, create `tasks/impl/NN-epicN.md` (implementation agent brief)
- Add guardrails to every task file (no git writes, file-path boundaries, no migrations)
- Add CI boundary check (`.github/workflows/agent-guardrails.yml`)

**Conventions:**
- Test-writer agents: read spec only; no access to `src/main/`; produce failing tests
- Implementation agents: read failing tests; no access to `src/test/`; no Flyway migrations; no frontend files
- Rationale for the two-phase design: `docs/decisions.md`

**Tool:** Chat mode. No agents.

**Outputs:** `AGENTS.md`, `tasks/00-base-it.md`, `tasks/tests/01–10.md`, `tasks/impl/01–10.md`, `.github/workflows/agent-guardrails.yml`

---

## Phase 5 — Agent execution ▶

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

## Phase 6 — Frontend ○

**Steps:**
- Create `tasks/tests/1x-frontend.md` for each frontend epic — CT and E2E tests from spec
- Create `tasks/impl/1x-frontend.md` for each frontend epic — implement Angular components to pass the tests
- Adapt guardrails for `src/main/frontend/` paths

**Note:** TE IDs for frontend tests are already reserved in `docs/test-plan.md`. The same two-phase agent pattern applies as in phases 4–5.

**Tool:** Agents; same two-phase pattern as backend.
