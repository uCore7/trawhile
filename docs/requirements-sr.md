# System requirements (SR)

System requirements derive from the user requirements in `docs/requirements-ur.md`, the architecture in `docs/architecture.md`, and the ADRs under `docs/adr/`. Format: *"The system shall [behaviour/property]. [Rationale: parent UR + design source]"*

**Identifier scheme.** Each SR ID encodes its parent UR and own type: `SR-NN-Tnn.Tnn` where the first segment matches the parent `UR-NN-Tnn` and the second segment is the SR's own type qualifier (`F` functional, `Q` quality, `C` constraint) plus a two-digit zero-padded sequence shared across all SR children of the same parent UR regardless of type. Example: `UR-03-F12 → SR-03-F12.F01, SR-03-F12.Q02, SR-03-F12.C03`.

**Type rules.** F-type and Q-type SRs each have at least one TE in the test plan. C-type SRs have none (satisfied by construction and verified by review). An F-type or Q-type parent UR must produce at least one SR of any type; a C-type parent UR may produce zero SRs.

**C-type URs satisfied purely by construction** — deployment topology, project organisation, hosting platform, the existence of named source files — produce zero SRs at this layer. The rationale for each such UR is documented in the upstream UR.

---

## Epic E-00 — Constraints

### Operational

**UR-00-C01** (one deployed instance per company) — zero SRs. Pure deployment topology; verified by review of the deployment context and the absence of multi-tenant primitives in the schema (no `company_id` discriminator anywhere).

**UR-00-C02** — OIDC providers configured at startup.

- **SR-00-C02.F01** (type F): The system shall validate at startup that at least one of the OIDC provider registration IDs `google`, `apple`, `microsoft`, `keycloak` has a non-empty `client-id` configured, and shall refuse to start with a descriptive error message naming the missing configuration when none does. [Rationale: UR-00-C02; configuration validation per architecture §8.8]
- **SR-00-C02.F02** (type F): The system shall expose `GET /auth/providers` without authentication, returning the registration IDs of OIDC providers that have a non-empty `client-id` configured at startup. The frontend uses this response to render only the sign-in buttons that have a working backend. [Rationale: UR-00-C02; providers are deployment-time configuration]

**UR-00-C03** — invitations via `mailto:` only.

- **SR-00-C03.F01** (type F): Invitation creation (parent UR-01-F04) shall return a `mailto:` URI whose body contains the application base URL, the invitee's email address as the invitation address, and a plain-language instruction to sign in with an OIDC provider linked to that address. The body is generated server-side; the application does not connect to any SMTP server. [Rationale: UR-00-C03]
- **SR-00-C03.C01** (type C): The application image and runtime dependencies shall contain no SMTP client and no outbound mail-transport library. [Rationale: UR-00-C03; verified by review of the build dependency manifest]

**UR-00-C04** — responsive browser application; no native mobile app.

- **SR-00-C04.C01** (type C): The frontend is implemented as a single Angular SPA, served by the backend under the same public HTTPS origin (architecture §5.3, ADR 0014). No separate native mobile application or alternative delivery channel exists. [Rationale: UR-00-C04]

**UR-00-C05** (GitHub hosting; PVR inbound + GHSA outbound) — zero SRs at the application layer. Project-organisation constraint; the outbound advisory channel is delivered through the admin-facing About page link (SR-06-F02.F01) and the guided subscription page (SR-06-F03.F01); the application makes no outbound query for advisories.

**UR-00-C07** (reference Compose includes log capture + log retention + backup tooling) — verified by review of the reference Compose file shipped with the project; the application's responsibility is limited to emitting structured logs in a format the pipeline can ingest (SR-00-C16.F01) and exposing the backup-tool entry points consumed by the backup container (parent UR-07-F02). Zero further SRs.

**UR-00-C08** — API-key access boundary.

