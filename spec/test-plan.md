# Test plan

Traceability chain: **UR → SR → TE-{SR-id}-nn**. Every SR of type F or Q maps to at least one happy-path test and, where the SR defines a rejection condition, at least one error test. SR of type C have no required TEs.

Test IDs follow the pattern `TE-{SR-id}-{nn}` where `{SR-id}` is the SR identifier with its `SR-` prefix stripped (e.g. `01-F01.F01`) and `{nn}` is a two-digit sequence within that SR. Example: `TE-01-F01.F01-01`. `@Tag("TE-…")` is applied to every backend test method for CI filtering and traceability.

## Test categories

| Code | Meaning | Tooling |
|---|---|---|
| `IT` | Integration test against a real PostgreSQL instance | `@SpringBootTest` + Testcontainers `postgres:17` reused per suite via `BaseIT` |
| `UT` | Unit test, no Spring context, no DB | Plain JUnit 5 |
| `SIT` | HTTP contract surface test against the controller layer, no DB | `@WebMvcTest` + `MockMvc` |
| `CT` | Contract test verifying the running implementation against `spec/openapi.yaml` and `spec/schema.sql` | Schemathesis (or `springdoc-openapi` runtime validation) for OpenAPI; pgTAP-style assertions for schema |
| `ACT` | Angular component test under TestBed | `*.spec.ts` with `@angular/core/testing` |
| `E2E` | End-to-end test driving the full deployed stack via a real browser | Playwright against the local Docker Compose deployment |

All `IT` tests extend `BaseIT` which starts the Postgres container once per suite via `@Testcontainers` + `@Container(reuse = true)`.

Every endpoint SR (one that defines an HTTP method + path) carries at least one `CT` row in addition to its `IT` rows; the `CT` row asserts that the implementation's request/response shapes, status codes, and error format conform to `spec/openapi.yaml`. Every SR that adds or constrains a database column or trigger carries at least one `CT` row asserting that the live schema matches `spec/schema.sql`.

## Coverage rule

For each F or Q SR:
- At least one happy-path TE.
- At least one error-path TE when the SR defines explicit rejection conditions (HTTP 4xx outcomes, constraint violations, etc.).
- One CT row for endpoint SRs and for SRs constraining persisted schema shape.

For C-type SRs: TEs are optional; included only when the constraint is observable through behaviour the test can exercise.

---

## Epic E-00 — Cross-cutting constraints

| TE | SR | UR | Type | Class | Test description |
|---|---|---|---|---|---|
| TE-00-C02.F01-01 | SR-00-C02.F01 | UR-00-C02 | IT | `ConfigStartupIT` | Startup with no OIDC provider configured fails with non-zero exit and an error log naming the missing property |
| TE-00-C02.F02-01 | SR-00-C02.F02 | UR-00-C02 | IT | `ConfigStartupIT` | Startup with one configured provider exposes its registration id via the discovery endpoint used by the login page |
| TE-00-C03.F01-01 | SR-00-C03.F01 | UR-00-C03 | IT | `InvitationIT` | `mailto:` URI is built server-side by invitation creation and contains the application base URL, invitee email, and sign-in instruction; no SMTP send or invitation token is used |
| TE-00-C08.F01-01 | SR-00-C08.F01 | UR-00-C08 | IT | `AuthAdapterIT` | API-key bearer on `/api/account/me` is rejected (session-only endpoint per SR-05-F01.C01) |
| TE-00-C08.F01-02 | SR-00-C08.F01 | UR-00-C08 | IT | `AuthAdapterIT` | OIDC session on a tracking endpoint succeeds; API-key bearer on same endpoint succeeds |
| TE-00-C08.F02-01 | SR-00-C08.F02 | UR-00-C08 | IT | `AuthAdapterIT` | OIDC-session-cookie request to `/api/mcp` is rejected with HTTP 401; API-key-bearer request to `/api/mcp` is accepted (auth-success) |
| TE-00-C09.F01-01 | SR-00-C09.F01 | UR-00-C09 | IT | `BackupArtifactIT` | Backup tool produces `pg_dump -Fc` artifact + manifest; restore via documented procedure yields a structurally equivalent database |
| TE-00-C10.F01-01 | SR-00-C10.F01 | UR-00-C10 | CT | `SchemaCT` | All timestamp columns in `spec/schema.sql` use `TIMESTAMPTZ`; no column carries a separate timezone or offset field |
| TE-00-C10.C01-01 | SR-00-C10.C01 | UR-00-C10 | UT | `TimeFormatTest` | All Java timestamp types in port DTOs and event payloads are `java.time.Instant` (UTC by construction) — never `LocalDateTime`, `ZonedDateTime`, or `OffsetDateTime` |
| TE-00-C10.F02-01 | SR-00-C10.F02 | UR-00-C10 | IT | `ReportTzIT` | Report endpoints accept a caller-supplied IANA TZ; backend uses `AT TIME ZONE` for bucket grouping; invalid IANA name returns HTTP 400 |
| TE-00-C11.F01-01 | SR-00-C11.F01 | UR-00-C11 | IT | `AccountEmailIT` | OIDC callback persists `users.email` from the `email` claim on bootstrap, invitation-match, and known-identity-login paths; subsequent login refreshes the column |
| TE-00-C11.F02-01 | SR-00-C11.F02 | UR-00-C11 | IT | `AccountEmailIT` | Backend returns `null` for `displayName` and `email` on user references where `users.anonymised_at IS NOT NULL` |
| TE-00-C11.F02-02 | SR-00-C11.F02 | UR-00-C11 | ACT | `anonymisedUserLabel.spec.ts` | Frontend renders the `account.anonymisedUserLabel` translation key when displayName is null and anonymised marker is implied by context |
| TE-00-C13.F01-01 | SR-00-C13.F01 | UR-00-C13 | IT | `InvitationExpiryIT` | Lifecycle job deletes `pending_invitations` rows and their pre-created `users` rows ≥ 90 days after creation |
| TE-00-C14.F01-01 | SR-00-C14.F01 | UR-00-C14 | IT | `LogRedactionIT` | Log entries emitted by the redaction pipeline never contain email, OIDC subject, or request body content |
| TE-00-C16.F01-01 | SR-00-C16.F01 | UR-00-C16 | IT | `LogCorrelationIT` | Every log entry carries `traceId`, `requestId`, `sessionId` (when applicable), and pseudonymised `actorId` (when actor known) |
| TE-00-C17.F01-01 | SR-00-C17.F01 | UR-00-C17 | IT | `RetentionPurgeIT` | Purge job deletes `time_records` rows whose counted-duration end is past the 3-year boundary; rows inside boundary survive |
| TE-00-C17.F02-01 | SR-00-C17.F02 | UR-00-C17 | IT | `RetentionPurgeIT` | Purge job deletes nodes whose subtree holds no `time_records` and whose own creation time is past 3 years, processed bottom-up |
| TE-00-C18.F01-01 | SR-00-C18.F01 | UR-00-C18 | ACT | `localeResolution.spec.ts` | `navigator.language` `en-US` resolves to `en-GB`; `de-CH` resolves to `de-DE`; unknown language falls back to `en-GB` |
| TE-00-C18.F02-01 | SR-00-C18.F02 | UR-00-C18 | ACT | `languageSwitcher.spec.ts` | Language switcher renders each dialect via `Intl.DisplayNames` endonym: `English (United Kingdom)`, `Deutsch (Deutschland)`, etc. |
| TE-00-C19.C01-01 | SR-00-C19.C01 | UR-00-C19 | CT | `CaddyConfigCT` | `deploy/caddy/Caddyfile` plus `spec/openapi.yaml` and the SPA build manifest jointly enumerate every routed external URL of the deployment; every such URL falls into exactly one of the four classes named in SR-00-C19.C01 (SPA static, OIDC authorization flow, OIDC discovery, `/api/*`) |
| TE-00-C19.F01-01 | SR-00-C19.F01 | UR-00-C19 | CT | `CaddyConfigCT` | `deploy/caddy/Caddyfile` declares token-bucket rate-limit directives covering every endpoint in the set defined by SR-00-C19.C01 — the SPA static surface, the OIDC authorization flow (`/oauth2/authorization/**`, `/login/oauth2/code/**`, `/logout`), `/auth/providers`, and `/api/*` (which includes `/api/mcp`); the rate-limit module is configured to emit Prometheus metrics |
| TE-00-C20.F01-01 | SR-00-C20.F01 | UR-00-C20 | E2E | `responsiveLayout.spec.ts` | Tracking page renders correctly at 360px, 768px, and 1280px viewport widths |
| TE-00-C21.F01-01 | SR-00-C21.F01 | UR-00-C21 | SIT | `CsrfConfigSIT` | `Authorization: Bearer` requests bypass `CsrfFilter`; session-only requests without CSRF token are rejected |
| TE-00-C22.F01-01 | SR-00-C22.F01 | UR-00-C22 | IT | `EdgeProtectionIT` | Unauthenticated requests receive a generic error; no version, OpenAPI, or outbound-connection information leaks via headers or error bodies |

