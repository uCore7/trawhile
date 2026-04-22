# Key decisions

Each entry records a non-obvious choice, the alternatives considered, and why the chosen option was preferred. This context is not derivable from the code alone.

---

## Spring Data JDBC over JPA or JOOQ

**Chosen:** Spring Data JDBC + plain SQL  
**Rejected:**
- JPA/Hibernate — hides SQL; lazy-loading surprises; mapping complexity for recursive queries; N+1 problem risks
- JOOQ — type-safe SQL is appealing but requires build-time codegen, heavier dependency, and complicates the agentic coding setup

**Reason:** Spring Data JDBC is explicit about SQL without ceremony. The recursive CTEs required by the authorization model (Q1–Q4 in `docs/schema.sql`) are plain SQL strings — readable, testable, and straightforward for agents to produce.

**Security implication:** Choosing Spring Data JDBC over JOOQ means compile-time SQL injection prevention via the type system is unavailable. This is compensated by SpotBugs + Find Security Bugs in CI (see below).

**Re-evaluation note:** If the amount of hand-written SQL, PostgreSQL-specific typing (`jsonb`, `timestamptz`), or persistence boilerplate grows substantially, jOOQ is the most plausible future alternative. It would preserve the SQL-first architecture better than JPA/Hibernate while adding generated types and stronger compile-time checking. The current decision still stands: prefer Spring Data JDBC until the extra complexity of jOOQ is clearly justified by recurring maintenance cost.

---

## Security gate: SpotBugs + Find Security Bugs

**Chosen:** SpotBugs + Find Security Bugs as a mandatory CI gate  
**Rejected:** Structural SQL injection prevention via JOOQ (would have required choosing JOOQ above)

**Reason:** Once Spring Data JDBC was chosen, structural enforcement was no longer possible. SpotBugs + Find Security Bugs provides equivalent protection as a CI gate. Build fails on HIGH/CRITICAL findings. This makes SQL injection protection non-optional without adding runtime overhead.

---

## Frontend: Angular + PrimeNG + Tailwind CSS

**Chosen:** Angular SPA + PrimeNG + Tailwind CSS  
**Rejected:**
- React — wins on ecosystem (shadcn/ui); loses on security (manual XSS protection), agentic coding consistency (many valid approaches = higher agent variance), and project coherence
- Angular Material — dated design language; desktop-oriented; not suitable for the slick mobile UX goal
- Spartan UI — too new at time of decision; weak agent training data coverage
- Vaadin Flow — server-side rendering model produces sluggish interactions; not suitable for the UX goal
- Commercial component libraries — rejected on cost and open-source preference

**Reason:** Angular wins on built-in XSS protection, strict TypeScript enforcement, opinionated structure (fewer architectural decisions for agents to make), and PrimeNG v17+ provides modern design with broad agent training data coverage.

---

## Backend hosting: Hetzner VPS + Docker Compose + Caddy

**Chosen:** Hetzner VPS + Docker Compose + Caddy (reverse proxy + TLS)  
**Rejected:**
- Hono + Cloudflare Workers + Neon — weakest security posture: DIY OAuth2, Workers runtime gaps, npm supply chain risk, migration ordering fragility in CI/CD
- Go + Cloud Run/Fly.io + Neon — good operations story; loses on development cohesion and agentic coding (assembled stack, multiple paradigms, less agent training data for the combination)

**Reason:** One-instance, one-company deployment maps naturally to a single VPS. Docker Compose is simple to operate, easy to reason about for agents, and the full stack stays under one roof. Caddy handles TLS automatically with no configuration overhead.

---

## Identity: OAuth2/OIDC only — no password authentication

**Chosen:** GitHub OAuth2 + Google OAuth2; Apple OIDC planned but deferred  
**Rejected:** Password authentication, rolling own auth

**Reason:** Rolling own auth is high risk and high effort. Spring Security has first-class OAuth2/OIDC support. Once the user table is provider-agnostic, adding a new provider is mostly a configuration change.

