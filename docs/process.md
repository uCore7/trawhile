# Development process

Template for how trawhile is built and maintained. Each phase has a status, a checklist of steps, the conventions that apply, the tool to use, and the outputs it produces. Use this as a guide when starting or resuming a phase.

**Status legend:** ✓ complete · ▶ in progress · ○ not started

---

## Phase 1 — Requirements engineering ✓

**Steps:**
1. Identify all stakeholders and document the system boundary and context boundary
2. Write `docs/glossary.md` — canonical definitions of all domain terms
3. Write `docs/epics.md` — role model, system invariants, and feature descriptions (F-codes) grouped by epic
4. Write user requirements (URs) in IREB format with goal rationale
5. Derive system requirements (SRs) from URs with full traceability
6. Design the PostgreSQL schema (`docs/schema.sql`) — evaluate every data field against the GDPR necessity principle before adding it
7. Author the REST API contract (`docs/openapi.yaml`)

**Conventions:**
Follow the IREB standard throughout. Produce the typical IREB results: stakeholder analysis, system context, glossary, and a requirements document containing URs and SRs with full traceability. Project-specific notes:
- Every SR traces to exactly one UR or named external constraint (GDPR, CRA, OWASP)
- No user story format ("As a user…") — rationale in `docs/decisions.md`

**Identifier schemes:**
- `ST-1`, `ST-2`, … — stakeholders; defined in `docs/requirements-ur.md`
- `UR-001`, `UR-002`, … — user requirements; three-digit sequential
- `SR-001`, `SR-007a`, `SR-064b` — system requirements; optional lowercase letter suffix for sub-requirements; letters uppercased in derived IDs (e.g., `TE-064B-01`)
- `F1.0`, `F1.1`, `F9.6` — feature codes in `docs/epics.md`; first digit is the epic number
- `C-1`, `C-2`, … — named constraints referenced by SRs (e.g., C-2: no email stored)
- `TE-{SR}-{nn}` — test case IDs; defined in Phase 3

**Tool:** Chat mode. No agents.

**Outputs:** `docs/requirements-ur.md`, `docs/requirements-sr.md`, `docs/schema.sql`, `docs/openapi.yaml`, `docs/glossary.md`, `docs/epics.md`

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
- Define test ID scheme: `TE-{SR}-{nn}` (e.g., TE-028-03, TE-057A-02)
- For every SR, specify at least one happy-path test and one error-path test
- Assign each test a type: IT (Testcontainers + real DB), UT (plain JUnit 5), SIT (MockMvc, no DB), CT (Angular TestBed), E2E (Playwright)
- Produce the traceability matrix UR → SR → TE in `docs/test-plan.md`
- Defer frontend tests (CT, E2E) with IDs reserved

**Conventions:**
- Every SR has at least one TE
- TE IDs are stable — never renumber; retire with a comment if a test is removed
- `@Tag("TE-xxx-nn")` on every test method for CI filtering and traceability

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