---

## Epic E-01 — System administration

| TE | SR | UR | Type | Class | Test description |
|---|---|---|---|---|---|
| TE-01-F01.F01-01 | SR-01-F01.F01 | UR-01-F01 | IT | `BootstrapIT` | First OIDC callback whose email matches `BOOTSTRAP_ADMIN_EMAIL` with no existing root-admin grant inserts users + provider link + root-admin row in one transaction; session established; audit event `oidc_login_succeeded` with `bootstrap=true` |
| TE-01-F01.F01-02 | SR-01-F01.F01 | UR-01-F01 | IT | `BootstrapIT` | Bootstrap with mismatched email redirects to `not_invited` and emits `oidc_login_rejected(cause=not_invited)` |
| TE-01-F01.F02-01 | SR-01-F01.F02 | UR-01-F01 | IT | `BootstrapIT` | After a root-admin grant exists, the bootstrap outcome no longer fires; matching email proceeds via invitation-match or known-identity-login |
| TE-01-F02.F01-01 | SR-01-F02.F01 | UR-01-F02 | IT | `UserManagementIT` | Admin user list returns all users with correct `status` derivation, `displayName`, `email` (null for anonymised), and OIDC-provider count |
| TE-01-F02.F01-02 | SR-01-F02.F01 | UR-01-F02 | IT | `UserManagementIT` | Non-admin caller receives HTTP 403 |
| TE-01-F02.F01-03 | SR-01-F02.F01 | UR-01-F02 | CT | `AdminUsersCT` | `GET /api/admin/users` request/response shapes conform to `spec/openapi.yaml` |
| TE-01-F03.F01-01 | SR-01-F03.F01 | UR-01-F03 | IT | `InvitationIT` | Admin invitations list returns the invitation id, invitee email, inviter id and display name, timestamps, and pre-assigned authorization count |
| TE-01-F03.F01-02 | SR-01-F03.F01 | UR-01-F03 | CT | `AdminInvitationsCT` | `GET /api/admin/invitations` conforms to `spec/openapi.yaml` |
| TE-01-F04.F01-01 | SR-01-F04.F01 | UR-01-F04 | IT | `InvitationIT` | Create invitation with new email inserts `users` (pending) + `pending_invitations` rows in one transaction; emits `invitation_created`; returns `mailto:` link |
| TE-01-F04.F01-02 | SR-01-F04.F01 | UR-01-F04 | IT | `InvitationIT` | Create invitation with email matching an existing non-anonymised user returns HTTP 409 |
| TE-01-F04.F01-03 | SR-01-F04.F01 | UR-01-F04 | IT | `InvitationIT` | Create invitation with email matching a non-expired pending invitation returns HTTP 409 |
| TE-01-F04.F01-04 | SR-01-F04.F01 | UR-01-F04 | CT | `AdminInvitationsCT` | `POST /api/admin/invitations` conforms to `spec/openapi.yaml` |
| TE-01-F05.F01-01 | SR-01-F05.F01 | UR-01-F05 | IT | `InvitationIT` | Withdraw invitation runs SR-07-F01.F02 cleanup on pre-created user; emits `invitation_withdrawn`; deletes both rows in one transaction |
| TE-01-F05.F01-02 | SR-01-F05.F01 | UR-01-F05 | IT | `InvitationIT` | Withdraw by non-admin returns HTTP 403 |
| TE-01-F05.F01-03 | SR-01-F05.F01 | UR-01-F05 | CT | `AdminInvitationsCT` | `DELETE /api/admin/invitations/{id}` conforms to `spec/openapi.yaml` |
| TE-01-F06.F01-01 | SR-01-F06.F01 | UR-01-F06 | IT | `UserManagementIT` | Admin removal runs SR-07-F01.F01 cleanup on the target user; emits `user_removed` |
| TE-01-F06.F01-02 | SR-01-F06.F01 | UR-01-F06 | IT | `UserManagementIT` | Admin removal of the last root-admin user returns HTTP 409 |
| TE-01-F06.F01-03 | SR-01-F06.F01 | UR-01-F06 | CT | `AdminUsersCT` | `POST /api/admin/users/{id}/remove` conforms to `spec/openapi.yaml` |
| TE-01-F06.F02-01 | SR-01-F06.F02 | UR-01-F06 | E2E | `removeUserWizard.spec.ts` | Multi-step removal wizard: step 1 shows displayName + grants + active record; step 2 explains consequences in active dialect; step 3 confirms and invokes the remove endpoint |
| TE-01-F07.F01-01 | SR-01-F07.F01 | UR-01-F07 | IT | `UserManagementIT` | Admin-side authorizations list returns explicit grants for the target user (same shape as SR-05-F04.F01) |
| TE-01-F07.F01-02 | SR-01-F07.F01 | UR-01-F07 | CT | `AdminUsersCT` | `GET /api/admin/users/{id}/authorizations` conforms to `spec/openapi.yaml` |
| TE-01-F07.F02-01 | SR-01-F07.F02 | UR-01-F07 | IT | `UserManagementIT` | Admin-side grant via user-view endpoint delegates to the same service method as SR-02-F07.F01 and enforces the same invariants (caller has admin on the affected node; not granting to self) |
| TE-01-F07.F02-02 | SR-01-F07.F02 | UR-01-F07 | IT | `UserManagementIT` | Admin-side revoke via user-view endpoint delegates to the same service method as SR-02-F08.F01; rejects when revoke would leave the node without effective admin |
| TE-01-F07.F02-03 | SR-01-F07.F02 | UR-01-F07 | CT | `AdminUsersCT` | `POST` and `DELETE /api/admin/users/{id}/authorizations[/{nodeId}]` conform to `spec/openapi.yaml` |
| TE-01-F08.F01-01 | SR-01-F08.F01 | UR-01-F08 | IT | `InvitationIT` | Resend invitation updates `expires_at = NOW() + 90d`, returns fresh `mailto:` link, retains existing UUID, emits `invitation_resent` |
| TE-01-F08.F01-02 | SR-01-F08.F01 | UR-01-F08 | CT | `AdminInvitationsCT` | `POST /api/admin/invitations/{id}/resend` conforms to `spec/openapi.yaml` |
| TE-01-F09.F01-01 | SR-01-F09.F01 | UR-01-F09 | IT | `UserManagementIT` | Admin dashboard "pending invitations" count equals the count from the loaded users list, computed presenter-side |
| TE-01-F10.F01-01 | SR-01-F10.F01 | UR-01-F10 | IT | `MetricsIT` | `/actuator/prometheus` exposes Micrometer + Spring + HikariCP standard metric series under the application's management port |
| TE-01-F10.F02-01 | SR-01-F10.F02 | UR-01-F10 | IT | `MetricsIT` | All custom Micrometer counters / gauges named in SR-01-F10.F02 are registered and exposed at `/actuator/prometheus` |
| TE-01-F10.F03-01 | SR-01-F10.F03 | UR-01-F10 | IT | `MetricsArtifactsIT` | `deploy/monitoring/` directory contains the three operator artifacts; alerting rule definitions reference only metric names declared in SR-01-F10.F02 |
| TE-01-F11.C01-01 | SR-01-F11.C01 | UR-01-F11 | IT | `LogPipelineIT` | Application emits no log-viewer endpoint; Loki + Promtail reference deployment artifacts present in `deploy/` |
| TE-01-F12.F01-01 | SR-01-F12.F01 | UR-01-F12 | IT | `ConfigStartupIT` | Configuration validation fails fast on missing required OIDC provider config; non-zero exit + descriptive error log naming the failing property |
| TE-01-F13.F01-01 | SR-01-F13.F01 | UR-01-F13 | IT | `AuthFlowIT` | OIDC callback classifies each outcome correctly: bootstrap, invitation match, provider linking, known-identity login, rejected |
| TE-01-F13.F01-02 | SR-01-F13.F01 | UR-01-F13 | IT | `AuthFlowIT` | Invitation-match callback links provider + activates user + deletes invitation in one transaction; emits `oidc_login_succeeded` |
| TE-01-F13.F01-03 | SR-01-F13.F01 | UR-01-F13 | IT | `AuthFlowIT` | Rejected callback redirects to `/login?error=not_invited`; emits `oidc_login_rejected(cause=not_invited)`; does not reveal whether email is known |
| TE-01-F13.F02-01 | SR-01-F13.F02 | UR-01-F13 | ACT | `loginPage.spec.ts` | Login page displays a sign-in button per registration id; explanatory invitation-only note; About-page link; renders `not_invited` message when query parameter present |
| TE-01-F14.F01-01 | SR-01-F14.F01 | UR-01-F14 | IT | `SessionIT` | Session backed by Spring Session in Redis; cookie `Secure`, `HttpOnly`, `SameSite=Lax`; 12-hour inactivity timeout; sign-out deletes session from Redis |
| TE-01-F14.F02-01 | SR-01-F14.F02 | UR-01-F14 | IT | `SessionIT` | Anonymisation and admin removal delete the target user's Spring Session entries from Redis in the cleanup transaction |