- **SR-00-C08.F01** (type F): The system shall classify every external-actor endpoint as either *session-only* (mutating Account Holder operations; reads of the Account Holder's own profile data; System Admin operations; operations requiring OIDC interaction) or *session-or-API-key* (Viewer, Tracker, Node Admin operations). The authentication layer (architecture §5.2 Security/OIDC adapter) shall reject API-key-authenticated requests on session-only endpoints with HTTP 403 and a Problem response identifying the authentication mismatch. [Rationale: UR-00-C08]
- **SR-00-C08.F02** (type F): The MCP transport at `/api/mcp` (per `spec/openapi.yaml`) shall accept API-key-bearer authentication only; OIDC-session-cookie authentication shall not be honoured on this endpoint. The authentication layer shall reject session-cookie-only requests with HTTP 401 before dispatch to the Spring AI MCP server. [Rationale: UR-00-C08 — MCP clients are the protocol variant of API clients per the glossary; the MCP transport does not carry session cookies.]
- **SR-00-C08.C01** (type C): The classification of every endpoint shall be expressed in code (annotation or filter rule) such that adding a new endpoint forces an explicit choice; no endpoint shall default into session-or-API-key by omission. [Rationale: UR-00-C08; secure-by-default]

**UR-00-C09** — automated tests of backup-creation tooling.

- **SR-00-C09.F01** (type F): The project shall include an automated test suite that (a) produces a backup artifact using the trawhile-provided backup tooling against a populated test instance, (b) restores the artifact into a fresh empty instance following the documented restore procedure, and (c) verifies that the restored instance returns equivalent business state for a representative read query set. [Rationale: UR-00-C09; documented restore must be grounded in tested artifact format]

### Domain

**UR-00-C10** — UTC timestamps.

- **SR-00-C10.F01** (type F): All timestamp columns in the persistence schema shall use PostgreSQL `timestamptz` and store values normalised to UTC; no column shall store an additional timezone or offset value. [Rationale: UR-00-C10; architecture §8.12]
- **SR-00-C10.F02** (type F): All timestamp fields in REST API payloads (request and response) shall be serialised as ISO 8601 strings with the `Z` UTC suffix. [Rationale: UR-00-C10; OpenAPI contract clarity]
- **SR-00-C10.C01** (type C): All in-memory timestamp values in backend application code, persistence DTOs, port models, and event payloads shall be of type `java.time.Instant` (UTC by construction). No backend type shall carry a per-record timezone field. [Rationale: UR-00-C10; architecture §8.12]

**UR-00-C11** — store email address for registered users; cleared on anonymisation.

- **SR-00-C11.C01** (type C): The `users` table shall carry an `email TEXT` column that holds the email address as supplied by the OIDC `email` claim. The column shall be `NOT NULL` on active rows (`anonymised_at IS NULL`) and `NULL` on anonymised rows; the anonymisation cleanup of SR-07-F01.F01 sets it to `NULL` together with the other identifying-field clears. The `pending_invitations.email` column continues to exist for invitation lookup and is deleted together with the row when the invitation is consumed (UR-01-F13) or expires (UR-00-C13). [Rationale: UR-00-C11]
- **SR-00-C11.F01** (type F): OIDC-callback handling (architecture §6.1) shall persist the provider-returned email to `users.email` on every successful authenticated callback: on first-callback paths (bootstrap, invitation match) the email is written when the `users` row is finalised to active; on known-identity-login paths the email is refreshed from the current OIDC claim, so an email change at the IdP propagates to trawhile on next sign-in. [Rationale: UR-00-C11; UR-01-F01; UR-01-F13]
- **SR-00-C11.F02** (type F): When the backend returns a user reference (typically `{ id, displayName, email }`) for a user whose `users.anonymised_at IS NOT NULL`, both `displayName` and `email` shall be `null`. The frontend shall render such references using the localised placeholder string keyed `account.anonymisedUserLabel` per SR-00-C18.F01, in the active UI dialect. [Rationale: UR-00-C11; consistent rendering of anonymised users across all peer- and admin-facing contexts]

**UR-00-C12** — Docker Compose single-VPS deployment.

- **SR-00-C12.C01** (type C): The project shall ship a reference `docker-compose.yml` together with any required supporting files (Caddyfile, configuration templates, environment-variable examples) as the canonical deployment artifact for a complete single-VPS deployment. The Compose file shall define all services enumerated in architecture §7: `caddy`, `app`, `db` (PostgreSQL), `redis`, `log-pipeline`, and `backup`. The project shall not ship a manifest for any container-orchestration platform (Kubernetes, Docker Swarm, Nomad, ECS). [Rationale: UR-00-C12; architecture §7]

**UR-00-C13** — pending invitations expire after 90 days.

- **SR-00-C13.F01** (type F): A scheduled lifecycle job (architecture §6.4 Invitation expiry row) shall, at its architecture-defined interval, delete `pending_invitations` rows where `expires_at < NOW()`, cascading to the pre-created user record linked from each row per the *Pending invitation* glossary entry. The job is idempotent on restart. [Rationale: UR-00-C13; UR-07-F01]

**UR-00-C14** — application logs contain no personal data.

- **SR-00-C14.F01** (type F): The logging emission layer shall redact personal-data fields at emission time: structured-field arguments are passed through a pseudonymisation step that replaces direct user emails, names, and profile content with the user's internal UUID; raw request and response payloads are not logged in full. [Rationale: UR-00-C14; GDPR Art. 5(1)(c)]
- **SR-00-C14.C01** (type C): Backend logging code shall use only the project-provided structured-logging helpers; direct calls to underlying SLF4J / Logback APIs that would bypass the redaction step are forbidden by review and (where practical) by static check. [Rationale: UR-00-C14]

**UR-00-C15** — application logs retained 3 years by the log pipeline.

- **SR-00-C15.C01** (type C): The reference Docker Compose deployment's log-pipeline service shall be configured with a 3-year retention window; the retention period is fixed in the pipeline configuration, not exposed as an operator-tunable property of the application or the pipeline. The application shall not contain log-rotation or log-deletion code. [Rationale: UR-00-C15]

**UR-00-C16** — application logs carry correlation identifiers.

- **SR-00-C16.F01** (type F): Every log entry emitted by the application shall include the structured fields `requestId` (per inbound HTTP request), `traceId` (per outermost call from inbound or scheduled trigger), `sessionId` (where an authenticated session exists), and `actorId` (the pseudonymous user identifier where an actor is known). These are SLF4J MDC keys; the camelCase convention aligns with Spring Boot / Micrometer Tracing's auto-emitted `traceId` so the framework's value can be reused without renaming. Field population is the responsibility of an HTTP filter (for request-driven entries) and the lifecycle-trigger adapter (for scheduled entries). [Rationale: UR-00-C16]

**UR-00-C17** — data retention 3 years (fixed); time records + empty old nodes purged.

- **SR-00-C17.F01** (type F): A scheduled purge job (architecture §6.4 Data retention purge row) shall, at its architecture-defined interval, delete `time_records` rows where the record's counted-duration `ended_at` is older than `NOW() - INTERVAL '3 years'`. The job processes the work in chunks; each chunk runs in its own transaction and updates the job progress row so restart resumes from the stored cutoff. [Rationale: UR-00-C17]
- **SR-00-C17.F02** (type F): The same purge job shall, after time-record deletion has caught up, delete `nodes` rows whose subtree contains no remaining time records and whose own creation timestamp is older than `NOW() - INTERVAL '3 years'`, processed bottom-up so a parent is considered only after all its descendants have been considered. Cascade rules in the schema remove the node's authorization rows together with the node. [Rationale: UR-00-C17]
- **SR-00-C17.C01** (type C): The 3-year boundary shall be a compile-time constant in the application; it shall not appear as a configuration property and shall not be tunable at runtime. [Rationale: UR-00-C17]

**UR-00-C18** — UI language from browser locale; en-GB / de-DE / fr-FR / es-ES; default en-GB.

- **SR-00-C18.F01** (type F): The frontend shall implement runtime translation via `ngx-translate` with translation files for `en-GB`, `de-DE`, `fr-FR`, `es-ES` shipped under the SPA source tree. The active language is resolved at SPA startup from `navigator.language` by best-match against the four supported tags: an exact tag match selects that file; a same-language-different-region tag falls back to the language's shipped dialect (e.g., `en-US` resolves to `en-GB`, `de-CH` resolves to `de-DE`); if no language root matches, `en-GB` is used. [Rationale: UR-00-C18]
- **SR-00-C18.F02** (type F): The language-switcher UI shall label each available dialect by its endonym — the language name in that language followed by the region name in that language, parenthesized — derived at render time from the browser's `Intl.DisplayNames` API (e.g., `English (United Kingdom)`, `Deutsch (Deutschland)`, `Français (France)`, `Español (España)`). Endonym display ensures a user always recognises their language regardless of which UI dialect is currently active. New shipped dialects do not require code changes to the switcher beyond adding the tag to the supported set of SR-00-C18.F01. [Rationale: UR-00-C18]
- **SR-00-C18.C01** (type C): The backend shall emit no user-facing localised text. API error responses use the OpenAPI `Problem` shape with stable, locale-neutral error codes (architecture §8.7); the frontend maps codes to user-facing strings in the active language. [Rationale: UR-00-C18]

### Security

**UR-00-C19** — request-rate limits.

- **SR-00-C19.C01** (type C): The set of routed external endpoints reachable from outside the deployed instance is exhaustively constituted of four classes:
  - **(a) SPA static surface** — the SPA shell, all built JS/CSS/font/image assets, the favicon, and every other static-asset path served by Caddy from the SPA build mount;
  - **(b) OIDC authorization flow** — the Spring Security OAuth2 routes (`/oauth2/authorization/**` initiator, `/login/oauth2/code/**` callback) and the sign-out route (`/logout`);
  - **(c) OIDC discovery** — the unauthenticated `/auth/providers` endpoint per SR-00-C02.F02;
  - **(d) Application API surface** — every endpoint documented under `paths:` in `spec/openapi.yaml` (every `/api/*` path, which includes the MCP transport at `/api/mcp`).

  No URL shall be reachable from outside the deployment that is not in one of these four classes. Adding a new external endpoint requires classifying it into one of (a)–(d). [Rationale: UR-00-C19; defining the closed set lets downstream SRs that quantify over "every external endpoint" be unambiguous and verifiable.]
- **SR-00-C19.F01** (type F): The Caddy reverse proxy shall apply token-bucket rate limiting to **every endpoint in the set defined by SR-00-C19.C01** — the SPA static surface, the OIDC authorization flow, the OIDC discovery endpoint, and the entire application API surface (including `/api/mcp`). Requests exceeding the bucket shall receive HTTP 429 with a generic Problem body that does not reveal the bucket configuration. Rate-limit rejections shall be observable via the Caddy metrics endpoint scraped by the external Monitoring stack. [Rationale: UR-00-C19; architecture §8.4]

**UR-00-C20** — security headers on every HTTP response.

- **SR-00-C20.F01** (type F): Every HTTP response from the application origin shall include `Content-Security-Policy` (default same-origin; external sources only as named exceptions for fonts, OIDC provider iframes if any), `Strict-Transport-Security: max-age=31536000; includeSubDomains`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, and `Referrer-Policy: no-referrer`. Headers shall be set by Spring Security's response-header configuration (architecture §8.4). [Rationale: UR-00-C20]

**UR-00-C21** — CSRF protection on state-mutating endpoints.

- **SR-00-C21.F01** (type F): All `POST`, `PUT`, `PATCH`, and `DELETE` endpoints reached by registered users (session-authenticated requests) shall require a valid CSRF token; missing or invalid token shall return HTTP 403 with a Problem body identifying the failure as CSRF. CSRF protection is implemented via Spring Security's built-in token mechanism. [Rationale: UR-00-C21]
- **SR-00-C21.C01** (type C): API-key-authenticated requests shall be exempt from CSRF protection (no browser-origin attack vector; bearer-token presentation is itself the proof of intent). The exemption shall be implemented via Spring Security's CSRF configurer using a request matcher (`HttpSecurity.csrf(csrf -> csrf.ignoringRequestMatchers(...))`) that matches requests whose `Authorization` header starts with `Bearer `; such requests bypass `CsrfFilter`. Session-cookie-authenticated requests carry no `Authorization` header and are therefore not matched, so CSRF is always enforced on them. The matcher decides per request rather than per route, so the same endpoint correctly enforces CSRF on session calls and bypasses CSRF on API-key calls. [Rationale: UR-00-C21; UR-00-C08]

**UR-00-C22** — no fingerprinting in anonymous responses.

- **SR-00-C22.F01** (type F): All anonymous responses (error pages, generic 404/403/500 documents, HTTP response headers, OIDC callback redirect bodies, login pages) shall not include the application version, dependency identifiers, runtime stack traces, OpenAPI schema content, or outbound network configuration. Caddy strips `Server` and `Via` response headers; Spring Boot removes its `Server` header; error responses to unauthenticated requests use generic Problem documents. [Rationale: UR-00-C22; architecture §8.4]
- **SR-00-C22.C01** (type C): The SBOM (CycloneDX artifact) shall be published only as a GitHub release artifact and shall not be served by the deployed application at any URL. The OpenAPI specification, the running version, the outbound-connections list, and the disclosure/advisory links shall be reachable only via the auth-gated About page (parent UR-05-F06). [Rationale: UR-00-C22; UR-05-F06]

## Epic E-01 — System administration

**UR-01-F01** — operator designates the first System Admin.

- **SR-01-F01.F01** (type F): The system shall accept the environment variable `BOOTSTRAP_ADMIN_EMAIL` and shall classify the first successful OIDC callback whose provider-returned email matches that value as a *first-admin bootstrap* outcome (per architecture §6.1) when no `node_authorizations` row with `auth_level = 'admin'` on the root node exists in the database. In a single transaction the system shall: insert a `users` row, insert a `user_oauth_providers` row linking the OIDC (provider, subject) pair, and insert a `node_authorizations` row granting `admin` on the root node id; then establish an authenticated session and emit an `oidc_login_succeeded` audit event (per SR-06-F01.F01) with a `bootstrap=true` field. [Rationale: UR-01-F01; architecture §6.1]
- **SR-01-F01.F02** (type F): Once at least one `node_authorizations` row with `auth_level = 'admin'` on the root node exists, the bootstrap outcome of SR-01-F01.F01 shall no longer trigger regardless of `BOOTSTRAP_ADMIN_EMAIL`. Subsequent callbacks for that email follow normal invitation-match or known-identity-login outcomes. [Rationale: UR-01-F01; bootstrap is single-shot]
**UR-01-F02** — view all registered users.

- **SR-01-F02.F01** (type F): The system shall expose `GET /api/admin/users`, accepting only requests authenticated with an effective `admin` grant on the root node, returning the complete list of `users` rows in one response. Each row carries: the user's UUID, `displayName`, `email` (per SR-00-C11.C01; `null` for anonymised users), `status` (`pending` if a `pending_invitations` row exists for the user; `anonymised` if `users.anonymised_at IS NOT NULL` per SR-05-F03.C01; `active` otherwise), the count of linked OIDC providers, the count of effective node-authorization grants, the creation timestamp, and the last-active timestamp where one exists. Pagination and filtering (by status, by `displayName` substring, by `email` substring) are presenter-side concerns: the frontend loads the full list once per session and renders, filters, and chunks it client-side. The expected single-company scale (tens of users) makes whole-list transfer cheaper than the round-trips of server-side pagination. [Rationale: UR-01-F02; UR-00-C11]

**UR-01-F03** — view all pending invitations.

- **SR-01-F03.F01** (type F): The system shall expose `GET /api/admin/invitations`, accepting only requests authenticated with effective `admin` on the root node, returning the list of `pending_invitations` rows including: the invitation id, the invitee email, the inviter's user UUID and display name, `invited_at`, `expires_at`, the linked pre-created user UUID, and the count of pre-assigned `node_authorizations` rows referencing the linked user. [Rationale: UR-01-F03]

**UR-01-F04** — create invitation by email, return `mailto:` link.

- **SR-01-F04.F01** (type F): The system shall expose `POST /api/admin/invitations` with body `{ email }`, accepting only requests authenticated with effective `admin` on the root node, and shall execute within a single transaction:
  - reject with HTTP 409 if the email already appears in a non-expired `pending_invitations` row;
  - reject with HTTP 409 if the email matches `users.email` for any non-anonymised user (per SR-00-C11.C01); the existing user is already registered and an invitation is unnecessary;
  - insert a `users` row in the *pending* state;
  - insert a `pending_invitations` row with the supplied email, the inviter id, `invited_at = NOW()`, `expires_at = NOW() + INTERVAL '90 days'`, and `user_id` referencing the newly inserted users row;
  - emit the `invitation_created` audit event;
  - return the response body as defined by SR-00-C03.F01 (a `mailto:` URI built server-side). [Rationale: UR-01-F04; UR-00-C03; UR-00-C11; UR-00-C13]

**UR-01-F05** — withdraw pending invitation.

- **SR-01-F05.F01** (type F): The system shall expose `DELETE /api/admin/invitations/{id}`, accepting only requests authenticated with effective `admin` on the root node, and shall execute the *pending user* access-termination cleanup defined by SR-07-F01.F02 on the linked pre-created user, deleting the invitation and the user record in one transaction. The endpoint shall emit the `invitation_withdrawn` audit event. [Rationale: UR-01-F05; UR-07-F01]

**UR-01-F06** — remove user via guided confirmation wizard.

- **SR-01-F06.F01** (type F): The system shall expose `POST /api/admin/users/{id}/remove`, accepting only requests authenticated with effective `admin` on the root node, and shall execute the *active user* access-termination cleanup defined by SR-07-F01.F01 on the target user, with the `user_removed` audit event recording the actor and target. The operation shall be rejected with HTTP 409 if the target user holds the last `admin` grant on the root node (no path may leave the deployment without a System Admin). [Rationale: UR-01-F06; UR-07-F01]
- **SR-01-F06.F02** (type F): The frontend shall present user removal as a multi-step confirmation wizard:
  - **step 1**: show the user's `displayName`, the list of node-authorization grants (including direct vs inherited), and the user's currently active tracking record if any;
  - **step 2**: explain the consequences in the active language (per SR-00-C18.F01): irreversibility; the user's identifying data is anonymised; activity records remain subject to UR-00-C17 retention; the user may re-register only via a new invitation;
  - **step 3**: require explicit confirmation; on confirm, call `POST /api/admin/users/{id}/remove`. [Rationale: UR-01-F06; informed-consent UX for irreversible actions]

**UR-01-F07** — manage a user's node-authorization assignments across the tree.

- **SR-01-F07.F01** (type F): The system shall expose `GET /api/admin/users/{id}/authorizations` (effective `admin` on root), returning the same shape as `GET /api/account/me/authorizations` (SR-05-F04.F01) for the target user. [Rationale: UR-01-F07]
- **SR-01-F07.F02** (type F): The grant and revoke operations exposed at `POST /api/admin/users/{id}/authorizations` and `DELETE /api/admin/users/{id}/authorizations/{nodeId}` shall delegate to the same node-authorization service methods as UR-02-F07 / UR-02-F08 (SR-02-F07.F01 / SR-02-F08.F01), enforcing the same invariants (caller must have effective `admin` on the affected node; caller cannot CRUD their own grants per the no-self-CRUD key invariant; target user must not be anonymised on grant; revoke must not leave the node without an effective admin). The endpoints differ from the node-side endpoints only in the selection axis (user-first rather than node-first). [Rationale: UR-01-F07; single grant/revoke pathway]

**UR-01-F08** — resend pending invitation, reset expiry, fresh `mailto:` link.

- **SR-01-F08.F01** (type F): The system shall expose `POST /api/admin/invitations/{id}/resend`, accepting only requests authenticated with effective `admin` on the root node, and shall:
  - update `pending_invitations.expires_at = NOW() + INTERVAL '90 days'` for the row;
  - emit the `invitation_resent` audit event;
  - return a fresh `mailto:` URI with the same body format as SR-00-C03.F01.

  The endpoint shall not create new `users` or `node_authorizations` rows, and shall not alter any pre-assigned authorizations. [Rationale: UR-01-F08]

**UR-01-F09** — invite-members prompt when no other members exist.

- **SR-01-F09.F01** (type F): The frontend shall, on initial post-login route resolution for users with effective `admin` on the root node, fetch the user list via `GET /api/admin/users` (SR-01-F02.F01) and, when the returned list contains only the authenticated user, display a non-dismissible-on-this-route prompt to invite members. The prompt links to the invitation-creation flow (SR-01-F04.F01) and is closable in the standard way once invitations have been issued. [Rationale: UR-01-F09]
- **SR-01-F09.C01** (type C): The invite-members prompt shall be shown only to users with effective `admin` on the root node (any other user has no API path to create invitations). [Rationale: UR-01-F09; permission alignment]

**UR-01-F10** — monitor system health via Prometheus-compatible metrics.

- **SR-01-F10.F01** (type F): The system shall expose `/actuator/prometheus` on the dedicated Spring Boot management port (per architecture §5.2.1 and SR-00-C19.F01 router classification). The management port shall not be routed through Caddy and shall not require additional authentication; access is restricted by the container network topology of the reference Docker Compose deployment. The endpoint shall include all standard Spring Boot Actuator metrics: JVM (heap, GC, threads), HTTP server request counts and latency histograms, HikariCP connection pool metrics, and Tomcat metrics. [Rationale: UR-01-F10; architecture §5.2.1 MeterRegistry row]
- **SR-01-F10.F02** (type F): The system shall additionally expose the following custom metrics via Micrometer:

  | Metric name | Type | Labels | Description |
  |---|---|---|---|
  | `trawhile_purge_job_last_completed_seconds` | Gauge | `job_type` | UTC timestamp (seconds) of the last successful completion of each purge job per SR-00-C17 and SR-00-C13 |
  | `trawhile_purge_job_deleted_total` | Counter | `job_type` | Cumulative rows deleted across all purge job runs |
  | `trawhile_purge_job_failures_total` | Counter | `job_type` | Purge job runs terminated by an unhandled exception |
  | `trawhile_db_transaction_errors_total` | Counter | — | Database transactions rolled back due to error |
  | `trawhile_oidc_login_failures_total` | Counter | `provider` | Failed OIDC login callbacks per provider |
  | `trawhile_sse_connections_active` | Gauge | — | Currently active SSE emitter connections |
  | `trawhile_tracking_sessions_active` | Gauge | — | Users with an open-ended (`ended_at IS NULL`) time record |
  | `trawhile_api_key_use_total` | Counter | `outcome` | API-key-authenticated requests per outcome (`accepted`, `rejected_expired`, `rejected_revoked`, `rejected_not_found`) |
  | `trawhile_webhook_delivery_outbox_pending` | Gauge | — | Current number of `webhook_deliveries` rows in pending status; a sustained growth signals worker stall or persistent subscriber outage (UR-03-F12) |
  | `trawhile_webhook_delivery_total` | Counter | `outcome` | Webhook delivery attempts per outcome (`success`, `transient_failure`, `permanent_failure`); the rule for surfacing permanently failing deliveries per UR-03-F12 fires on a non-zero rate of `permanent_failure` |

  [Rationale: UR-01-F10; metric names align with the audit event vocabulary of SR-06-F01.F01]
- **SR-01-F10.F03** (type F): The project shall ship a `deploy/monitoring/` directory containing operator artifacts that consume the metrics defined in SR-01-F10.F02:
  - `deploy/monitoring/prometheus-scrape-config.yml` — ready-to-paste Prometheus scrape job blocks targeting the Spring Boot management port and the Caddy metrics endpoint;
  - `deploy/monitoring/alerting-rules.yml` — AlertManager rules for purge-job staleness (`trawhile_purge_job_last_completed_seconds` exceeding 26 hours per `job_type`), sustained database transaction error rate, high HTTP 5xx rate, instance down;
  - `deploy/monitoring/grafana-dashboard.json` — an importable Grafana dashboard covering JVM, HTTP, HikariCP, the custom metrics above, and Caddy edge metrics.

  These artifacts are not bundled into the application container image; they are operator tooling tracked in the source repository. [Rationale: UR-01-F10; monitoring artifacts that drift from metric names silently break dashboards and alerts]

**UR-01-F11** — access application logs via the trawhile-provided logging infrastructure.

- **SR-01-F11.C01** (type C): Operator log access is provided by the `log-pipeline` service of the reference Docker Compose deployment (architecture §7), realised as Grafana Loki plus Promtail per ADR 0018, and not by an in-application log viewer. The application's responsibility is limited to emitting structured log entries per SR-00-C14.F01, SR-00-C16.F01, and SR-06-F01.F01. The operator query surface for ST-5 is LogQL via Grafana Explore against the Loki datasource; the Grafana instance is the one already serving metrics dashboards from the Monitoring stack (SR-01-F10.F03). [Rationale: UR-01-F11; architecture §7; ADR 0018]

**UR-01-F12** — operator informed of configuration errors at startup.

- **SR-01-F12.F01** (type F): The system shall validate every configuration property bound under the `trawhile:` namespace at application context refresh and shall refuse to start with a non-zero exit code and a descriptive error log entry naming the failing property and the violated constraint when validation fails. Validation includes: SR-00-C02.F01 (at least one OIDC provider configured); and any further constraints defined as the configuration surface is finalised. [Rationale: UR-01-F12; architecture §8.8]

**UR-01-F13** — OIDC sign-in.

- **SR-01-F13.F01** (type F): The OIDC callback handler (architecture §5.2.3 Auth flow adapter, §6.1) shall classify each successful OIDC callback into exactly one of the following first-callback outcomes:
  - **first-admin bootstrap** per SR-01-F01.F01 (BOOTSTRAP_ADMIN_EMAIL match and no admin grants exist);
  - **invitation match** (provider-returned email matches a still-pending invitation): in a single transaction insert a `user_oauth_providers` row linking the OIDC (provider, subject) to the pre-created user, transition the user from *pending* to *active*, delete the `pending_invitations` row, emit the `oidc_login_succeeded` audit event with the relevant tags;
  - **provider linking** (browser session is already authenticated): add the new (provider, subject) pair to the authenticated user's linked providers per SR-05-F02.F02;
  - **known-identity login** (the (provider, subject) pair exists in `user_oauth_providers`): establish an authenticated session for the linked user;
  - **rejected login** (none of the above): redirect to `/login?error=not_invited` and emit `oidc_login_rejected(cause=not_invited)`; the response shall not reveal whether the email is known to the system (per UR-00-C22). [Rationale: UR-01-F13; architecture §6.1]
- **SR-01-F13.F02** (type F): The login page shall be accessible without authentication and shall display: an OIDC sign-in button for each provider registration id returned by SR-00-C02.F02; a brief explanatory note that access is invitation-only; and a link to the About page (SR-05-F06.F01, behind auth). When rendered with `?error=not_invited`, it shall additionally display the message "No pending invitation was found for your account. Please contact your administrator." (translated per SR-00-C18.F01). The login page shall not request any company-specific data from the backend and shall not reveal company-identifying information to an unauthenticated visitor (UR-00-C22). [Rationale: UR-01-F13; UR-00-C22]

**UR-01-F14** — continue OIDC session without re-authenticating until sign-out or expiry.

- **SR-01-F14.F01** (type F): Authenticated browser sessions shall be backed by Spring Session in Redis with a finite inactivity timeout (default 12 hours; operator-tunable via the standard Spring Session configuration property under `spring.session.timeout`). The 12-hour default is chosen so that a session covers a full workday without forcing re-authentication during work. Session cookies shall be configured `Secure`, `HttpOnly`, and `SameSite=Lax`. The user shall remain signed in across requests within the same browser until the cookie expires, the session times out by inactivity, or the user explicitly signs out (which deletes the session from Redis). [Rationale: UR-01-F14; architecture §8.4]
- **SR-01-F14.F02** (type F): On Account Holder anonymisation (SR-05-F05.F01) or admin removal (SR-01-F06.F01) of a user, any Spring Session entries associated with that user shall be deleted as part of the cleanup transaction (the *invalidate active delegated access* step of SR-07-F01.F01), so any open browser tabs of the removed user are signed out on their next request. [Rationale: UR-01-F14; UR-07-F01]

---

## Epic E-02 — Node administration

All endpoints in this epic accept both OIDC-session and API-key authentication (per UR-00-C08 and SR-00-C08.F01), and all node-level authorization checks resolve via the PostgreSQL recursive-CTE helper functions (architecture §8.2).

**UR-02-F01** — view node details and direct children with at least `view`.

- **SR-02-F01.F01** (type F): The system shall expose `GET /api/nodes/tree`, returning the caller's full visible node tree — every node on which the caller holds at least `view` via the recursive grant rule (architecture §8.2). The response is a nested tree: each node carries `id`, `parentId`, `displayName`, `description`, `color`, `icon`, `logoUrl`, `isActive`, `sortOrder`, `deactivatedAt` (nullable), the caller's effective authorization level on the node (enum: `view` | `edit` | `admin`), and a `children` array containing the node's direct children expressed in the same shape, ordered by `sortOrder`. The same backend service method shall produce this REST response and the `NodeTreeChanged` snapshot SSE event payload per ADR 0017 and SR-03-F12.F01; the OpenAPI type names the payload of both channels. Nodes outside the caller's visibility do not appear in the response and their existence is not disclosed (UR-00-C22). [Rationale: UR-02-F01; UR-03-F12; ADR 0017; UR-00-C22]

**UR-02-F02** — create child node.

- **SR-02-F02.F01** (type F): The system shall expose `POST /api/nodes/{parentId}/children` with body `{ displayName, description?, color?, icon? }`, accepting only requests whose caller has effective `admin` on the parent node (via recursive CTE). On success the system shall, in a single transaction: acquire a row lock on every existing sibling (`SELECT id FROM nodes WHERE parent_id = {parentId} FOR UPDATE`) to serialise concurrent appends under the same parent; insert a `nodes` row with `parent_id = {parentId}`, `is_active = true`, `sort_order = COALESCE(MAX(sort_order), 0) + 1` computed against the locked sibling set, the supplied fields, and `deactivated_at = NULL`; emit a node-tree change event (per SR-03-F12.F01 / `NodeTreeChanged` per ADR 0017) to all users whose visible subtree now contains the new node; and return the created node. The `logo` attribute is not part of the create body; logos are set after creation via SR-02-F03.F01. [Rationale: UR-02-F02; architecture §8.2]

**UR-02-F03** — edit node attributes including logo upload constraints.

- **SR-02-F03.F01** (type F): The system shall expose `PATCH /api/nodes/{id}` with body `{ displayName?, description?, color?, icon?, logo? }`, accepting only requests whose caller has effective `admin` on the node. Field omission means "no change"; explicit `null` clears the field on the node row (for nullable fields `description`, `color`, `icon`, and `logoUrl`). The `logo` field, when present and non-null, carries the new logo as base64-encoded image bytes plus the declared MIME type (concrete shape defined in `spec/openapi.yaml`); the size and MIME-type checks of SR-02-F03.F02 apply to the decoded bytes and the declared MIME type respectively, and run before any persistence write. The update runs in a single transaction; on success the system emits the corresponding `NodeTreeChanged` snapshot SSE event. [Rationale: UR-02-F03; ADR 0017]
- **SR-02-F03.F02** (type F): Logo payloads submitted via the `logo` field of SR-02-F03.F01 shall be rejected with HTTP 413 (`Payload Too Large`) when the decoded byte length exceeds 256 KB and with HTTP 415 (`Unsupported Media Type`) when the declared MIME type is not one of `image/png`, `image/jpeg`, `image/svg+xml`, `image/webp`. Both checks shall run before any persistence write. [Rationale: UR-02-F03]
- **SR-02-F03.C01** (type C): The 256 KB logo size limit and the four accepted MIME types of SR-02-F03.F02 shall be compile-time constants; no operator-tunable property exposes either. [Rationale: UR-02-F03; bounded resource consumption]

**UR-02-F04** — reorder child nodes.

- **SR-02-F04.F01** (type F): The system shall expose `PUT /api/nodes/{id}/children/order` with body `{ orderedChildIds: [UUID, ...] }`, accepting only requests whose caller has effective `admin` on the parent node. The endpoint shall reject the call with HTTP 409 if the submitted list does not equal the current set of direct children of the parent (no additions, no removals). On success the system shall update each child's `sort_order` to its index in the submitted list, in one transaction, then emit `NodeTreeChanged`. [Rationale: UR-02-F04]

**UR-02-F05** — deactivate / reactivate node.

- **SR-02-F05.F01** (type F): The system shall expose `POST /api/nodes/{id}/deactivate`, accepting only requests whose caller has effective `admin` on the node. Within a single transaction the system shall: acquire a row lock on the node and every direct or indirect descendant by issuing `SELECT id FROM nodes WHERE id IN (<recursive-descendant CTE from {id}>) FOR UPDATE` (the recursive descendant filter uses the helper from architecture §8.2), so that concurrent child-creation under any node in the subtree is serialised against the deactivation; reject the call with HTTP 409 if any locked row has `is_active = true` (other than the target node itself); set `is_active = false`, `deactivated_at = NOW()` on the target node row; and emit `NodeTreeChanged`. An open `time_records` row on the node itself shall not block deactivation; the user who started tracking may stop it normally. [Rationale: UR-02-F05; architecture §8.2; tracking glossary; key invariants]
- **SR-02-F05.F02** (type F): The system shall expose `POST /api/nodes/{id}/reactivate`, accepting only requests whose caller has effective `admin` on the node. On success the system shall set `is_active = true`, `deactivated_at = NULL` in one transaction, then emit `NodeTreeChanged`. [Rationale: UR-02-F05]

**UR-02-F06** — move node.

- **SR-02-F06.F01** (type F): The system shall expose `POST /api/nodes/{id}/move` with body `{ destinationParentId }`, and shall execute, within a single transaction, the following validations and update:
  - the caller has effective `admin` on the node being moved (HTTP 403 otherwise);
  - the caller has effective `admin` on the destination parent node (HTTP 403 otherwise);
  - the destination parent is not the node itself or any of its descendants (HTTP 409 with message "Cannot move a node into its own subtree" otherwise; per the key invariant in `requirements-ur.md`);
  - update `parent_id` to the destination and set `sort_order` to one greater than the current maximum among the destination's existing children.

  On success emit `NodeTreeChanged`. [Rationale: UR-02-F06; key invariant]

**UR-02-F07** — grant `view`/`track`/`admin` authorization.

- **SR-02-F07.F01** (type F): The system shall expose `POST /api/nodes/{nodeId}/authorizations` with body `{ userId, level }` where `level ∈ {view, track, admin}`. The endpoint shall reject the call with HTTP 403 if the caller lacks effective `admin` on the node. The endpoint shall reject the call with HTTP 409 if `body.userId == caller.user_id` ("Cannot modify your own node-authorization grants; ask a peer admin or self-anonymise per UR-05-F05" — per the key invariant in requirements-ur.md). The endpoint shall reject the call with HTTP 409 if the target user is anonymised (`users.anonymised_at IS NOT NULL`). On success the endpoint shall upsert the `node_authorizations` row for `(user_id, node_id)` to the requested `level`, in one transaction; submitting a new grant for the same `(user, node)` overwrites the previous direct level (raise, lower, or keep). Inherited grants from ancestors are not affected; effective rights on the node are the maximum of the direct grant on this node, if any, and any grants inherited from ancestors. Granting `admin` on the root node confers System Admin rights (no separate operation). On success emit the `AuthorizationChanged` snapshot SSE event (per ADR 0017) to the affected user, and emit `NodeTreeChanged` to that user if the grant changed their visibility. [Rationale: UR-02-F07; UR-01-F07; ADR 0017; no-self-CRUD key invariant]
- **SR-02-F07.F02** (type F): The permissions UI presented at grant and revoke screens (both the node-side and user-side flows of UR-02-F07 / UR-01-F07) shall display explanatory text describing what each level confers, before any submit button is enabled. Required level descriptions (translated per SR-00-C18.F01):

  | Level | Description |
  |---|---|
  | `view` | The user can see this node, all nodes beneath it, and team time aggregates for them. |
  | `track` | Everything `view` confers, plus the ability to track time on this node and any trackable node beneath it. |
  | `admin` | Everything `track` confers, plus the ability to manage child nodes and to grant or revoke authorizations on this node and any node beneath it. Granted authorizations are inherited downward. |

  The UI shall additionally state that authorizations are inherited downward. [Rationale: UR-02-F07; UR-02-F08; informed-consent UX for elevated permissions]

**UR-02-F08** — revoke authorization grant; cannot leave a node without effective admin; no self-revoke.

- **SR-02-F08.F01** (type F): The system shall expose `DELETE /api/nodes/{nodeId}/authorizations/{userId}`. The endpoint shall reject the call with HTTP 403 if the caller lacks effective `admin` on the node. The endpoint shall reject the call with HTTP 409 if `{userId} == caller.user_id` ("Cannot revoke your own node-authorization grants; ask a peer admin or self-anonymise per UR-05-F05" — per the key invariant in requirements-ur.md). The endpoint shall reject the call with HTTP 409 and the message "Cannot leave the node without an effective admin" if executing this delete would leave the node with zero users holding effective `admin` (counting remaining direct admin grants on the node plus admin grants inherited from ancestors). On success the system shall delete the row in one transaction, then emit `AuthorizationChanged` to the affected user. Revoking `admin` on the root node removes System Admin rights from the affected user (no separate operation). [Rationale: UR-02-F08; UR-01-F07; no-self-CRUD and no-orphaned-admin key invariants]

**UR-02-F09** — view all authorization assignments on a node with at least `view`.

- **SR-02-F09.F01** (type F): The system shall expose `GET /api/nodes/{nodeId}/authorizations`, accepting only requests whose caller has effective `view` on the node, returning the list of authorization rows that are effective on the node, each annotated with: the target user UUID, `displayName`, and `email` (per UR-00-C11 peer visibility for collision disambiguation); the granted level; a flag distinguishing *direct* (the row's `node_id` equals `{nodeId}`) from *inherited* (the row exists on an ancestor and is effective here via the recursive grant rule), with the inheriting ancestor's node id and path included for the inherited case. [Rationale: UR-02-F09; UR-00-C11; UR-00-C22 visibility scoping]

---

## Epic E-03 — Time tracking

All endpoints in this epic accept both OIDC-session and API-key authentication (per UR-00-C08). All time-record writes (create, edit, duplicate, atomic switch) enforce the key invariants from `requirements-ur.md` on the write path: at most one open record per user, no overlap with other records of the same user, `started_at` and `ended_at` within 3 years of `NOW()`, `ended_at >= started_at` when set, description ≤ a fixed maximum length. Failure of any invariant returns HTTP 409 with a Problem document identifying the violated invariant.

**UR-03-F01** — view current tracking status.

- **SR-03-F01.F01** (type F): The system shall expose `GET /api/tracking/current`, returning the caller's currently-open `time_records` row (one with `ended_at IS NULL`) or the JSON literal `null` when no open record exists. The response carries: `id`, `nodeId`, the node's full ancestor path from root, `startedAt` (ISO 8601 UTC), `elapsedSeconds` (computed `EXTRACT(EPOCH FROM (NOW() - started_at))`), and `description` if set. [Rationale: UR-03-F01]
- **SR-03-F01.F02** (type F): The same response shape returned by SR-03-F01.F01 shall be the payload of the `TrackingChanged` snapshot SSE event per ADR 0017; the same backend service method produces both. The event is pushed to all active SSE sessions of the owning user on every change to the user's open record (create, switch, stop, auto-close). [Rationale: UR-03-F01; UR-03-F12; ADR 0017]

**UR-03-F02** — view recent time record history (chronological list of individual records).

- **SR-03-F02.F01** (type F): The system shall expose `GET /api/tracking/history`, returning the caller's own closed time records in descending `startedAt` order, paginated with a stable page-token mechanism, each row carrying: `id`, `nodeId`, the node's full ancestor path from root, `startedAt`, `endedAt`, `durationSeconds`, and `description` if set. Open records (where `ended_at IS NULL`) are excluded from the history list; the live tracking status is served separately by SR-03-F01.F01. [Rationale: UR-03-F02; key invariant on report exclusion of open records]
- **SR-03-F02.C01** (type C): The history response shall contain no `overlap` annotation; overlaps are prevented on the write path (per UR-03 key invariant + SR-03-F08.F01 / SR-03-F09.F01 / SR-03-F11.F01) and therefore cannot exist in stored data. [Rationale: UR-03-F02; no-overlap key invariant]

**UR-03-F03** — start tracking via node picker.

- **SR-03-F03.F01** (type F): The system shall expose `POST /api/tracking/start` with body `{ nodeId, description? }`. The endpoint shall reject the call with HTTP 409 if any of the following holds:
  - the target node has `is_active = false` (deactivated nodes do not accept live tracking start; per *Trackable* glossary);
  - the target node has at least one direct child with `is_active = true` (only leaf-equivalent nodes are trackable);
  - the caller's effective authorization on the target node is below `track`;
  - the caller already has an open `time_records` row (`ended_at IS NULL`) — only one open record per user (UR-03 key invariant);
  - the supplied `description` exceeds the fixed maximum description length (SR-03-F03.C01).

  On success the system shall insert a `time_records` row with `user_id = caller`, `node_id = {nodeId}`, `started_at = NOW()`, `ended_at = NULL`, `description = {description}` (NULL if omitted), in one transaction, then emit the `TrackingChanged` snapshot SSE event to the caller. [Rationale: UR-03-F03; key invariants]
- **SR-03-F03.C01** (type C): The maximum description length shall be 256 Unicode characters. The limit is a compile-time constant; no operator-tunable property exposes it. [Rationale: UR-03-F03; key invariant on description length]

**UR-03-F04** — start tracking from quick-access list.

- **SR-03-F04.F01** (type F): The quick-access start path shall delegate to the same backend service method as SR-03-F03.F01 (one start service method, two UI entry points). The frontend's quick-access UI submits the same `POST /api/tracking/start` body with the chosen node's id and the optional description. [Rationale: UR-03-F04; single start pathway]

**UR-03-F05** — atomic switch.

- **SR-03-F05.F01** (type F): The system shall expose `POST /api/tracking/switch` with body `{ nodeId, description? }`, applying all SR-03-F03.F01 validations to the target node and description, except the "user has no open record" check (an open record is required for switch). On success the system shall, in a single transaction: set `ended_at = NOW()` on the caller's currently-open record; insert a new `time_records` row with the new node id, `started_at = NOW()`, and the supplied description; emit one `TrackingChanged` event reflecting the new open record. Either both writes succeed or neither does. [Rationale: UR-03-F05; atomic switch invariant]

**UR-03-F06** — stop tracking.

- **SR-03-F06.F01** (type F): The system shall expose `POST /api/tracking/stop`. The endpoint shall reject the call with HTTP 409 if the caller has no open record. On success the system shall set `ended_at = NOW()` on the open record in one transaction, then emit `TrackingChanged` with payload `null`. [Rationale: UR-03-F06]

**UR-03-F07** — manage quick-access list.

- **SR-03-F07.F01** (type F): The system shall expose `GET /api/account/me/quick-access` (session or API key), returning the caller's quick-access entries in their `sort_order`, each annotated with the referenced node's id, `displayName`, full ancestor path, and a `non_trackable` flag set to `true` when the referenced node has `is_active = false` *or* has at least one active child *or* the caller's effective authorization on it is below `track`. Non-trackable entries are returned, not silently removed. [Rationale: UR-03-F07; *Quick access* glossary entry]
- **SR-03-F07.F02** (type F): The system shall expose `POST /api/account/me/quick-access` with body `{ nodeId }` (add), `DELETE /api/account/me/quick-access/{nodeId}` (remove), and `PUT /api/account/me/quick-access/order` with body `{ orderedNodeIds: [UUID, ...] }` (reorder), accepting only requests whose caller has at least `view` effective authorization on the affected node(s). The add endpoint shall reject with HTTP 409 when the maximum quick-access size is exceeded (SR-03-F07.C01). [Rationale: UR-03-F07]
- **SR-03-F07.C01** (type C): The maximum number of quick-access entries per user shall be 9 (aligned with the single-digit keyboard shortcuts 1–9). The limit is a compile-time constant. [Rationale: UR-03-F07]

**UR-03-F08** — create retroactive record.

- **SR-03-F08.F01** (type F): The system shall expose `POST /api/tracking/records` with body `{ nodeId, startedAt, endedAt, description? }`. The endpoint shall reject the call with HTTP 409 if any of the following holds:
  - the target node is not *trackable* for the caller per the glossary definition (`is_active = false` **or** has at least one active child **or** caller's effective authorization is below `track`) — note that for retroactive create the node need not be *active*, only *trackable*;
  - `startedAt` or `endedAt` falls more than 3 years before `NOW()`, or more than 3 years after `NOW()` (per key invariant on time-record bounds);
  - `endedAt < startedAt` (per key invariant);
  - the interval `[startedAt, endedAt)` overlaps any other `time_records` row of the same user (per the no-overlap key invariant of requirements-ur.md);
  - the supplied `description` exceeds the fixed maximum description length (SR-03-F03.C01);
  - the resulting record duration would exceed 24 hours (per the 24-hour cap key invariant).

  On success the system shall insert a `time_records` row in one transaction. No `TrackingChanged` SSE event is emitted because retroactive create does not affect the current open record. [Rationale: UR-03-F08; key invariants]

**UR-03-F09** — edit own time record.

- **SR-03-F09.F01** (type F): The system shall expose `PATCH /api/tracking/records/{id}` with body `{ nodeId?, startedAt?, endedAt?, description? }`, accepting only requests whose caller owns the record. Omitted fields are left unchanged. The endpoint shall reject the call with HTTP 409 if the effective post-update record violates any of the same checks as SR-03-F08.F01, evaluated against all other records of the same user (including any open record, if a closed-record edit would create an overlap). If `nodeId` is changed, the new node must be trackable for the caller per the same definition as SR-03-F08.F01. On success the system shall update the row in one transaction. If the edited record is the currently-open one of the caller, the system shall additionally emit `TrackingChanged`. [Rationale: UR-03-F09; key invariants]

**UR-03-F10** — delete own time record.

- **SR-03-F10.F01** (type F): The system shall expose `DELETE /api/tracking/records/{id}`, accepting only requests whose caller owns the record. On success the system shall delete the row in one transaction. If the deleted record was the caller's currently-open one, the system shall emit `TrackingChanged` with payload `null`. [Rationale: UR-03-F10]

**UR-03-F11** — duplicate own time record.

- **SR-03-F11.F01** (type F): The system shall expose `POST /api/tracking/records/{id}/duplicate` with body `{ startedAt, endedAt }`, accepting only requests whose caller owns the source record. The endpoint shall execute all SR-03-F08.F01 checks against `{ nodeId = source.node_id, startedAt, endedAt, description = source.description }`. On success the system shall insert a new `time_records` row with the source's `node_id` and `description` and the supplied `startedAt`/`endedAt`, in one transaction. The duplicate is independent of the source record from that point onward. [Rationale: UR-03-F11; key invariants]

**UR-03-F12** — live updates across browser sessions plus configurable API-consumer delivery.

- **SR-03-F12.F01** (type F): The system shall emit the following snapshot SSE events per ADR 0017, each delivered to every active SSE session of the targeted user. Snapshot payloads are produced by the same backend service method as the corresponding REST endpoint:

  | Event type | Recipients | Payload shape | Trigger sources |
  |---|---|---|---|
  | `TrackingChanged` | the tracker (per-user) | response of `GET /api/tracking/current` (SR-03-F01.F01) | SR-03-F03.F01, SR-03-F05.F01, SR-03-F06.F01, SR-03-F09.F01 (when editing the open record), SR-03-F10.F01 (when deleting the open record), the 24-hour auto-close lifecycle job (UR-03 key invariant) |
  | `NodeTreeChanged` | every user with at least `view` on the affected subtree after the change | response of `GET /api/nodes/tree` (SR-02-F01.F01) for the recipient | SR-02-F02.F01, SR-02-F03.F01, SR-02-F04.F01, SR-02-F05.F01/F02, SR-02-F06.F01; also when grants change visibility (SR-02-F07.F01, SR-02-F08.F01) |
  | `AuthorizationChanged` | the affected user | response of `GET /api/account/me/authorizations` (SR-05-F04.F01) for the recipient | SR-02-F07.F01, SR-02-F08.F01 |
  | `AccountChanged` | the affected user | response of `GET /api/account/me` (SR-05-F01.F01) for the recipient | SR-05-F02.F02, SR-05-F03.F01 |

  Snapshot events are computed per recipient (a `NodeTreeChanged` event carries each recipient's visible subtree, not the full tree). [Rationale: UR-03-F12; ADR 0017]
- **SR-03-F12.F02** (type F): The system shall additionally emit the command-shaped SSE events listed in architecture §5.3.3 (`InvitationWithdrawn`, `AccountAnonymisedByAdmin`) per ADR 0017 to the affected user(s). [Rationale: UR-03-F12; ADR 0017]
- **SR-03-F12.F03** (type F): The system shall expose, for authenticated Account Holders (session-only per UR-00-C08(a)), the per-user live-update delivery configuration CRUD endpoints under `/api/account/me/webhook-subscriptions`: `GET` (list), `POST` (create), `PATCH /{id}` (update endpoint URL, signing secret rotation, status), and `DELETE /{id}` (revoke). The subscription record persists via the architecture §5.3.3 Subscription persistence port; the raw signing secret is returned exactly once at creation and rotation, mirroring the API-key one-time-return pattern of UR-08-F01. [Rationale: UR-03-F12; architecture §6.3; UR-08-F01 pattern]
- **SR-03-F12.F04** (type F): For every event emitted via SR-03-F12.F01 and SR-03-F12.F02, the system shall additionally write one outbox row per matching webhook subscription into the `webhook_deliveries` persistence (architecture §5.3.3 Webhook outbox persistence port), in the same database transaction as the originating business mutation (transactional outbox pattern per ADR 0017's predecessor decisions). A background `webhook delivery worker` shall POST each outbox row to the subscription's configured endpoint with HMAC signing using the subscription's signing secret, applying retry-and-backoff. Permanently failing deliveries shall be surfaced (architecture §6.3) and shall not silently disappear. At-least-once delivery semantics; subscribers must be idempotent on event consumption. [Rationale: UR-03-F12; architecture §6.3]
- **SR-03-F12.C01** (type C): The webhook delivery worker shall claim outbox rows with `SELECT ... FOR UPDATE SKIP LOCKED` so the same worker invocation never processes a row another concurrent claim is already handling. Single-process operation under UR-00-C12 makes the practical contention zero; the locking discipline is used unconditionally because the cost is negligible and the alternative (claim-by-update without explicit locking) admits race windows whose correctness depends on isolation level and is harder to reason about. [Rationale: UR-03-F12; UR-00-C12]

---

## Epic E-04 — Reporting & export

All reporting endpoints in this epic enforce two scoping rules: (1) only nodes the caller has at least `view` on (via recursive CTE) appear in any response; (2) only **closed** `time_records` rows (where `ended_at IS NOT NULL`) contribute to any aggregate (the open-record exclusion key invariant). Open records are visible only to their owner via the live tracking status (SR-03-F01.F01), never via reports. All endpoints accept both OIDC-session and API-key authentication (per UR-00-C08).

**UR-04-F01** — view aggregated time report.

- **SR-04-F01.F01** (type F): The system shall expose `GET /api/reports/aggregate` with query parameters:
  - `from`: ISO 8601 UTC instant (inclusive); supplied by the frontend after converting the user-local period start to UTC (per UR-00-C10);
  - `to`: ISO 8601 UTC instant (inclusive); supplied by the frontend after converting the user-local period end to UTC (per UR-00-C10);
  - `bucketSize`: one of `hour`, `day`, `week`, `month`, `year` (per UR-04-F01; period selectors such as YTD and MTD are resolved by the frontend into `from`/`to` and are not values on this parameter; the backend imposes no restriction on which bucket size is appropriate for a given `from`/`to` range, the frontend chooses what to expose);
  - `tz`: IANA time-zone identifier (e.g., `Europe/Zurich`) used by the backend to align bucket boundaries to the user's local time via `date_trunc(<bucketSize> AT TIME ZONE <tz>)` per ADR 0019; the backend validates the identifier against `pg_timezone_names` and rejects unknown values with HTTP 400 before executing the aggregation;
  - `nodeId`: optional UUID; when supplied, the report covers the named node and all its descendants that are visible to the caller; when omitted, the report covers every node the caller has at least `view` on;
  - `userId`: optional UUID; when supplied, the report covers only time records owned by that user, subject to the visibility constraint of SR-04-F04.F01;
  - `mode`: one of `summary` or `detailed` (per UR-04-F02).

  The response shall return per-bucket aggregates: for `mode=summary`, one row per (node, time bucket) pair with the total `durationSeconds`; for `mode=detailed`, one row per (node, time bucket, normalised description) tuple. The response shall not expose individual `time_records` rows (UR-04-F01 explicitly forbids per-record exposure in reports; per-record detail is the tracking history surface of UR-03-F02). [Rationale: UR-04-F01; UR-00-C10; ADR 0019; key invariants on closed-only + visibility]
- **SR-04-F01.C01** (type C): The `from` and `to` parameters of SR-04-F01.F01 are UTC instants computed by the frontend from user-local boundaries (per UR-00-C10); the backend does not impose a "full-day boundary" check on the parameter values themselves. Sub-second precision in the instants is accepted as supplied; the backend performs no rounding or truncation outside the `AT TIME ZONE` bucket grouping defined in SR-04-F01.F01. [Rationale: UR-04-F01; UR-00-C10; ADR 0019]

**UR-04-F02** — toggle report between summary, detailed, and chart view.

- **SR-04-F02.F01** (type F): The view-mode toggle (summary/detailed/chart) shall not trigger a new backend query when switching between summary and chart; both views consume the same `mode=summary` dataset from SR-04-F01.F01 with rendering chosen client-side. Switching to or from `detailed` shall re-issue the backend query with the new `mode` parameter (because the detailed dataset includes the description split). [Rationale: UR-04-F02; backend-efficiency principle for chart rendering]

**UR-04-F03** — CSV export of the current report view.

- **SR-04-F03.F01** (type F): The frontend shall produce a CSV download of the current report result set entirely client-side, without re-querying the backend or invoking a server-side rendering endpoint. The CSV shall include a header row carrying the human-readable column names in the active UI language (per SR-00-C18.F01), then one data row per result row in the order the user currently sees them on screen.

  The CSV dialect shall be locale-aware, matched to the dialect that the spreadsheet application of the user's locale auto-detects on import:

  | Active UI dialect | Field separator | Decimal separator (numeric cells) |
  |---|---|---|
  | en-GB | `,` | `.` |
  | de-DE | `;` | `,` |
  | fr-FR | `;` | `,` |
  | es-ES | `;` | `,` |

  The file shall be encoded as UTF-8 with a leading byte-order mark (`EF BB BF`) so Excel-on-Windows recognises the encoding without manual import; line endings shall be CRLF; fields containing the active separator, a quote, or a line break shall be wrapped in double quotes with internal double quotes doubled (RFC 4180 quoting rules adapted to the active separator). [Rationale: UR-04-F03; minimise server load; preserve user-visible ordering; SR-00-C18.F01 locale consistency with spreadsheet auto-import]

**UR-04-F04** — view cross-member aggregates on visible nodes.

- **SR-04-F04.F01** (type F): The system shall expose `GET /api/reports/member-summaries` with query parameters `from`, `to`, `bucketSize`, `tz`, `nodeId?` (same semantics as SR-04-F01.F01, including the ADR 0019 caller-supplied IANA time-zone), returning one row per (target user, node, time bucket) tuple with the total `durationSeconds`. Each row shall include the target user's `id`, `displayName`, and `email` (per UR-00-C11 peer visibility for collision disambiguation), the node's id, `displayName`, and ancestor path, and the bucket start/end. The response shall include only target users for whom the caller has at least `view` effective authorization on at least one node the user contributed time to within the queried period, and shall include only nodes the caller has at least `view` on. The response shall not expose any individual `time_records` row, any record description, any start/end timestamp, or any other per-record attribute (per UR-04-F04 explicit prohibition + GDPR data minimisation). [Rationale: UR-04-F04; UR-00-C10; UR-00-C11; ADR 0019; GDPR data minimisation]

**UR-04-F05** — chart view types.

- **SR-04-F05.F01** (type F): The frontend chart view shall render a single bar chart with time on the X axis (bucketed at the user-selected `bucketSize` from SR-04-F01.F01) and the bucketed total `durationSeconds` on the Y axis. The chart is driven entirely client-side from the dataset already fetched for the summary view (SR-04-F01.F01 with `mode=summary`) using the same active filters and the same `bucketSize`; no additional backend query is triggered when switching between summary view and chart view. The frontend chooses which bucket granularities to offer for a given date range; the backend accepts any supported granularity. Charts are rendered via PrimeNG's existing Chart.js integration; no additional charting dependency shall be added. [Rationale: UR-04-F05; PrimeNG/Chart.js already in fixed frontend stack per architecture §3]

**UR-04-F06** — PDF export of the current report view.

- **SR-04-F06.F01** (type F): The frontend shall produce a PDF download of the current report view entirely client-side using the PDF and table rendering libraries listed in architecture §3 (`jsPDF` with `jsPDF-AutoTable`). The PDF shall use vector rendering for the active view's tabular content (summary or detailed table) — text, table grid, headers, footers, and page numbers — so the resulting document is selectable, searchable, and scalable without resolution loss. The bar chart view, when active, shall be embedded as a bitmap snapshot of the Chart.js canvas at its display size, because the canvas backing model has no clean vector export. The page size shall be A4 portrait for all UI dialects currently shipped (per UR-00-C18: en-GB, de-DE, fr-FR, es-ES); the page-size selection is dialect-driven so that future dialects can map to a different size (e.g., en-US to Letter) without re-architecting the SR. No server-side PDF rendering endpoint shall exist. [Rationale: UR-04-F06; minimise server load; vector output where the source allows; locale-appropriate page size]

**UR-04-F07** — auto-persisted, cross-device report filter state.

- **SR-04-F07.F01** (type F): The system shall expose `GET /api/account/me/report-filters` (session-only per UR-00-C08(a); same scoping as profile reads) returning the caller's last saved report filter state as a JSON document: `{ from, to, bucketSize, tz, nodeId?, userId?, mode }`. If no value has been saved, the endpoint shall return the JSON literal `null`. [Rationale: UR-04-F07]
- **SR-04-F07.F02** (type F): The system shall expose `PUT /api/account/me/report-filters` (session-only) which upserts the caller's report-filter JSON document. The frontend shall call this endpoint debounced at 1 second after the user stops changing filters, so the latest filter state persists across sessions and devices without overwhelming the backend with intermediate writes during continuous filter manipulation. [Rationale: UR-04-F07]
- **SR-04-F07.C01** (type C): The persisted document shall be stored in a single `jsonb` column `last_report_filters` on the `user_profile` table so it travels through the standard backup, anonymisation, and access-termination cleanup paths without bespoke handling. [Rationale: UR-04-F07; aligns with SR-07-F01.F01]

---

## Epic E-05 — Account

**UR-05-F01** — view own profile (name).

- **SR-05-F01.F01** (type F): The system shall expose `GET /api/account/me`, accepting only an OIDC-session-authenticated request (per UR-00-C08(a)), returning the authenticated user's profile: `id` (UUID), `displayName` (from `users` row), `email` (from `users` row per SR-00-C11.C01), `linkedProviders` (the list of registration IDs from `user_oauth_providers`), and `status` (`active`). [Rationale: UR-05-F01; UR-00-C11]
- **SR-05-F01.C01** (type C): API-key-authenticated requests to `GET /api/account/me` shall be rejected per the endpoint classification of SR-00-C08.F01. [Rationale: UR-05-F01; UR-00-C08]

**UR-05-F02** — link additional OIDC provider.

- **SR-05-F02.F01** (type F): The system shall expose `POST /api/account/oidc-providers/link/{provider}` (session-only), which initiates the OIDC authorization-code flow against the named provider and stores the authenticated user's id in the HTTP session for completion. The endpoint shall reject the call with HTTP 400 if the requested `provider` is not configured at startup (per SR-00-C02.F02). [Rationale: UR-05-F02]
- **SR-05-F02.F02** (type F): On the OIDC callback from the link flow initiated by SR-05-F02.F01, the system shall:
  - reject with HTTP 409 and the message "Provider already linked to another account" if the (provider, subject) pair returned by OIDC already exists in `user_oauth_providers` for any user;
  - reject with HTTP 409 and the message "Provider already linked to your account" if the pair already exists for the current authenticated user;
  - otherwise insert a `user_oauth_providers` row linking the (provider, subject) pair to the authenticated user, in a single transaction, and emit the corresponding audit event per SR-06-F01.F01.

  The user's `users.email` is refreshed from the OIDC `email` claim of this callback per SR-00-C11.F01. No identifying information from the OIDC response other than the (provider, subject) pair and the email is persisted. [Rationale: UR-05-F02; UR-00-C11]

**UR-05-F03** — unlink OIDC provider, provided at least one remains.

- **SR-05-F03.F01** (type F): The system shall expose `DELETE /api/account/oidc-providers/{provider}` (session-only), which deletes the `user_oauth_providers` row for the named provider and the authenticated user, in a single transaction. The endpoint shall reject the call with HTTP 409 and the message "Cannot unlink the last linked provider" if doing so would leave the user with zero linked providers. Successful unlink shall emit the corresponding audit event. [Rationale: UR-05-F03]
- **SR-05-F03.C01** (type C): The `users` table shall carry an `anonymised_at TIMESTAMPTZ NULL` column. A non-anonymised user (`anonymised_at IS NULL`) shall hold at least one row in `user_oauth_providers`. The invariant is enforced through the persistence port for `user_oauth_providers`, which exposes two distinct write methods reflecting the two legitimate delete contexts:

  - **`unlinkProvider(user_id, provider)`** — called by the user-driven unlink endpoint SR-05-F03.F01. Its adapter implements the delete as an atomic SQL statement whose `WHERE` clause guards the invariant directly: the row is removed only when the user has `anonymised_at IS NOT NULL` (scrub-time path, allowed) **or** the user retains more than one provider after the delete (non-last unlink, allowed). When neither holds, the statement affects zero rows and the application service of SR-05-F03.F01 returns HTTP 409 with the message "Cannot unlink the last linked provider".

  - **`scrubProviders(user_id)`** — called only by the SR-07-F01.F01 user-termination cleanup, which has already set `users.anonymised_at = NOW()` earlier in the same transaction. The adapter implements this as an unguarded delete of all `user_oauth_providers` rows for the user.

  No other write method on `user_oauth_providers` is exposed by the port. The atomic-SQL guard in `unlinkProvider` makes the unlink path race-safe (no check-then-delete window) and encodes the invariant in the statement that performs the delete; the two-method shape makes the user-driven and cleanup-driven contexts explicit in the application code. [Rationale: UR-05-F03; data integrity invariant enforced by limiting the persistence port API and embedding the constraint in the atomic delete SQL, per ports-and-adapters architecture; the application-layer pre-check in SR-05-F03.F01 serves UX (early friendly error) and is not the enforcement boundary.]

**UR-05-F04** — view own node-authorization assignments.

- **SR-05-F04.F01** (type F): The system shall expose `GET /api/account/me/authorizations` (per UR-00-C08(a), session-only since this is read of the Account Holder's own data), returning the authenticated user's explicit node-authorization assignments — one entry per `node_authorizations` row where `user_id` equals the authenticated user — each carrying: the granted node id, the node's display name, the full ancestor path from the root, and the granted authorization level (`view`, `track`, `admin`). Rights that the user holds only through inheritance from ancestor grants are not enumerated; UR-05-F04 asks to see what was assigned, not the derived effective tree. Effective per-node authorization for any operation is computed by the backend at request time via the recursive-CTE helper (architecture §8.2); the response of this endpoint is the authoritative input to that computation. [Rationale: UR-05-F04; architecture §8.2 recursive authorization model]
- **SR-05-F04.F02** (type F): The same data shape returned by SR-05-F04.F01 shall be the payload of the `AuthorizationChanged` snapshot SSE event (per ADR 0017 and the §5.3.3 event taxonomy). The same backend service method produces the response for the REST query and the push event payload; only one query path exists. [Rationale: UR-05-F04; UR-03-F12; ADR 0017]

**UR-05-F05** — anonymise own account via guided wizard.

- **SR-05-F05.F01** (type F): The system shall expose `POST /api/account/me/anonymise` (session-only), which executes the active-user access-termination cleanup defined by SR-07-F01.F01 with the authenticated user as the target and `account_anonymised(bySelf=true)` as the audit event. The endpoint shall reject the call with HTTP 401 if the caller's HTTP session does not carry an OIDC step-up event (per SR-05-F05.F02 step 2) whose timestamp is within the last 5 minutes; the step-up event consumed by this call shall be cleared from the session on success so it cannot be replayed. The operation is irreversible (SR-07-F01.C01); re-registration requires a new invitation (per the *Anonymization* glossary entry) and produces a fresh `users` row unlinked from the prior stub. [Rationale: UR-05-F05; GDPR right to erasure; step-up enforcement at the API layer so the wizard's intent guarantee cannot be bypassed by direct API calls]
- **SR-05-F05.F02** (type F): The frontend shall present account anonymisation as a multi-step confirmation wizard:
  - **step 1**: explain the consequences in the user's active language (per SR-00-C18.F01): irreversibility, the anonymisation of identifying account data, the retention of historical activity records subject to UR-00-C17 until purge removes them, the necessity of a new invitation for re-registration;
  - **step 2**: initiate an OIDC step-up re-authentication flow against one of the user's currently-linked providers, using `prompt=login` to force a fresh sign-in even when the IdP session is still valid; on successful callback the backend records a step-up event timestamp in the user's HTTP session;
  - **step 3**: on successful step-up return, call `POST /api/account/me/anonymise` from SR-05-F05.F01, then sign the user out and redirect to the login page.

  The wizard is rendered by a Presenter component (per ADR 0013) and the multi-step state is purely UI-local. OIDC step-up is preferred over a typed confirmation phrase because it proves both deliberate intent and fresh identity confirmation from the IdP, avoids per-locale phrase machinery, and reuses the OIDC flow the user already knows. [Rationale: UR-05-F05; informed-consent UX for irreversible actions]

**UR-05-F06** — About page (authenticated; OIDC session or API key).

- **SR-05-F06.F01** (type F): The system shall expose `GET /api/about`, accepting both OIDC-session-authenticated and API-key-authenticated requests (parent UR-05-F06 explicitly permits both auth modes), returning a JSON document with the following fields:
  - `applicationVersion`: the running application version string from build metadata;
  - `thirdPartyLicenses`: an array of `{ name, version, license }` objects covering every direct runtime dependency;
  - `personalDataSummary`: a stable, structured description of what personal data is stored, where, and for how long — the GDPR transparency summary required by UR-05-F06; the structural and content constraints are defined in SR-05-F06.C02;
  - `outboundConnections`: an array of `{ destination, purpose, conditional }` objects enumerating every outbound network connection the deployed instance makes: the OIDC token-exchange endpoints of each provider configured per SR-00-C02.F02 (conditional on operator configuration), and the subscriber endpoints of each active webhook subscription per SR-03-F12.F03 (conditional on user-configured subscriptions); no other outbound connection is made by the application;
  - `disclosureChannelUrl`: a compile-time constant pointing at GitHub's private vulnerability reporting URL for the trawhile project repository;
  - `advisoryChannelUrl`: a compile-time constant pointing at the project's GHSA index (per SR-06-F02.F01);
  - `openApiDownloadUrl`: the path to the auth-gated OpenAPI specification download (per SR-05-F06.F02).

  The endpoint shall reject unauthenticated requests with HTTP 401 (no anonymous access; UR-05-F06). [Rationale: UR-05-F06]
- **SR-05-F06.F02** (type F): The system shall expose `GET /api/about/openapi`, accepting both OIDC-session-authenticated and API-key-authenticated requests, returning the OpenAPI specification of the running application as a downloadable YAML file. Unauthenticated requests shall be rejected with HTTP 401. [Rationale: UR-05-F06; UR-00-C22 (OpenAPI surface auth-gated)]
- **SR-05-F06.C01** (type C): The SBOM shall not be served by the application at any URL; it is published only as a GitHub release artifact (per SR-00-C22.C01). The About-page response shall not include an SBOM download link. [Rationale: UR-05-F06; UR-00-C22]
- **SR-05-F06.C02** (type C): The `personalDataSummary` field of SR-05-F06.F01 shall be a stable structured shape whose semantics do not depend on operator configuration; the deployed instance does not interpolate company-specific text into the GDPR summary (no `privacy-notice-url`, no company name). The summary's content is part of the application source and changes only with application releases. [Rationale: UR-05-F06; GDPR transparency]

---

## Epic E-06 — Security & audit

**UR-06-F01** — emit audit-relevant events into the application log stream.

- **SR-06-F01.F01** (type F): The system shall emit a structured log entry for each of the following audit-relevant events with the structured field `eventType` set to the listed value:
  - `oidc_login_succeeded`
  - `oidc_login_rejected` (carries the rejection cause: `not_invited`, `provider_error`)
  - `oidc_provider_linked` (carries the linked provider registration id)
  - `oidc_provider_unlinked` (carries the unlinked provider registration id)
  - `oidc_step_up_succeeded` (carries the provider registration id and the purpose, e.g., `account_anonymise`)
  - `authorization_check_denied` (carries the target node id and required level)
  - `node_authorization_granted` (carries target user, target node, level)
  - `node_authorization_revoked` (carries target user, target node, previous level)
  - `node_created` (carries the new node id and the parent node id)
  - `node_updated` (carries the node id and the names of the changed attribute fields)
  - `node_moved` (carries the node id, the previous parent id, and the new parent id)
  - `node_deactivated` (carries the node id)
  - `node_reactivated` (carries the node id)
  - `node_purged` (carries the node id; emitted by the retention-purge job per architecture §6.4)
  - `invitation_created` (carries the pre-created user UUID)
  - `invitation_resent`
  - `invitation_withdrawn`
  - `invitation_expired`
  - `user_removed`
  - `account_anonymised` (carries `bySelf` or `byAdmin`)
  - `api_key_generated` (carries the API key UUID and scope summary; never the raw key)
  - `api_key_revoked` (carries the API key UUID and `bySelf` or `byAdmin`)
  - `api_key_used` (one entry per API-key-authenticated request; carries the API key UUID, not the raw key)
  - `webhook_subscription_created` (carries the subscription UUID and the endpoint host)
  - `webhook_subscription_updated` (carries the subscription UUID and the names of the changed fields)
  - `webhook_subscription_deleted` (carries the subscription UUID)
  - `purge_job_started`, `purge_job_chunk_completed`, `purge_job_completed` (carry job type and counts; per architecture §6.4)

  Every such entry shall additionally carry the actor pseudonymous identifier (`actorId`), the target pseudonymous identifier (`targetId` where applicable), a UTC timestamp (per SR-00-C10.C01), and the correlation identifiers required by SR-00-C16.F01. [Rationale: UR-06-F01; architecture §8.5]
- **SR-06-F01.F02** (type F): Audit log entries shall pass through the same redaction pipeline as operational log entries (SR-00-C14.F01); no audit entry shall carry an email address, profile content, request/response body, or other personal data beyond the pseudonymous identifiers listed in SR-06-F01.F01. [Rationale: UR-06-F01; UR-00-C14]
- **SR-06-F01.C01** (type C): Audit events shall be persisted only as application log entries handled by the log pipeline (SR-00-C15.C01). The persistence schema shall not contain a security-events or audit-events table; the application code shall not write audit records to PostgreSQL or Redis. [Rationale: UR-06-F01; architecture §8.5 explicitly rejects an in-app audit store]

**UR-06-F02** — informed of vulnerabilities via project advisory channel; discoverable on About page.

- **SR-06-F02.F01** (type F): The About page (parent UR-05-F06) shall include a labelled hyperlink to the trawhile project's GitHub Security Advisories index page. The link target shall be a compile-time constant that points at the canonical GHSA URL for the project repository; it shall not be operator-configurable. [Rationale: UR-06-F02; UR-05-F06]

**UR-06-F03** — guided in-app subscription to the advisory channel.

- **SR-06-F03.F01** (type F): The admin UI shall include a guided page accessible only to users with effective `admin` on the root node, presenting step-by-step instructions to subscribe to the trawhile project's GHSA notifications by either watching the GitHub repository for security alerts or subscribing the operator's incident-response tooling to the per-repo GHSA Atom feed. The page shall present both options and shall include the URLs needed for each (the repository URL and the Atom feed URL), both as compile-time constants. [Rationale: UR-06-F03; UR-00-C05]

**UR-06-F05** — look up a user by pseudonymised identifier.

- **SR-06-F05.F01** (type F): The admin UI shall include a lookup function, accessible only to users with effective `admin` on the root node, that accepts an internal user UUID and returns: the user's current state (`active`, `pending`, `anonymised`), the user's `displayName`, the user's `email` (from `users.email` per SR-00-C11.C01; `null` for anonymised users), the user's OIDC subject identifiers from `user_oauth_providers`, and the user's authorization assignments via the same query used by UR-01-F07. [Rationale: UR-06-F05; UR-00-C11]
- **SR-06-F05.F02** (type F): The same lookup function shall additionally accept an OIDC subject identifier (provider name + subject) and shall resolve it to the same response shape as SR-06-F05.F01. Resolution uses the `user_oauth_providers` table. If the OIDC subject is not linked to any user (provider link removed via SR-05-F03.F01 unlink or wiped during SR-07-F01.F01 user-termination cleanup, or never registered), the response shall return an explicit "not found" rather than an empty user record. [Rationale: UR-06-F05]
- **SR-06-F05.F03** (type F): The same lookup function shall additionally accept an email address (exact match against `users.email` per SR-00-C11.C01) and shall resolve it to the same response shape as SR-06-F05.F01. If no non-anonymised user holds the supplied email, the response shall additionally check `pending_invitations.email` and return the corresponding pending user where one exists; otherwise return an explicit "not found". Anonymised users are not found by email (their `email` column is `NULL` per SR-00-C11.C01). [Rationale: UR-06-F05; UR-00-C11]

---

## Epic E-07 — Data lifecycle

**UR-07-F01** — atomic access-termination cleanup.

- **SR-07-F01.F01** (type F): When an *active* user's access is terminated (parent UR-01-F06 admin removal or UR-05-F05 self-anonymisation), the system shall execute, within a single PostgreSQL transaction, the entry actions of the *Access Termination Cleanup* state in architecture §6.5, in the following order:
  1. if an open `time_records` row exists for the user, set `ended_at = NOW()` on that row;
  2. delete all `node_authorizations` rows where `user_id` matches the target user;
  3. if a `pending_invitations` row exists for the user (it should not for an active user, but the cleanup is defensive), delete that row;
  4. set `users.anonymised_at = NOW()` and clear all identifying fields on the `users` row (`displayName = NULL`, `email = NULL`, and any other identifying column in the `users` table set to `NULL`); the row continues to exist as the *Anonymised Account Stub* with its UUID preserved (see architecture §6.5);
  5. call the persistence port's `scrubProviders(user_id)` method (per SR-05-F03.C01) to delete all `user_oauth_providers` rows linking external OIDC identities to the user (this step must follow step 4 so that, even if the `unlinkProvider` path were inadvertently used instead, the atomic-SQL guard would see `anonymised_at IS NOT NULL` and allow the deletes);
  6. set `revoked_at = NOW()` on all `api_keys` rows owned by the user that are not already revoked or expired;
  7. invalidate any Spring Session entries associated with the user so currently-open browser tabs are signed out on their next request.

  Transaction commit ends the operation; partial failure rolls the entire cleanup back. The post-condition is that the anonymised stub persists indefinitely (no further job removes it; see SR-07-F01.C01) and no active credentials remain for the user. [Rationale: UR-07-F01; architecture §6.5; SR-05-F03.C01 port interaction]
- **SR-07-F01.F02** (type F): When a *pending* user's access is terminated (parent UR-01-F05 invitation withdrawal or UR-00-C13 invitation expiry), the system shall execute, within a single PostgreSQL transaction:
  - delete the `pending_invitations` row;
  - delete any `node_authorizations` rows that the System Admin may have preassigned to the still-pending user;
  - delete the pre-created `users` row referenced by the deleted invitation.

  No anonymised stub is left behind for pending users (the user never had retained activity records or an active OIDC link to preserve). [Rationale: UR-07-F01; UR-00-C13; *Pending invitation* glossary entry]
- **SR-07-F01.F03** (type F): The cleanup operations of SR-07-F01.F01 and SR-07-F01.F02 shall be invoked by exactly one shared service method (architecture §6.5 single cleanup path) regardless of trigger; the trigger source (admin removal, invitation expiry, invitation withdrawal, self-anonymisation) is recorded as the `actorId` or one of the `bySelf` / `byAdmin` attributes on the emitted audit event (SR-06-F01.F01) but does not branch the cleanup logic. [Rationale: UR-07-F01; single cleanup path per architecture]
- **SR-07-F01.C01** (type C): No scheduled or on-demand job shall delete an Anonymised Account Stub on the grounds of age, inactivity, or empty time-record set. The stub persists for the lifetime of the deployment; the data-retention policy (UR-00-C17) governs only `time_records` and `nodes` and does not extend to anonymised user records (see architecture §6.5 rationale on retention decoupling). [Rationale: UR-07-F01; UR-00-C17; architecture §6.5]
- **SR-07-F01.C02** (type C): The cleanup transaction shall be idempotent: invoking it on a user that is already in the anonymised state (active path) or already removed (pending path) shall complete without throwing and shall make no further state change. [Rationale: UR-07-F01; defensive on replay and on partial-failure recovery]

**UR-07-F02** — backup-creation tooling; restore is operator-documented.

- **SR-07-F02.F01** (type F): The project shall ship a backup-creation tool (the `backup` Docker Compose service per architecture §7) that produces a single self-contained backup artifact for the running deployment. The artifact shall consist of a `pg_dump` of the PostgreSQL database in custom format (`-Fc`), accompanied by a manifest file recording the application version, the database schema version (Flyway migration version), the `pg_dump` version used, and the UTC timestamp at which the dump began. The artifact and manifest are written to a single output directory mounted from the operator-provisioned backup storage target. The tool shall not perform encryption; at-rest encryption of the backup artifact is the operator's responsibility per UR-07-F02 (filesystem, disk, cloud-provider, or backup-target encryption). [Rationale: UR-07-F02; UR-00-C07]
- **SR-07-F02.F02** (type F): The backup tool shall accept its output directory path and any required PostgreSQL connection credentials from environment variables only; it shall not require interactive input and shall be invokable both on operator demand and on an operator-defined schedule (e.g., via the operator's `cron`). [Rationale: UR-07-F02; operator usability]
- **SR-07-F02.F03** (type F): The backup tool shall exit with a non-zero exit code and emit a structured log line classified as a failure when `pg_dump` returns an error, when the output directory is not writable, when the manifest cannot be written, or when free space at the output target is insufficient to hold the projected dump size. [Rationale: UR-07-F02; operator must know when a backup has not succeeded]
- **SR-07-F02.C01** (type C): The project shall not ship a restore tool, restore endpoint, restore command, or in-application restore workflow. The documented restore procedure references standard PostgreSQL tools (`pg_restore` on a fresh empty database, then schema-version verification against the manifest) and is executed by the operator. [Rationale: UR-07-F02; explicit project scope decision]
- **SR-07-F02.C02** (type C): The backup tool, the documented restore procedure, and the manifest format shall be exercised by the automated test suite required by SR-00-C09.F01 (backup-artifact validity tests). Changes to the backup-artifact format that would break the documented restore procedure are caught by these tests at CI time. [Rationale: UR-07-F02; UR-00-C09]

---

## Epic E-08 — API access

All endpoints in this epic are **session-only** (per UR-00-C08(a) — API-key lifecycle is reads and mutations of the Account Holder's own configuration; admin oversight is a System Admin operation). The endpoint classification of SR-00-C08.F01 rejects API-key-authenticated requests on every endpoint in this epic.

**UR-08-F01** — generate a named API key with scope and required expiry; raw key returned once.

- **SR-08-F01.F01** (type F): The system shall expose `POST /api/account/me/api-keys` (session-only) with body `{ name, expiresAt, scope: { nodeId, level } }` where `level ∈ {view, track, admin}`. The scope's `nodeId` selects a single tree node; through the recursive grant rule (architecture §8.2) the key carries `level` on that node and all its descendants. The endpoint shall reject the call with HTTP 409 if any of the following holds:
  - `name` is empty, blank, or exceeds the fixed maximum API-key-name length (SR-08-F01.C01);
  - `expiresAt` is missing, not in the future, or more than the fixed maximum API-key lifetime in the future (SR-08-F01.C02);
  - `scope` is missing or any of its fields is missing;
  - `scope.level` requests a level *higher* than the caller's effective authorization on `scope.nodeId` via recursive CTE (per UR-08-F01 subset constraint);
  - `scope.nodeId` is not visible to the caller (caller's effective authorization is below `view`).

  On success the system shall, in a single transaction: generate a 256-bit cryptographically random raw key; insert an `api_keys` row with `user_id = caller`, `name`, the supplied `scope`, `expires_at`, `key_hash` (SHA-256 of the raw key), `created_at = NOW()`, `revoked_at = NULL`; emit the `api_key_generated` audit event per SR-06-F01.F01 with the new key UUID and a scope summary (never the raw key); return the raw key exactly once in the response body, alongside the new key UUID. The raw key shall not be re-derivable from the row and shall not appear in any log, response, or persisted artifact other than the one-time generation response. To reshape an existing key's scope to a different node or level, the caller revokes the old key (SR-08-F03.F01) and generates a new one. [Rationale: UR-08-F01; architecture §8.4 API-key handling]
- **SR-08-F01.C01** (type C): The API-key `name` field shall be ≤ 64 Unicode characters. [Rationale: UR-08-F01]
- **SR-08-F01.C02** (type C): The maximum API-key lifetime from creation to expiry shall be 1 year. The minimum is implied (any positive future timestamp). [Rationale: UR-08-F01; bounded credential lifetime]

**UR-08-F02** — view own API keys.

- **SR-08-F02.F01** (type F): The system shall expose `GET /api/account/me/api-keys` (session-only), returning the caller's API keys, each carrying: `id` (UUID), `name`, `scope` (the same shape submitted at creation — `{ nodeId, level }` — with the node annotated with its current `displayName` and ancestor path for UI rendering), `createdAt`, `lastUsedAt` (nullable), `expiresAt`, `status` (one of `active`, `expired`, `revoked`). The raw key value is never returned by this endpoint. [Rationale: UR-08-F02]
- **SR-08-F02.F02** (type F): The `status` of each row in SR-08-F02.F01 shall be computed at query time: `revoked` if `revoked_at IS NOT NULL`; otherwise `expired` if `expires_at < NOW()`; otherwise `active`. The status is not stored as a column; it is derived. [Rationale: UR-08-F02; derivation avoids stale state]

**UR-08-F03** — revoke own API key.

- **SR-08-F03.F01** (type F): The system shall expose `POST /api/account/me/api-keys/{id}/revoke` (session-only), accepting only requests whose caller owns the named key. The endpoint shall set `revoked_at = NOW()` on the row in one transaction; subsequent presentation of the raw key value at any authenticated endpoint shall be rejected with HTTP 401 (the SR-08-F03.C01 invariant). The endpoint shall emit the `api_key_revoked(bySelf=true)` audit event. Revoking an already-revoked key is a no-op success. [Rationale: UR-08-F03]
- **SR-08-F03.C01** (type C): The authentication layer's API-key validation (architecture §5.2 Security/OIDC adapter; SR-00-C08.F01) shall, for every incoming API-key-authenticated request, reject the request when the corresponding `api_keys` row has `revoked_at IS NOT NULL` or `expires_at < NOW()`, before the request enters use-case handling. This rejection is observed as `trawhile_api_key_use_total{outcome="rejected_revoked"}` or `trawhile_api_key_use_total{outcome="rejected_expired"}` per SR-01-F10.F02. [Rationale: UR-08-F03; terminal-state invariant]
- **SR-08-F03.C02** (type C): The persistence port for `api_keys` shall expose only the following write operations: insert a new key row (called by SR-08-F01.F01), set `revoked_at = NOW()` for a given key id (called by SR-08-F03.F01 and SR-08-F06.F01), and set `last_used_at = NOW()` for a given key id (called by the authentication layer on successful API-key use). No write operation that modifies `name`, `scope`, `expires_at`, `key_hash`, `user_id`, or `created_at` shall be exposed by the port. The application is therefore structurally incapable of mutating these columns. Rotation by revoke-and-reissue is the only way to change a key's name, scope, or expiry: the caller revokes the old key and generates a new one with the desired attributes. [Rationale: UR-08-F03; rotation-by-revoke-and-reissue pattern; immutability invariant enforced by limiting the persistence port API per ports-and-adapters architecture.]

**UR-08-F05** — System Admin view of all API keys.

- **SR-08-F05.F01** (type F): The system shall expose `GET /api/admin/api-keys` (session-only; effective `admin` on the root node), returning all `api_keys` rows with the same row shape as SR-08-F02.F01 *plus* the owning user's UUID and `displayName`. Pagination and a substring filter on `name` shall be supported. [Rationale: UR-08-F05]

**UR-08-F06** — System Admin revoke any API key.

- **SR-08-F06.F01** (type F): The system shall expose `POST /api/admin/api-keys/{id}/revoke` (session-only; effective `admin` on the root node), accepting any API key regardless of owner. The endpoint shall set `revoked_at = NOW()` on the row in one transaction and emit the `api_key_revoked(bySelf=false, actorId=<admin uuid>)` audit event per SR-06-F01.F01. Revoking an already-revoked key is a no-op success. [Rationale: UR-08-F06]