**Apple note:** Apple Sign In was considered and deferred — non-standard OIDC (POST callback instead of GET, name only on first login), developer account cost, and complexity outweigh the benefit at this stage.

**Schema implication:** The `users` table stores `provider + subject_id`, not email. Email is stored only in `pending_invitations` and deleted on first login match (UR-C006). This was decided before writing the first migration to avoid a painful retrofit.

---

## Authorization model: recursive CTE evaluated at query time

**Chosen:** Recursive CTEs (Q1–Q4 in `docs/schema.sql`) evaluated per request  
**Rejected:**
- Materialized/flattened authorization table — requires cache invalidation on every tree mutation
- Post-processing in Java — moves authorization logic out of the database where it cannot be enforced atomically

**Reason:** The node tree is arbitrarily deep. Recursive CTEs are PostgreSQL-native, correct by construction, and co-located with the data. The four named queries (Q1–Q4) cover all access patterns; agents call `AuthorizationQueries` without needing to reason about tree shape.

---

## One instance = one company (no multi-tenancy)

**Chosen:** Single-tenant deployment  
**Rejected:** Row-level security / `tenant_id` columns / separate schemas per tenant

**Reason:** Target users are small companies self-hosting. Multi-tenancy adds schema complexity, authorization complexity, and data isolation risk. Single-tenant deployment is simpler to audit, simpler to GDPR-comply (one DPA per deployment), and simpler for agents to reason about.

---

## No custom sessions table

**Chosen:** Spring HttpSession (in-memory, or Redis-backed for multi-instance if needed)  
**Rejected:** Custom sessions table in PostgreSQL

**Reason:** Spring Session is an implementation detail — swappable without schema changes. A custom sessions table would need GDPR-compliant retention logic, cleanup jobs, and schema maintenance. HttpSession is managed by the container and cleared on logout naturally.

---

## Agentic coding: two-phase (test writers → implementers)

**Chosen:** Separate agents for writing tests and for writing production code  
**Rejected:** Single agents that write both tests and production code in one pass

**Reason:** When the same agent writes both tests and implementation, tests tend to be implementation-biased — they test what was built rather than what was specified. Separating the phases and restricting test agents to the spec (no `src/main/java/` visibility) ensures tests are derived from requirements, not from implementation choices. Implementation agents then have a clear, unambiguous contract to satisfy. This is the TDD red-green cycle applied at the agent level.

---

## Requirements: IREB standard, not user stories

**Chosen:** IREB-format user requirements (UR) and system requirements (SR)  
**Rejected:** Agile user stories ("As a [role], I want [feature], so that [benefit]")

**Reason:** User stories are an elicitation technique, not a documentation format. They are ambiguous, hard to verify formally, and lack structured traceability. IREB format produces requirements that are unambiguous, verifiable, and traceable — properties that are necessary for deriving test cases and for agentic coding where agents must determine independently whether a requirement is satisfied.

UR format: "The [stakeholder] shall be able to [capability]. [Goal: reason]"  
SR format: "The system shall [behaviour/property]. [Rationale: reason]"

---

## GDPR: non-negotiable constraint, not a feature

GDPR compliance is a hard constraint on every design decision, not a feature to be added later. Works council and DPO are named stakeholders in `docs/requirements-ur.md`. Any new data field must be evaluated against the necessity principle before it is added to the schema.

Key implications already baked into the design:
- No email stored for registered users (`users` table stores provider + subject only; UR-C006)
- Time tracking is always user-initiated, never system-initiated (no covert monitoring)
- Anonymization is self-service and irreversible (SR-F047.F01)
- Security event log capped at exactly 90 days (SR-C007.F01)
- Activity data retention is configurable with a minimum of 2 years (SR-F050.F01/SR-F050.F02)
- Every new data field requires a GDPR necessity justification before it enters the schema