---

## Epic E-02 — Node administration

| TE | SR | UR | Type | Class | Test description |
|---|---|---|---|---|---|
| TE-02-F01.F01-01 | SR-02-F01.F01 | UR-02-F01 | IT | `NodeIT` | `GET /api/nodes/tree` returns the caller's full visible nested tree with full attributes per node including effective auth level; nodes outside visibility do not appear |
| TE-02-F01.F01-02 | SR-02-F01.F01 | UR-02-F01 | IT | `NodeIT` | Anonymous request returns HTTP 401; authenticated caller with no grants returns an empty tree |
| TE-02-F01.F01-03 | SR-02-F01.F01 | UR-02-F01 | CT | `NodesCT` | `GET /api/nodes/tree` conforms to `spec/openapi.yaml` |
| TE-02-F02.F01-01 | SR-02-F02.F01 | UR-02-F02 | IT | `NodeIT` | Create child with admin caller acquires sibling lock and inserts with `sort_order = MAX+1`; emits `NodeTreeChanged` and `node_created` audit event |
| TE-02-F02.F01-02 | SR-02-F02.F01 | UR-02-F02 | IT | `NodeIT` | Concurrent create-child under same parent yields strictly increasing `sort_order` values without duplicates |
| TE-02-F02.F01-03 | SR-02-F02.F01 | UR-02-F02 | IT | `NodeIT` | Non-admin caller returns HTTP 403; logo field is rejected on create |
| TE-02-F02.F01-04 | SR-02-F02.F01 | UR-02-F02 | CT | `NodesCT` | `POST /api/nodes/{parentId}/children` conforms to `spec/openapi.yaml` |
| TE-02-F03.F01-01 | SR-02-F03.F01 | UR-02-F03 | IT | `NodeIT` | PATCH updates only supplied non-null fields; explicit `null` clears nullable fields; `logo` decoded from base64 and stored; emits `NodeTreeChanged` + `node_updated` |
| TE-02-F03.F01-02 | SR-02-F03.F01 | UR-02-F03 | IT | `NodeIT` | Non-admin caller returns HTTP 403 |
| TE-02-F03.F01-03 | SR-02-F03.F01 | UR-02-F03 | CT | `NodesCT` | `PATCH /api/nodes/{id}` conforms to `spec/openapi.yaml` |
| TE-02-F03.F02-01 | SR-02-F03.F02 | UR-02-F03 | IT | `NodeIT` | Logo payload with decoded byte length > 256 KB returns HTTP 413; unsupported MIME type returns HTTP 415; both checks fire before persistence |
| TE-02-F04.F01-01 | SR-02-F04.F01 | UR-02-F04 | IT | `NodeIT` | Reorder updates all sibling `sort_order` values to the submitted index in one transaction; emits `NodeTreeChanged` |
| TE-02-F04.F01-02 | SR-02-F04.F01 | UR-02-F04 | IT | `NodeIT` | Submitted child list with additions or removals returns HTTP 409; non-admin returns HTTP 403 |
| TE-02-F04.F01-03 | SR-02-F04.F01 | UR-02-F04 | CT | `NodesCT` | `PUT /api/nodes/{id}/children/order` conforms to `spec/openapi.yaml` |
| TE-02-F05.F01-01 | SR-02-F05.F01 | UR-02-F05 | IT | `NodeIT` | Deactivate locks subtree, sets `is_active = false` and `deactivated_at = NOW()`; emits `NodeTreeChanged` + `node_deactivated` |
| TE-02-F05.F01-02 | SR-02-F05.F01 | UR-02-F05 | IT | `NodeIT` | Deactivate with any active descendant returns HTTP 409; concurrent child-create blocked by subtree lock |
| TE-02-F05.F01-03 | SR-02-F05.F01 | UR-02-F05 | IT | `NodeIT` | Open `time_records` on the node itself does not block deactivation |
| TE-02-F05.F02-01 | SR-02-F05.F02 | UR-02-F05 | IT | `NodeIT` | Reactivate sets `is_active = true`, `deactivated_at = NULL`; emits `NodeTreeChanged` + `node_reactivated` |
| TE-02-F05.F01-04 | SR-02-F05.F01 | UR-02-F05 | CT | `NodesCT` | `POST /api/nodes/{id}/deactivate` and `POST /api/nodes/{id}/reactivate` conform to `spec/openapi.yaml` |
| TE-02-F06.F01-01 | SR-02-F06.F01 | UR-02-F06 | IT | `NodeIT` | Move updates `parent_id` and appends to destination's `sort_order`; emits `NodeTreeChanged` + `node_moved` |
| TE-02-F06.F01-02 | SR-02-F06.F01 | UR-02-F06 | IT | `NodeIT` | Move into own descendant returns HTTP 409; caller without admin on destination returns HTTP 403 |
| TE-02-F06.F01-03 | SR-02-F06.F01 | UR-02-F06 | CT | `NodesCT` | `POST /api/nodes/{id}/move` conforms to `spec/openapi.yaml` |
| TE-02-F07.F01-01 | SR-02-F07.F01 | UR-02-F07 | IT | `AuthorizationIT` | Grant upserts `(user_id, node_id)` direct row to requested level; inherited grants untouched; emits `AuthorizationChanged` and `node_authorization_granted` |
| TE-02-F07.F01-02 | SR-02-F07.F01 | UR-02-F07 | IT | `AuthorizationIT` | Grant where `body.userId == caller.user_id` returns HTTP 409 (no self-CRUD) |
| TE-02-F07.F01-03 | SR-02-F07.F01 | UR-02-F07 | IT | `AuthorizationIT` | Grant to an anonymised target user returns HTTP 409 |
| TE-02-F07.F01-04 | SR-02-F07.F01 | UR-02-F07 | IT | `AuthorizationIT` | Grant by non-admin returns HTTP 403; granting `admin` on root confers System Admin rights |
| TE-02-F07.F01-05 | SR-02-F07.F01 | UR-02-F07 | CT | `AuthorizationsCT` | `POST /api/nodes/{nodeId}/authorizations` conforms to `spec/openapi.yaml` |
| TE-02-F07.F02-01 | SR-02-F07.F02 | UR-02-F07 | ACT | `permissionsExplainer.spec.ts` | UI shows the level-description table before any submit button is enabled; "authorizations inherited downward" note present |
| TE-02-F08.F01-01 | SR-02-F08.F01 | UR-02-F08 | IT | `AuthorizationIT` | Revoke deletes the direct grant row; emits `AuthorizationChanged` and `node_authorization_revoked` |
| TE-02-F08.F01-02 | SR-02-F08.F01 | UR-02-F08 | IT | `AuthorizationIT` | Revoke where `{userId} == caller.user_id` returns HTTP 409 (no self-CRUD) |
| TE-02-F08.F01-03 | SR-02-F08.F01 | UR-02-F08 | IT | `AuthorizationIT` | Revoke that would leave the node without any user holding effective admin (combining direct + inherited) returns HTTP 409 |
| TE-02-F08.F01-04 | SR-02-F08.F01 | UR-02-F08 | IT | `AuthorizationIT` | Revoke by non-admin returns HTTP 403 |
| TE-02-F08.F01-05 | SR-02-F08.F01 | UR-02-F08 | CT | `AuthorizationsCT` | `DELETE /api/nodes/{nodeId}/authorizations/{userId}` conforms to `spec/openapi.yaml` |
| TE-02-F09.F01-01 | SR-02-F09.F01 | UR-02-F09 | IT | `AuthorizationIT` | Node authorizations list returns target user info (id, displayName, email), granted level, and direct/inherited flag with ancestor path for inherited rows |
| TE-02-F09.F01-02 | SR-02-F09.F01 | UR-02-F09 | IT | `AuthorizationIT` | Caller with only `view` on the node sees the authorizations list; caller without any grant returns HTTP 403 |
| TE-02-F09.F01-03 | SR-02-F09.F01 | UR-02-F09 | CT | `AuthorizationsCT` | `GET /api/nodes/{nodeId}/authorizations` conforms to `spec/openapi.yaml` |

---

## Epic E-03 — Time tracking

| TE | SR | UR | Type | Class | Test description |
|---|---|---|---|---|---|
| TE-03-F01.F01-01 | SR-03-F01.F01 | UR-03-F01 | IT | `TrackingIT` | `GET /api/tracking/current` returns the caller's open record (`ended_at IS NULL`) with `elapsedSeconds` computed from `NOW() - started_at`; returns `null` when no open record |
| TE-03-F01.F01-02 | SR-03-F01.F01 | UR-03-F01 | CT | `TrackingCT` | `GET /api/tracking/current` conforms to `spec/openapi.yaml` |
| TE-03-F01.F02-01 | SR-03-F01.F02 | UR-03-F01 | IT | `TrackingSseIT` | `TrackingChanged` snapshot SSE event is pushed on each open-record state change; payload shape equals `GET /api/tracking/current` response |
| TE-03-F02.F01-01 | SR-03-F02.F01 | UR-03-F02 | IT | `TrackingIT` | Tracking history returns closed records in descending `startedAt`; open record excluded; each row carries id, nodeId, ancestor path, timestamps, duration, description |
| TE-03-F02.F01-02 | SR-03-F02.F01 | UR-03-F02 | CT | `TrackingCT` | `GET /api/tracking/history` conforms to `spec/openapi.yaml` |
| TE-03-F03.F01-01 | SR-03-F03.F01 | UR-03-F03 | IT | `TrackingIT` | Start with valid node opens a new record at `NOW()`; emits `TrackingChanged` with payload of the new record |
| TE-03-F03.F01-02 | SR-03-F03.F01 | UR-03-F03 | IT | `TrackingIT` | Start when caller already has an open record returns HTTP 409 |
| TE-03-F03.F01-03 | SR-03-F03.F01 | UR-03-F03 | IT | `TrackingIT` | Start on a node where caller has below `track` returns HTTP 403; on a deactivated node returns HTTP 409 |
| TE-03-F03.F01-04 | SR-03-F03.F01 | UR-03-F03 | IT | `TrackingIT` | Description exceeding the 256-char limit (SR-03-F03.C01) returns HTTP 409 |
| TE-03-F03.F01-05 | SR-03-F03.F01 | UR-03-F03 | CT | `TrackingCT` | `POST /api/tracking/start` conforms to `spec/openapi.yaml` |
| TE-03-F04.F01-01 | SR-03-F04.F01 | UR-03-F04 | IT | `TrackingIT` | Quick-access start delegates to the same `POST /api/tracking/start` backend service method; identical validation surface |
| TE-03-F05.F01-01 | SR-03-F05.F01 | UR-03-F05 | IT | `TrackingIT` | Switch closes open record at `NOW()` and opens new one at `NOW()` in single transaction; emits one `TrackingChanged` carrying the new open record |
| TE-03-F05.F01-02 | SR-03-F05.F01 | UR-03-F05 | IT | `TrackingIT` | Switch when caller has no open record returns HTTP 409 |
| TE-03-F05.F01-03 | SR-03-F05.F01 | UR-03-F05 | CT | `TrackingCT` | `POST /api/tracking/switch` conforms to `spec/openapi.yaml` |
| TE-03-F06.F01-01 | SR-03-F06.F01 | UR-03-F06 | IT | `TrackingIT` | Stop sets `ended_at = NOW()` on open record; emits `TrackingChanged` with `null` payload |
| TE-03-F06.F01-02 | SR-03-F06.F01 | UR-03-F06 | IT | `TrackingIT` | Stop when no open record returns HTTP 409 |
| TE-03-F06.F01-03 | SR-03-F06.F01 | UR-03-F06 | CT | `TrackingCT` | `POST /api/tracking/stop` conforms to `spec/openapi.yaml` |
| TE-03-F07.F01-01 | SR-03-F07.F01 | UR-03-F07 | IT | `QuickAccessIT` | Quick-access list returns entries in `sort_order` with `non_trackable` flag set correctly when node is inactive, has active children, or caller's effective level < `track` |
| TE-03-F07.F01-02 | SR-03-F07.F01 | UR-03-F07 | CT | `AccountCT` | `GET /api/account/me/quick-access` conforms to `spec/openapi.yaml` |
| TE-03-F07.F02-01 | SR-03-F07.F02 | UR-03-F07 | IT | `QuickAccessIT` | Add / remove / reorder quick-access entries; add beyond 9 returns HTTP 409 |
| TE-03-F07.F02-02 | SR-03-F07.F02 | UR-03-F07 | CT | `AccountCT` | Quick-access mutation endpoints conform to `spec/openapi.yaml` |
| TE-03-F08.F01-01 | SR-03-F08.F01 | UR-03-F08 | IT | `TrackingIT` | Retroactive create inserts closed record with supplied timestamps; emits no `TrackingChanged` (open-record unchanged) |
| TE-03-F08.F01-02 | SR-03-F08.F01 | UR-03-F08 | IT | `TrackingIT` | Retroactive create overlapping another record of same user returns HTTP 409 (no-overlap key invariant) |
| TE-03-F08.F01-03 | SR-03-F08.F01 | UR-03-F08 | IT | `TrackingIT` | Retroactive create on non-trackable node returns HTTP 409 (node need not be active but caller must hold `track`) |
| TE-03-F08.F01-04 | SR-03-F08.F01 | UR-03-F08 | CT | `TrackingCT` | `POST /api/tracking/records` conforms to `spec/openapi.yaml` |
| TE-03-F09.F01-01 | SR-03-F09.F01 | UR-03-F09 | IT | `TrackingIT` | PATCH updates only supplied fields on caller's record; rejects when result would overlap with any other record of the same user |
| TE-03-F09.F01-02 | SR-03-F09.F01 | UR-03-F09 | IT | `TrackingIT` | PATCH of the caller's currently-open record emits `TrackingChanged` |
| TE-03-F09.F01-03 | SR-03-F09.F01 | UR-03-F09 | IT | `TrackingIT` | PATCH by non-owner returns HTTP 403 |
| TE-03-F09.F01-04 | SR-03-F09.F01 | UR-03-F09 | CT | `TrackingCT` | `PATCH /api/tracking/records/{id}` conforms to `spec/openapi.yaml` |
| TE-03-F10.F01-01 | SR-03-F10.F01 | UR-03-F10 | IT | `TrackingIT` | DELETE removes caller's record; if deleted record was open, emits `TrackingChanged` with `null` |
| TE-03-F10.F01-02 | SR-03-F10.F01 | UR-03-F10 | IT | `TrackingIT` | DELETE by non-owner returns HTTP 403 |
| TE-03-F10.F01-03 | SR-03-F10.F01 | UR-03-F10 | CT | `TrackingCT` | `DELETE /api/tracking/records/{id}` conforms to `spec/openapi.yaml` |
| TE-03-F11.F01-01 | SR-03-F11.F01 | UR-03-F11 | IT | `TrackingIT` | Duplicate copies node and description from source; uses supplied timestamps; subject to all SR-03-F08.F01 checks (no-overlap, trackable, time bounds) |
| TE-03-F11.F01-02 | SR-03-F11.F01 | UR-03-F11 | CT | `TrackingCT` | `POST /api/tracking/records/{id}/duplicate` conforms to `spec/openapi.yaml` |
| TE-03-F12.F01-01 | SR-03-F12.F01 | UR-03-F12 | IT | `SseTaxonomyIT` | Each named snapshot SSE event (`TrackingChanged`, `NodeTreeChanged`, `AuthorizationChanged`, `AccountChanged`) is emitted with the payload shape of its corresponding REST query response |
| TE-03-F12.F02-01 | SR-03-F12.F02 | UR-03-F12 | IT | `SseTaxonomyIT` | Each command SSE event (`InvitationWithdrawn`, `AccountAnonymisedByAdmin`) is emitted to the affected user |
| TE-03-F12.F03-01 | SR-03-F12.F03 | UR-03-F12 | IT | `WebhookSubscriptionIT` | Webhook subscription CRUD endpoints insert / update / delete subscription rows; raw signing secret returned exactly once at creation and rotation |
| TE-03-F12.F03-02 | SR-03-F12.F03 | UR-03-F12 | CT | `AccountCT` | `/api/account/me/webhook-subscriptions` endpoints conform to `spec/openapi.yaml` |
| TE-03-F12.F04-01 | SR-03-F12.F04 | UR-03-F12 | IT | `WebhookDeliveryIT` | Every emitted event writes one `webhook_deliveries` outbox row per matching subscription in the same transaction as the business mutation |
| TE-03-F12.F04-02 | SR-03-F12.F04 | UR-03-F12 | IT | `WebhookDeliveryIT` | Background worker POSTs outbox rows with HMAC signature; retry-and-backoff on transient failure; permanent failure surfaced |

---

## Epic E-04 — Reports

| TE | SR | UR | Type | Class | Test description |
|---|---|---|---|---|---|
| TE-04-F01.F01-01 | SR-04-F01.F01 | UR-04-F01 | IT | `ReportsIT` | Aggregate report returns per-(node, bucket) rows for `mode=summary`; per-(node, bucket, normalised description) rows for `mode=detailed`; never exposes raw `time_records` |
| TE-04-F01.F01-02 | SR-04-F01.F01 | UR-04-F01 | IT | `ReportsIT` | Bucket alignment matches caller-supplied `tz` (`date_trunc('week', started_at AT TIME ZONE tz)`); `hour` bucket size accepted |
| TE-04-F01.F01-03 | SR-04-F01.F01 | UR-04-F01 | IT | `ReportsIT` | Invalid IANA TZ returns HTTP 400 before aggregation runs; visibility constraints limit nodes and users |
| TE-04-F01.F01-04 | SR-04-F01.F01 | UR-04-F01 | CT | `ReportsCT` | `GET /api/reports/aggregate` conforms to `spec/openapi.yaml` |
| TE-04-F01.C01-01 | SR-04-F01.C01 | UR-04-F01 | IT | `ReportsIT` | Backend accepts arbitrary UTC instants for `from` and `to`; no rounding outside `date_trunc` bucket grouping |
| TE-04-F02.F01-01 | SR-04-F02.F01 | UR-04-F02 | ACT | `reportView.spec.ts` | Toggling between summary and chart view uses the cached `mode=summary` dataset with no new HTTP call; toggling to detailed view triggers `mode=detailed` fetch |
| TE-04-F03.F01-01 | SR-04-F03.F01 | UR-04-F03 | ACT | `reportCsv.spec.ts` | CSV export is generated client-side; header row matches active UI dialect; dialect-aware field and decimal separators per the SR's table |
| TE-04-F03.F01-02 | SR-04-F03.F01 | UR-04-F03 | ACT | `reportCsv.spec.ts` | File starts with UTF-8 BOM; line endings are CRLF; fields containing separator/quote/newline are wrapped in double quotes with internal doubles doubled |
| TE-04-F04.F01-01 | SR-04-F04.F01 | UR-04-F04 | IT | `ReportsIT` | Member summaries return per-(target user, node, bucket) rows including target id, displayName, email; never expose individual `time_records` |
| TE-04-F04.F01-02 | SR-04-F04.F01 | UR-04-F04 | IT | `ReportsIT` | Caller's visibility filters apply: only target users contributing time to caller-visible nodes within the period are returned |
| TE-04-F04.F01-03 | SR-04-F04.F01 | UR-04-F04 | CT | `ReportsCT` | `GET /api/reports/member-summaries` conforms to `spec/openapi.yaml` |
| TE-04-F05.F01-01 | SR-04-F05.F01 | UR-04-F05 | ACT | `reportChart.spec.ts` | Single bar chart with time on X axis (per bucketSize) and durationSeconds on Y; rendered via PrimeNG/Chart.js from the summary-view dataset |
| TE-04-F06.F01-01 | SR-04-F06.F01 | UR-04-F06 | ACT | `reportPdf.spec.ts` | PDF export uses `jsPDF` + `jsPDF-AutoTable`; tables rendered as vector; chart embedded as bitmap snapshot; page size A4 portrait for all shipped dialects |
| TE-04-F07.F01-01 | SR-04-F07.F01 | UR-04-F07 | IT | `ReportFiltersIT` | `GET /api/account/me/report-filters` returns the persisted filter JSON or `null` when none saved |
| TE-04-F07.F01-02 | SR-04-F07.F01 | UR-04-F07 | CT | `AccountCT` | `GET /api/account/me/report-filters` conforms to `spec/openapi.yaml` |
| TE-04-F07.F02-01 | SR-04-F07.F02 | UR-04-F07 | IT | `ReportFiltersIT` | `PUT` upserts the JSON document; frontend debounces at 1s after the user stops changing filters |
| TE-04-F07.F02-02 | SR-04-F07.F02 | UR-04-F07 | CT | `AccountCT` | `PUT /api/account/me/report-filters` conforms to `spec/openapi.yaml` |
| TE-04-F07.C01-01 | SR-04-F07.C01 | UR-04-F07 | CT | `SchemaCT` | `user_profile.last_report_filters` is a `jsonb` column |

---

## Epic E-05 — Account

| TE | SR | UR | Type | Class | Test description |
|---|---|---|---|---|---|
| TE-05-F01.F01-01 | SR-05-F01.F01 | UR-05-F01 | IT | `AccountIT` | `GET /api/account/me` returns id, displayName, email, linkedProviders, status=active for the OIDC-authenticated caller; rejects API-key bearer with HTTP 401 |
| TE-05-F01.F01-02 | SR-05-F01.F01 | UR-05-F01 | CT | `AccountCT` | `GET /api/account/me` conforms to `spec/openapi.yaml` |
| TE-05-F02.F01-01 | SR-05-F02.F01 | UR-05-F02 | IT | `AccountIT` | Initiate link flow returns the configured OIDC authorization URL; unknown provider returns HTTP 400 |
| TE-05-F02.F02-01 | SR-05-F02.F02 | UR-05-F02 | IT | `AccountIT` | Link callback inserts `user_oauth_providers` row; refreshes `users.email` from claim; emits `oidc_provider_linked` |
| TE-05-F02.F02-02 | SR-05-F02.F02 | UR-05-F02 | IT | `AccountIT` | Callback for (provider, subject) already linked to another user returns HTTP 409 |
| TE-05-F02.F02-03 | SR-05-F02.F02 | UR-05-F02 | IT | `AccountIT` | Callback for (provider, subject) already linked to current user returns HTTP 409 |
| TE-05-F03.F01-01 | SR-05-F03.F01 | UR-05-F03 | IT | `AccountIT` | Unlink deletes `user_oauth_providers` row via `unlinkProvider` port method; emits `oidc_provider_unlinked` |
| TE-05-F03.F01-02 | SR-05-F03.F01 | UR-05-F03 | IT | `AccountIT` | Unlink that would leave 0 providers on non-anonymised user returns HTTP 409 (atomic-SQL guard) |
| TE-05-F03.F01-03 | SR-05-F03.F01 | UR-05-F03 | CT | `AccountCT` | `DELETE /api/account/oidc-providers/{provider}` conforms to `spec/openapi.yaml` |
| TE-05-F03.C01-01 | SR-05-F03.C01 | UR-05-F03 | IT | `PortShapeIT` | The `user_oauth_providers` port exposes only `unlinkProvider` (with guard subquery) and `scrubProviders` (no guard); no other write method exists |
| TE-05-F03.C01-02 | SR-05-F03.C01 | UR-05-F03 | CT | `SchemaCT` | `users.anonymised_at` exists as a nullable `TIMESTAMPTZ` |
| TE-05-F04.F01-01 | SR-05-F04.F01 | UR-05-F04 | IT | `AccountIT` | Own-authorizations returns one row per explicit `node_authorizations` entry for the caller, with node id, displayName, ancestor path, and level |
| TE-05-F04.F01-02 | SR-05-F04.F01 | UR-05-F04 | CT | `AccountCT` | `GET /api/account/me/authorizations` conforms to `spec/openapi.yaml` |
| TE-05-F04.F02-01 | SR-05-F04.F02 | UR-05-F04 | IT | `SseTaxonomyIT` | `AuthorizationChanged` snapshot SSE event payload equals `GET /api/account/me/authorizations` response shape |
| TE-05-F05.F01-01 | SR-05-F05.F01 | UR-05-F05 | IT | `AccountIT` | Anonymise endpoint with valid recent step-up event in session executes SR-07-F01.F01 cleanup; emits `account_anonymised(bySelf=true)`; step-up event cleared |
| TE-05-F05.F01-02 | SR-05-F05.F01 | UR-05-F05 | IT | `AccountIT` | Anonymise endpoint without a step-up event within 5 minutes returns HTTP 401 |
| TE-05-F05.F01-03 | SR-05-F05.F01 | UR-05-F05 | CT | `AccountCT` | `POST /api/account/me/anonymise` conforms to `spec/openapi.yaml` |
| TE-05-F05.F02-01 | SR-05-F05.F02 | UR-05-F05 | E2E | `anonymiseWizard.spec.ts` | Multi-step wizard: step 1 explanation in active dialect; step 2 OIDC step-up redirect; step 3 anonymise call + sign-out |
| TE-05-F06.F01-01 | SR-05-F06.F01 | UR-05-F06 | IT | `AboutPageIT` | About endpoint returns applicationVersion, thirdPartyLicenses, personalDataSummary, outboundConnections (OIDC + webhook only), and disclosure/advisory/openapi URLs; unauthenticated returns HTTP 401 |
| TE-05-F06.F01-02 | SR-05-F06.F01 | UR-05-F06 | CT | `AboutCT` | `GET /api/about` conforms to `spec/openapi.yaml` |
| TE-05-F06.F02-01 | SR-05-F06.F02 | UR-05-F06 | IT | `AboutPageIT` | `GET /api/about/openapi` returns the running OpenAPI as YAML to authenticated callers; HTTP 401 for anonymous |
| TE-05-F06.F02-02 | SR-05-F06.F02 | UR-05-F06 | CT | `AboutCT` | `GET /api/about/openapi` conforms to `spec/openapi.yaml` |

---

## Epic E-06 — Audit and security observability

| TE | SR | UR | Type | Class | Test description |
|---|---|---|---|---|---|
| TE-06-F01.F01-01 | SR-06-F01.F01 | UR-06-F01 | IT | `AuditEventVocabularyIT` | Every event type listed in SR-06-F01.F01 is emitted with the required structured fields (`eventType`, `actorId`, `targetId`, timestamp, correlation ids) when its triggering operation occurs |
| TE-06-F01.F02-01 | SR-06-F01.F02 | UR-06-F01 | IT | `LogRedactionIT` | Audit log entries never contain email, profile content, or request/response bodies; only pseudonymous identifiers |
| TE-06-F01.C01-01 | SR-06-F01.C01 | UR-06-F01 | CT | `SchemaCT` | No `audit_events` or `security_events` table exists in `spec/schema.sql` |
| TE-06-F02.F01-01 | SR-06-F02.F01 | UR-06-F02 | ACT | `aboutPage.spec.ts` | About page links to the project's GHSA index via a compile-time constant URL |
| TE-06-F03.F01-01 | SR-06-F03.F01 | UR-06-F03 | ACT | `advisorySubscribePage.spec.ts` | Admin UI guided page presents both subscription options (watch repo, Atom feed) with their respective URLs as compile-time constants |
| TE-06-F05.F01-01 | SR-06-F05.F01 | UR-06-F05 | IT | `AdminLookupIT` | Lookup by user UUID returns status, displayName, email (null for anonymised), OIDC subject identifiers, and authorization assignments |
| TE-06-F05.F01-02 | SR-06-F05.F01 | UR-06-F05 | CT | `AdminLookupCT` | `GET /api/admin/users/lookup` (UUID variant) conforms to `spec/openapi.yaml` |
| TE-06-F05.F02-01 | SR-06-F05.F02 | UR-06-F05 | IT | `AdminLookupIT` | Lookup by OIDC subject identifier resolves to the linked user; "not found" returned when no link exists |
| TE-06-F05.F03-01 | SR-06-F05.F03 | UR-06-F05 | IT | `AdminLookupIT` | Lookup by email exact match resolves to the non-anonymised user; falls back to `pending_invitations.email`; anonymised users are not found by email |
| TE-06-F05.F03-02 | SR-06-F05.F03 | UR-06-F05 | CT | `AdminLookupCT` | `GET /api/admin/users/lookup` (email variant) conforms to `spec/openapi.yaml` |

---

## Epic E-07 — Retention and backup

| TE | SR | UR | Type | Class | Test description |
|---|---|---|---|---|---|
| TE-07-F01.F01-01 | SR-07-F01.F01 | UR-07-F01 | IT | `AccessTerminationIT` | Cleanup for active user runs all 7 ordered steps in one transaction: close open record, delete authorizations, delete pending invitations, set anonymised_at + clear identifying fields, scrubProviders, revoke api_keys, invalidate Spring sessions |
| TE-07-F01.F01-02 | SR-07-F01.F01 | UR-07-F01 | IT | `AccessTerminationIT` | Step 5 (scrubProviders) requires step 4 (anonymised_at = NOW()) to have run; ordering preserved |
| TE-07-F01.F01-03 | SR-07-F01.F01 | UR-07-F01 | IT | `AccessTerminationIT` | Partial failure rolls the entire cleanup back |
| TE-07-F01.F02-01 | SR-07-F01.F02 | UR-07-F01 | IT | `AccessTerminationIT` | Cleanup for pending user deletes both `pending_invitations` row and the pre-created `users` row in one transaction; no anonymised stub is created |
| TE-07-F01.F03-01 | SR-07-F01.F03 | UR-07-F01 | IT | `AccessTerminationIT` | All four cleanup triggers (admin removal, invitation expiry, invitation withdrawal, self-anonymisation) invoke the same shared service method; trigger source recorded in audit event |
| TE-07-F01.C01-01 | SR-07-F01.C01 | UR-07-F01 | IT | `RetentionPurgeIT` | No scheduled or on-demand job deletes a user row where `anonymised_at IS NOT NULL` regardless of time-record set or age |
| TE-07-F01.C02-01 | SR-07-F01.C02 | UR-07-F01 | IT | `AccessTerminationIT` | Cleanup invoked on already-anonymised user completes without throwing and makes no state change |
| TE-07-F02.F01-01 | SR-07-F02.F01 | UR-07-F02 | IT | `BackupArtifactIT` | Backup tool emits `pg_dump -Fc` artifact + manifest with application version, schema version, dump version, and UTC start timestamp |
| TE-07-F02.F02-01 | SR-07-F02.F02 | UR-07-F02 | IT | `BackupArtifactIT` | Backup tool accepts output directory and PostgreSQL credentials from environment variables; runs non-interactively |
| TE-07-F02.F03-01 | SR-07-F02.F03 | UR-07-F02 | IT | `BackupArtifactIT` | Backup tool exits non-zero on `pg_dump` error, non-writable output, manifest write failure, or insufficient free space |
| TE-07-F02.C01-01 | SR-07-F02.C01 | UR-07-F02 | UT | `BackupToolTest` | No restore tool, restore endpoint, restore command, or in-application restore workflow exists |
| TE-07-F02.C02-01 | SR-07-F02.C02 | UR-07-F02 | IT | `BackupArtifactValidityIT` | Automated test exercises full backup → restore → schema-version-verify cycle |

---

## Epic E-08 — API keys

| TE | SR | UR | Type | Class | Test description |
|---|---|---|---|---|---|
| TE-08-F01.F01-01 | SR-08-F01.F01 | UR-08-F01 | IT | `ApiKeyIT` | Create with valid scope (subset of caller's effective authorization) inserts row with random raw key and SHA-256 hash; returns raw key exactly once; emits `api_key_generated` |
| TE-08-F01.F01-02 | SR-08-F01.F01 | UR-08-F01 | IT | `ApiKeyIT` | Name violates SR-08-F01.C01 → HTTP 409; expiresAt missing / past / > 1y future → HTTP 409; scope missing fields → HTTP 409 |
| TE-08-F01.F01-03 | SR-08-F01.F01 | UR-08-F01 | IT | `ApiKeyIT` | scope.level higher than caller's effective on scope.nodeId → HTTP 409; scope.nodeId not visible → HTTP 409 |
| TE-08-F01.F01-04 | SR-08-F01.F01 | UR-08-F01 | CT | `ApiKeyCT` | `POST /api/account/me/api-keys` conforms to `spec/openapi.yaml` |
| TE-08-F01.C01-01 | SR-08-F01.C01 | UR-08-F01 | UT | `ApiKeyNameValidatorTest` | Name length validation enforces ≤ 64 Unicode characters |
| TE-08-F01.C02-01 | SR-08-F01.C02 | UR-08-F01 | UT | `ApiKeyLifetimeValidatorTest` | Lifetime > 1 year from creation is rejected |
| TE-08-F02.F01-01 | SR-08-F02.F01 | UR-08-F02 | IT | `ApiKeyIT` | List returns caller's keys with id, name, scope (annotated with current displayName + ancestor path), createdAt, lastUsedAt, expiresAt, derived status; never raw key |
| TE-08-F02.F01-02 | SR-08-F02.F01 | UR-08-F02 | CT | `ApiKeyCT` | `GET /api/account/me/api-keys` conforms to `spec/openapi.yaml` |
| TE-08-F02.F02-01 | SR-08-F02.F02 | UR-08-F02 | IT | `ApiKeyIT` | Status derivation: revoked_at non-null → revoked; else expires_at < NOW() → expired; else active |
| TE-08-F03.F01-01 | SR-08-F03.F01 | UR-08-F03 | IT | `ApiKeyIT` | Revoke own key sets revoked_at; subsequent bearer presentation rejected with HTTP 401; emits `api_key_revoked(bySelf=true)`; revoking already-revoked is no-op success |
| TE-08-F03.F01-02 | SR-08-F03.F01 | UR-08-F03 | CT | `ApiKeyCT` | `POST /api/account/me/api-keys/{id}/revoke` conforms to `spec/openapi.yaml` |
| TE-08-F03.C01-01 | SR-08-F03.C01 | UR-08-F03 | IT | `AuthAdapterIT` | API-key authentication rejects revoked or expired keys before use-case handling; increments `trawhile_api_key_use_total{outcome=rejected_revoked|rejected_expired}` |
| TE-08-F03.C02-01 | SR-08-F03.C02 | UR-08-F03 | IT | `PortShapeIT` | The `api_keys` persistence port exposes only insert, markRevoked, markLastUsed, and read methods; no update method on name/scope/expires_at/key_hash/user_id/created_at exists |
| TE-08-F05.F01-01 | SR-08-F05.F01 | UR-08-F05 | IT | `AdminApiKeyIT` | Admin all-keys list returns SR-08-F02.F01 row shape plus owner UUID and displayName; supports pagination and name-substring filter |
| TE-08-F05.F01-02 | SR-08-F05.F01 | UR-08-F05 | CT | `AdminApiKeyCT` | `GET /api/admin/api-keys` conforms to `spec/openapi.yaml` |
| TE-08-F06.F01-01 | SR-08-F06.F01 | UR-08-F06 | IT | `AdminApiKeyIT` | Admin revoke of any key sets revoked_at; emits `api_key_revoked(bySelf=false, actorId=<admin uuid>)`; already-revoked is no-op |
| TE-08-F06.F01-02 | SR-08-F06.F01 | UR-08-F06 | CT | `AdminApiKeyCT` | `POST /api/admin/api-keys/{id}/revoke` conforms to `spec/openapi.yaml` |
