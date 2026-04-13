# System requirements (SR)

Format: "The system shall [behaviour/property]. [Rationale: reason]"

Each SR ID encodes its parent UR and own type: `SR-{parent-UR-type}{parent-UR-number}.{SR-own-type}{nn}`.
The sequence number is shared across all SR children of the same parent UR regardless of type.
SR-F and SR-Q must each have at least one TE. SR-C has none.

## Epic 1 — System administration

**SR-F001.F01 (type F):** The system shall detect on startup whether any `node_authorizations` row exists with `authorization = 'admin'` on the root node. If none exists and `BOOTSTRAP_ADMIN_EMAIL` is set, the system shall treat the first OIDC login matching that email as a bootstrap registration: store session data and redirect to the GDPR notice screen (SR-F060.F01), then as part of SR-F060.F02 insert a `users` row, `user_profile`, and `user_oauth_providers`, and additionally insert a `node_authorizations` row granting `admin` on the root node. No `pending_invitations` row is required for the bootstrap path. [Rationale: UR-F001]

**SR-F004.F01 (type F):** The system shall return all `users` rows with their status — `pending` if a `pending_invitations` row exists for the user, `active` if `user_profile` exists, `anonymised` otherwise — plus name (from `user_profile` if active; email from `pending_invitations` if pending; fixed placeholder if anonymised) to users with effective `admin` on root. [Rationale: UR-F004]

**SR-F005.F01 (type F):** The system shall return all `pending_invitations` rows, including email, invited-by user name, `invited_at`, and the associated `users.id`, to users with effective `admin` on root. [Rationale: UR-F005]

**SR-F006.F01 (type F):** The system shall, when a System Admin submits an email address not already present in `pending_invitations` and not linked to any existing `user_oauth_providers` row, insert a `users` row and a `pending_invitations` row referencing it, and return a `mailto:` link whose pre-filled body contains: the application base URL; the invitee's email address labelled as their invitation address; and a plain-language instruction to open the URL and sign in with the OIDC provider account (Google, Apple, Microsoft, or Keycloak) linked to that email address. The link body shall be generated server-side; the admin sends the email manually. [Rationale: UR-F006; UR-C003]

**SR-F007.F01 (type F):** The system shall transition a user to the Ready for Scrubbing state (SR-F070.F01) when a System Admin withdraws their pending invitation. [Rationale: UR-F007]

**SR-F008.F01 (type F):** The system shall transition a user to the Ready for Scrubbing state (SR-F070.F01) when a System Admin requests their removal, via a guided confirmation wizard (SR-F008.F02). [Rationale: UR-F008]

**SR-F008.F02 (type F):** The user removal flow shall be implemented as a multi-step confirmation wizard presented to the System Admin: (1) show the user's name, authorization assignments, and active tracking state; (2) explain consequences (irreversible without re-invitation, tracking stopped, all authorizations removed); (3) require explicit confirmation before executing SR-F008.F01. [Rationale: UR-F008]

**SR-F009.F01 (type F):** The system shall return all `node_authorizations` rows for a given user, each annotated with the full path from root to the granted node. [Rationale: UR-F009]

**SR-F009.F02 (type F):** The system shall allow a System Admin, from the user-management view, to grant or revoke a selected user's authorization on any node within the admin's scope, using a node picker and the same authorization rules and effects as SR-F021.F01 and SR-F022.F01. [Rationale: UR-F009]

**SR-F010.F01 (type F):** The system shall return the resolved system configuration — name, timezone, freeze-offset-years, effective freeze cutoff (computed as `NOW() - freeze_offset_years * INTERVAL '1 year'`), retention-years, node-retention-extra-years, privacy-notice-url — to any authenticated user. [Rationale: UR-F010; GDPR transparency — members have a right to know when their records become immutable and when they will be purged]

**SR-F010.F02 (type F):** The frontend shall set the browser tab title to `{trawhile.name} — trawhile` once system settings are loaded (SR-F010.F01). Before settings are available the title shall be `trawhile`. [Rationale: UR-F010; multi-tab usability]

**SR-F011.F01 (type F):** The system shall, when a System Admin resends a pending invitation, update `pending_invitations.expires_at` to `NOW() + INTERVAL '90 days'` for that row and return a fresh `mailto:` link with the same body format as SR-F006.F01. No new records are created; the existing `users` UUID and any assigned `node_authorizations` are preserved. [Rationale: UR-F011]

**SR-F062.F01 (type F):** The system shall display a prompt to invite members to the System Admin upon login whenever no other members exist; this includes immediately after the first bootstrap login (after GDPR notice acknowledgement). The invitation flow remains accessible from user management at any time. [Rationale: UR-F062]

**SR-F059.F01 (type F):** The system shall expose a Prometheus-compatible metrics scrape endpoint at `GET /actuator/prometheus` on a dedicated management port (`management.server.port`), separate from the main application port. The management port shall not be routed through Caddy; access is restricted by Docker network topology and requires no additional authentication. The endpoint shall include all standard Spring Boot Actuator metrics: JVM (heap, GC, threads), HTTP server request counts and latency histograms, and HikariCP connection pool metrics. [Rationale: UR-F059; management port isolates operational endpoints from the public API surface]

**SR-F059.F02 (type F):** The system shall expose the following custom Prometheus metrics via Micrometer:

| Metric | Type | Labels | Description |
|---|---|---|---|
| `trawhile_purge_job_last_completed_seconds` | Gauge | `job_type` | Unix timestamp of the last successful completion of a purge job run; enables alerting when stale |
| `trawhile_purge_job_deleted_total` | Counter | `job_type` | Cumulative count of rows deleted across all purge job runs |
| `trawhile_purge_job_failures_total` | Counter | `job_type` | Number of purge job runs that terminated with an unhandled exception |
| `trawhile_db_transaction_errors_total` | Counter | — | Number of database transactions that rolled back due to an error |
| `trawhile_rate_limit_rejections_total` | Counter | `endpoint` | HTTP 429 responses issued by bucket4j |
| `trawhile_security_events_total` | Counter | `event_type` | Security events recorded, per event type |
| `trawhile_oauth2_login_failures_total` | Counter | `provider` | Failed OAuth2 login callbacks, per provider |
| `trawhile_sse_connections_active` | Gauge | — | Number of currently active SSE emitter connections |
| `trawhile_tracking_sessions_active` | Gauge | — | Number of users with an open-ended (active) time record |
| `trawhile_mcp_tool_invocations_total` | Counter | `tool` | MCP tool invocations, per tool name |

[Rationale: UR-F059; no email notification channel — Prometheus and AlertManager are the operator's primary alerting mechanism; persistent database errors are detected by AlertManager via sustained rate on `trawhile_db_transaction_errors_total`]

**SR-F059.F03 (type F):** The system shall ship a `monitoring/` directory in the source repository containing the following artifacts, maintained in sync with the metric names defined in SR-F059.F02:

- **`monitoring/prometheus-scrape-config.yml`** — a ready-to-paste Prometheus scrape job block targeting the management port, with comments explaining the port configuration property.
- **`monitoring/alerting-rules.yml`** — an AlertManager-compatible rules file defining alerts for: purge job not completed within 26 hours (`trawhile_purge_job_last_completed_seconds` stale per `job_type`); sustained database transaction error rate (`rate(trawhile_db_transaction_errors_total[5m]) > 0 for 5m`); high HTTP 5xx rate; application instance down.
- **`monitoring/grafana-dashboard.json`** — an importable Grafana dashboard JSON containing panels for: JVM heap and GC activity; HikariCP connection pool utilisation; HTTP request rate and latency (p50, p95, p99); purge job last completed (per `job_type`); purge job failure count (per `job_type`); database transaction error rate; rate limit rejections; security event rate (per `event_type`); OAuth2 login failure rate (per `provider`); active SSE connections; active tracking sessions; MCP tool invocation rate (per `tool`).

These files are operator tooling only — they are not included in the application container image. [Rationale: UR-F059; monitoring artifacts that drift from metric names silently break dashboards and alerts — first-class maintenance is required]

**SR-F065.F01 (type F):** The system shall validate all supported application configuration defined in this specification at startup and fail fast with a descriptive error message identifying the invalid property or violated constraint if any of the following constraints are violated: any property constraint listed in SR-F050.F05; or no OIDC provider has a non-empty `client-id` configured (at least one of Google, Apple, Microsoft Entra ID, or Keycloak must be active, per UR-C002). [Rationale: UR-F065; UR-C002; operator usability — a misconfigured instance must not start silently]

**SR-F067.F01 (type F):** The system shall, on OAuth2 callback where a `user_oauth_providers` row exists for the provider/subject pair, create an authenticated session via Spring Security and return a session cookie. (The existence of `user_oauth_providers` guarantees `user_profile` exists via cascade.) [Rationale: UR-F067]

**SR-F067.F02 (type F):** The system shall expose `GET /auth/providers` without authentication, returning the list of OIDC provider registration IDs for which a non-empty `client-id` is configured at startup. The frontend shall call this endpoint on app init and render sign-in buttons only for the returned providers. [Rationale: UR-F067; providers are deployment-time config, not build-time]

## Epic 2 — Node administration

**SR-F014.F01 (type F):** The system shall return the details and direct children of a node to any user whose effective authorization on that node is at least `view`, resolved via recursive ancestor CTE. [Rationale: UR-F014]

**SR-F015.F01 (type F):** The system shall insert a `nodes` row as a child of the specified parent when a Node Admin of that parent or any ancestor submits a valid name. The new node shall have `is_active = true`, `sort_order` set to one greater than the current maximum sibling `sort_order`, and `deactivated_at = NULL`. [Rationale: UR-F015]

**SR-F016.F01 (type F):** The system shall update `nodes.name`, `nodes.description`, `nodes.color`, `nodes.icon`, and/or `nodes.logo` for a node when a Node Admin of that node or any ancestor submits new values. Logo uploads shall be rejected if the payload exceeds 256 KB or the MIME type is not one of `image/png`, `image/jpeg`, `image/svg+xml`, `image/webp`. [Rationale: UR-F016]

**SR-F017.F01 (type F):** The system shall update `sort_order` values on the direct children of a given node when a Node Admin of that node or any ancestor submits a complete new ordering. [Rationale: UR-F017]

**SR-F018.F01 (type F):** The system shall set `nodes.is_active = false` and `nodes.deactivated_at = NOW()` when a Node Admin requests deactivation, and shall reject the operation if any direct or indirect child node has `is_active = true`. An active `time_records` row on the node itself shall not block deactivation; the running record may complete normally. [Rationale: UR-F018]

**SR-F019.F01 (type F):** The system shall set `nodes.is_active = true` and `nodes.deactivated_at = NULL` for a deactivated node when a Node Admin of that node or any ancestor requests reactivation. [Rationale: UR-F019]

**SR-F020.F01 (type F):** The system shall update `nodes.parent_id` and set `nodes.sort_order` to append after existing siblings at the destination when a Node Admin submits a move operation, provided: (a) the requesting user has effective `admin` on the node being moved; (b) the requesting user has effective `admin` on the destination parent node; (c) the destination node is not the node itself or any of its descendants. [Rationale: UR-F020]

**SR-F021.F01 (type F):** The system shall insert or update a `node_authorizations` row granting the specified level (`view`, `track`, or `admin`) to the specified existing user on the specified node, when the requesting user has effective `admin` on that node via recursive ancestor CTE. Granting `admin` on the root node confers System Admin rights; no separate operation exists for this. [Rationale: UR-F021]

**SR-F021.F02 (type F):** The permissions UI (grant, revoke, and view authorization assignments) shall display clear explanatory text describing what each permission level means in plain language. The explanations shall be shown before and during grant/revoke operations, not only in help text. Required explanations:

| Permission level | Explanation required |
|---|---|
| `view` | Can see this node and all nodes beneath it, and view time summaries for the team |
| `track` | Can track time on this node and all trackable nodes beneath it, plus everything `view` allows |
| `admin` | Can manage nodes, assign and revoke permissions, and close requests within this node and all beneath it, plus everything `track` allows. This permission is inherited: granting admin here gives admin on all descendants |

The UI shall also clarify that permissions are inherited downward — a permission granted on a node is automatically effective on all nodes beneath it. The explanations shall be translated per SR-F061.F01–SR-F061.F02. [Rationale: UR-F021; UR-F022; users must understand consequences of granting admin before doing so]

**SR-F022.F01 (type F):** The system shall delete a `node_authorizations` row when a user with effective `admin` on the node or any ancestor requests revocation, and shall reject the operation if the row being deleted is the last `admin`-level authorization on that node. Revoking `admin` on the root node removes System Admin rights; no separate operation exists for this. [Rationale: UR-F022]

**SR-F023.F01 (type F):** The system shall return all effective authorization assignments on a node to any user with effective `admin` on that node or any ancestor, distinguishing rows where `node_authorizations.node_id` equals the queried node (direct) from those where it is an ancestor node (inherited). [Rationale: UR-F023]

## Epic 3 — Time tracking

**SR-F024.F01 (type F):** The system shall return the `time_records` row where `ended_at IS NULL` for the requesting user, including the full node path from root to the tracked node and elapsed time computed as `NOW() - started_at`. If no active row exists the system shall return an empty tracking state. [Rationale: UR-F024]

**SR-F025.F01 (type F):** The system shall return the most recent `time_records` rows for the requesting user in descending `started_at` order, annotated with: an overlap flag on any record whose time range overlaps with any other record for the same user; a gap flag on any pair of consecutive records where the gap between `ended_at` of one and `started_at` of the next exceeds zero. [Rationale: UR-F025]

**SR-F026.F01 (type F):** The system shall insert a `time_records` row with `ended_at = NULL` and the client-supplied IANA timezone string stored in `timezone` when a user starts tracking. The system shall reject the operation if any of the following hold: `nodes.is_active = false` for the target node; the target node has at least one child with `is_active = true`; the requesting user's effective authorization on the target node is below `track`. [Rationale: UR-F026]

**SR-F026.F02 (type F):** The frontend shall provide a reusable node picker widget used wherever a node selection is required (tracking start, retroactive record, report filter, request submission, quick-access management). The widget shall operate entirely on the client-side node tree already held in memory — no backend requests shall be made during interaction. The widget shall support: (1) hierarchical swipe/scroll navigation — swiping up/down moves through siblings; swiping right drills into a child; swiping left returns to the parent; (2) full-text search filtering the in-memory tree by node name, showing matching nodes with their ancestor path, updating instantly on each keystroke. Both interaction modes shall work on desktop (keyboard/mouse) and mobile (touch gestures). [Rationale: UR-F026; node selection is a high-frequency action and must be fast and mobile-friendly]

**SR-F027.F01 (type F):** The system shall return the `quick_access` rows for the requesting user, annotating each record whose referenced node has `is_active = false` or has at least one active child with a `non_trackable` flag; such records are not automatically removed. [Rationale: UR-F027; UR-F030]

**SR-F028.F01 (type F):** The system shall, when a user starts tracking while an active `time_records` row already exists for that user, execute within a single transaction: set `ended_at = NOW()` on the existing active row; insert a new `time_records` row subject to the same constraints as SR-F026.F01. [Rationale: UR-F028]

**SR-F029.F01 (type F):** The system shall set `ended_at = NOW()` on the active `time_records` row for the requesting user when they stop tracking, and shall return an empty tracking state thereafter. [Rationale: UR-F029]

**SR-F030.F01 (type F):** The system shall manage `quick_access` rows for the requesting user as follows: insert a row on add (reject if count would exceed 9); delete a row on remove; update `sort_order` values on reorder. [Rationale: UR-F030]

**SR-F031.F01 (type F):** The system shall insert a `time_records` row with the supplied `started_at`, `ended_at`, client-supplied IANA timezone string, and optional `description` when a user creates a retroactive record. The system shall reject the operation if: `nodes.is_active = false` for the target node; the target node has at least one active child; the requesting user's effective authorization is below `track`; or `started_at >= ended_at`. [Rationale: UR-F031]

**SR-F032.F01 (type F):** The system shall update `node_id`, `started_at`, `ended_at`, and/or `description` on a `time_records` row owned by the requesting user, leaving omitted fields unchanged. The system shall reject the operation if: the effective updated `started_at` falls before the effective freeze cutoff (`NOW() - freeze_offset_years * INTERVAL '1 year'`); the effective updated `ended_at` is non-null and `started_at >= ended_at`; or, when `node_id` is being changed, the new node has `nodes.is_active = false`, has at least one child with `is_active = true`, or the requesting user's effective authorization on the new node is below `track`. [Rationale: UR-F032]

**SR-F033.F01 (type F):** The system shall delete a `time_records` row owned by the requesting user and shall reject the operation if `started_at` falls before the effective freeze cutoff (`NOW() - freeze_offset_years * INTERVAL '1 year'`). [Rationale: UR-F033]

**SR-F034.F01 (type F):** The system shall insert a new `time_records` row copied from an existing row owned by the requesting user, substituting the user-supplied `started_at` and `ended_at` and copying the `description` from the original, subject to the same constraints as SR-F031.F01. [Rationale: UR-F034]

## Epic 4 — Reporting & export

**SR-F036.F01 (type F):** The system shall return `time_records` rows matching the supplied filters (date range, `user_id`, node), restricted to nodes for which the requesting user has at least `view` via recursive visible CTE. When a node filter is supplied, the result shall include records for all nodes in the subtree of the selected node that are also visible to the requesting user. The response shall contain either aggregated totals per node (summary mode) or individual records (detailed mode) as requested. All timestamps shall be converted to the company timezone for display. [Rationale: UR-F036]

**SR-F036.F02 (type F):** The system shall annotate pairs of `time_records` rows for the same user whose time ranges overlap with an overlap flag in detailed report mode, and annotate consecutive records with a gap flag where the gap between them is greater than zero. [Rationale: UR-F036]

**SR-F037.F01 (type F):** The frontend shall allow the user to switch a time report between summary view, detailed view, and chart view without changing the active report filters. [Rationale: UR-F037]

**SR-F038.F01 (type F):** The system shall serialise the current report result set to CSV and return it as a file download. [Rationale: UR-F038]

**SR-F052.F01 (type F):** The system shall return, for a caller-supplied date range (which must align to full days in the company timezone) and node, the total duration tracked per member per interval bucket on that node and all its descendants. Supported interval buckets: day, week, month, year, year-to-date, month-to-date. Each bucket row shall include a `has_data_quality_issues` flag that is true when any of the member's time records within that bucket overlap with another record, or when any gap exists between consecutive records within that bucket. The request may optionally filter results by `has_data_quality_issues = true` or `false`; when the filter is absent, both kinds of buckets shall be returned. The result is restricted to members for whom the requesting user has at least `view` on the relevant nodes via recursive CTE. Individual `time_records` rows shall not be exposed to any user other than the record owner; only the per-member per-bucket aggregate and quality flag are returned. [Rationale: UR-F052; GDPR data minimisation — aggregated totals over full-day intervals are the minimum granularity necessary for team coordination; quality flag enables controllers to identify unreliable data without exposing record detail]

**SR-F063.F01 (type F):** The frontend shall render chart view using PrimeNG's Chart.js integration (no additional charting dependency). The three chart types are: time per node (bar or pie, switchable via a toggle within chart view); time over period (bar or line, switchable); per-member breakdown (stacked bar). All chart data shall be derived client-side from the same dataset already fetched for the table view — no separate backend request shall be made when switching to chart view. [Rationale: UR-F063; PrimeNG Chart.js is already a project dependency]

**SR-F064.F01 (type F):** The frontend shall generate PDF exports client-side using jsPDF with html2canvas. When the user triggers PDF export, the currently visible view (summary table, detailed table, or chart) is rendered into a hidden fixed-width container (1280 px) before capture, so that the resulting PDF has a consistent desktop layout regardless of the device the user is on. No server-side PDF rendering is performed. [Rationale: UR-F064; client-side avoids server load and keeps report rendering stateless; fixed-width render ensures cross-device consistency]

**SR-F066.F01 (type F):** The system shall persist the authenticated user's last report filter state (date range, interval bucket, node filter, user filter) as JSONB in `user_profile.last_report_settings` whenever the user changes any report filter, and shall return the stored value as part of SR-F043.F01. The frontend shall restore the last saved settings from this field on loading the report view, ensuring consistent report state across devices and sessions. [Rationale: UR-F066]

## Epic 5 — Requests

**SR-F039.F01 (type F):** The system shall insert a `requests` row when a user submits a request against a node for which they have at least `view` authorization. The `template` field shall contain one of the following system-defined identifiers or `'free-text'`; `body` may be supplied for any template: `'grant-authorization'` (Grant authorization at [node]), `'create-child'` (Create child node under [node]), `'free-text'` (Other). [Rationale: UR-F039]

**SR-F041.F01 (type F):** The system shall return all `requests` rows (open and closed) for a given node to any user with at least `view` on that node via recursive ancestor CTE. [Rationale: UR-F041]

**SR-F042.F01 (type F):** The system shall set `requests.status = 'closed'`, `resolved_at = NOW()`, and `resolved_by = requesting_user_id` on a `requests` row when a user with effective `admin` on the node or any ancestor submits a close operation. [Rationale: UR-F042]

## Epic 6 — Account

**SR-F043.F01 (type F):** The system shall return the `user_profile` row for the authenticated user, including name, linked OIDC providers, and last saved report filter state. [Rationale: UR-F043; UR-F066]

**SR-F044.F01 (type F):** The system shall insert a `user_oauth_providers` row linking the authenticated user's `user_profile` to the new provider/subject pair, and shall reject the operation if that provider/subject pair already exists for any user. [Rationale: UR-F044]

**SR-F045.F01 (type F):** The system shall delete the specified `user_oauth_providers` row and shall reject the operation if doing so would leave the user with no linked providers. [Rationale: UR-F045]

**SR-F046.F01 (type F):** The system shall return all effective `node_authorizations` rows for the authenticated user, each annotated with the full path from root to the granted node. [Rationale: UR-F046]

**SR-F047.F01 (type F):** The system shall, when the authenticated user requests account anonymisation via a guided confirmation wizard (SR-F047.F03), execute the access-termination cleanup defined in SR-F070.F01. Anonymisation is irreversible. The user may re-register only via a new invitation; re-registration creates a new `users` row unlinked from any prior stub. [Rationale: UR-F047; GDPR right to erasure]

**SR-F047.F03 (type F):** The account anonymisation flow shall be implemented as a multi-step confirmation wizard: (1) explain consequences in the user's language (irreversible, data replaced, time records retained anonymously, re-registration requires new invitation); (2) require the user to type a confirmation phrase; (3) execute SR-F047.F01 only on confirmed submission. [Rationale: UR-F047]

**SR-F048.F01 (type F):** The system shall serve an About page containing: the deployed application version; a list of third-party component names and their licenses; a link to download the CycloneDX SBOM generated at build time; a link to download the OpenAPI specification for the REST API; and the built-in GDPR data summary (identical content to SR-F060.F02). The About page and all download links shall be accessible without authentication. If `privacy_notice_url` is configured in the system config, a link to the company Privacy Notice shall additionally be shown, but only to authenticated users who have at least one effective node authorization (i.e. at least `view` on any node via recursive CTE). [Rationale: UR-F048; CRA; GDPR transparency — built-in data summary is generic and public; the Privacy Notice URL is company-internal and restricted to users with actual access rights]

## Epic 7 — Security & audit

**SR-F049.F01 (type F):** The system shall insert a `security_events` row for each of the following: successful OIDC login; failed OIDC login; Node Admin grant; Node Admin revoke; account anonymisation; user removal; rate limit breach; authorization failure on a protected endpoint; activity purge execution (one row per job run, recording cutoff date and deleted counts); node deletion execution (same); MCP token generation; MCP token revocation; MCP token use (one row per request). [Rationale: UR-F049; CRA; GDPR accountability]

**SR-F049.F02 (type F):** The system shall return `security_events` rows, with filtering by `event_type`, `user_id`, and `occurred_at` range, exclusively to users with effective `admin` on root. [Rationale: UR-F049]

## Epic 8 — Data lifecycle

**SR-F050.F01 (type F):** The system shall, at the configured purge schedule in the company timezone, if the `purge_jobs` row for `job_type = 'activity'` has `status = 'idle'`, set `status = 'active'`, `cutoff_date = CURRENT_DATE - trawhile.retention-years * INTERVAL '1 year'`, and `started_at = NOW()`. [Rationale: UR-F050; UR-C004]

**SR-F050.F02 (type F):** The system shall, when the activity `purge_jobs` row has `status = 'active'`, delete `time_records` rows where `started_at < cutoff_date` and `requests` rows where `created_at < cutoff_date` in batches. After each batch the system shall update `deleted_counts` in the `purge_jobs` row and commit. When no rows remain to delete, the system shall set `status = 'idle'` and `completed_at = NOW()`. The job is idempotent: on application startup, if `status = 'active'`, the job resumes using the stored `cutoff_date`. [Rationale: UR-F050; idempotency]

**SR-F050.F03 (type F):** The system shall, after the activity purge job completes in a scheduled purge run, if the `purge_jobs` row for `job_type = 'node'` has `status = 'idle'`, set `status = 'active'`, `cutoff_date = CURRENT_DATE - (trawhile.retention-years + trawhile.node-retention-extra-years) * INTERVAL '1 year'`, and `started_at = NOW()`. [Rationale: UR-F050; UR-C004]

**SR-F050.F04 (type F):** The system shall, when the node `purge_jobs` row has `status = 'active'`, iteratively delete deactivated nodes in bottom-up order. In each iteration the system shall find deactivated nodes where: `deactivated_at < cutoff_date`; the node has no children; no `time_records` rows reference any node in the subtree; no `requests` rows reference any node in the subtree. Deletion cascades to `node_authorizations` via ON DELETE CASCADE. After each batch the system shall update `deleted_counts` and commit. When no qualifying nodes remain, the system shall set `status = 'idle'` and `completed_at = NOW()`. The job is idempotent on restart using stored `cutoff_date`. [Rationale: UR-F050; idempotency]

**SR-F050.F05 (type F):** System configuration shall be provided via an external `application.yml` file mounted into the container at runtime (Spring Boot external config at `/app/config/application.yml`). The repository shall ship a `config/application.yml.example` template documenting all supported properties with their defaults and a comment on each; `config/application.yml` shall be listed in `.gitignore`. The following properties shall be supported under the `trawhile:` namespace:

| Property | Type | Default | Constraint |
|---|---|---|---|
| `name` | string | `trawhile` | — |
| `timezone` | IANA timezone string | `UTC` | must be a valid IANA zone |
| `purge-cron` | Spring cron expression | `0 59 23 * * *` | must be a valid Spring cron expression; interpreted in `trawhile.timezone` |
| `freeze-offset-years` | integer | `2` | ≥ 0; ≤ retention-years |
| `retention-years` | integer | `5` | ≥ 2 |
| `node-retention-extra-years` | integer | `1` | ≥ 0 |
| `privacy-notice-url` | HTTPS URL or blank | _(none)_ | must be HTTPS if set |

The application shall validate all properties on startup and fail fast (with a descriptive error message) if any constraint is violated. Secrets (database credentials, OAuth client secrets, bootstrap admin email) are provided via environment variables and shall not appear in the config file. [Rationale: UR-F050; operator usability; 12-factor config discipline]

**SR-F060.F01 (type F):** The system shall, on OIDC callback where no `user_oauth_providers` row exists for the provider/subject pair but a `pending_invitations` row matches the provider-returned email, store `{pending_invitations.id, user_id, provider, subject, name}` in the HTTP session and redirect to the GDPR notice screen. The email shall not be persisted anywhere; it is used only to locate the `pending_invitations` row and then discarded. No database records shall be written at this point. [Rationale: UR-F060; UR-C006; GDPR data minimisation]

**SR-F060.F02 (type F):** The system shall, when an unauthenticated request arrives at `POST /auth/gdpr-notice` and the HTTP session contains pending registration data from SR-F060.F01, execute within a single transaction: insert a `user_profile` row (user_id and name from session); insert a `user_oauth_providers` row (provider and subject from session); delete the `pending_invitations` row by the stored ID. For the bootstrap path (SR-F001.F01), also insert the `users` row itself. Establish an authenticated session and respond with the `privacy_notice_url` from the system config only if that URL is configured and the user has at least one effective node authorization after registration completes; otherwise the response shall contain `null` for that field. If no pending registration data is present in the session, return HTTP 400. If the `pending_invitations` row identified in the session no longer exists (withdrawn by a System Admin between the OIDC callback and GDPR acknowledgement), the transaction shall be aborted and the user redirected to `/login?error=not_invited`. The built-in GDPR data summary displayed on the notice screen shall state: the name and OAuth provider identifier are stored; no email address or profile picture is stored; data is retained for the configured period and then purged automatically; the user may anonymise their account at any time. [Rationale: UR-F060; UR-F001 bootstrap path; GDPR transparency]

**SR-F060.F03 (type F):** The login page shall be accessible without authentication and shall display: sign-in buttons for each configured OIDC provider (Google, Apple, Microsoft Entra ID, Keycloak — only providers with a configured client-id are shown, via SR-F067.F02); a brief explanatory note stating that access is by invitation only and that users should sign in with the account linked to their invitation email; and a link to the About page (SR-F048.F01). The login page shall not request any company-specific data from the backend (name, settings, logo) and shall expose no information that identifies the company to an unauthenticated visitor. [Rationale: UR-F060; invitation-only model must be communicated before the user attempts sign-in]

**SR-F070.F01 (type F):** The system shall, when a user transitions to the Ready for Scrubbing state (invoked by SR-F007.F01, SR-C010.C01, SR-F008.F01, or SR-F047.F01), execute within a single transaction: set `ended_at = NOW()` on any active `time_records` row for that user; delete all `node_authorizations` rows for that user; delete the `pending_invitations` row for that user if one exists; delete the `user_profile` row for that user if one exists (cascades to `user_oauth_providers` and `quick_access`); set `revoked_at = NOW()` on any active `mcp_tokens` for that user; delete the `users` row if no `time_records` and no `requests` reference it, otherwise retain it as an anonymous stub until SR-F050.F02 orphan cleanup removes it. [Rationale: UR-F070; also invoked by UR-F007 (invitation withdrawal), UR-C010 (invitation expiry), UR-F008 (user removal)]

## Epic 9 — MCP integration

**SR-F053.F01 (type F):** The system shall, when an authenticated user generates an MCP token, insert an `mcp_tokens` row containing: `user_id`; a user-supplied label; `token_hash` (SHA-256 of the raw token); `created_at`; `expires_at` (nullable); `revoked_at` (NULL). The raw token shall be returned exactly once in the response and never stored. [Rationale: UR-F053]

**SR-F053.F02 (type F):** The system shall, on each MCP request bearing a Bearer token, compute the SHA-256 hash of the token, locate the matching `mcp_tokens` row, and reject the request with HTTP 401 if: no row matches; `revoked_at IS NOT NULL`; or `expires_at IS NOT NULL AND expires_at < NOW()`. On success, the system shall update `last_used_at = NOW()` and resolve the owning user for authorization checks. [Rationale: UR-F053; token security]

**SR-F054.F01 (type F):** The system shall return all `mcp_tokens` rows where `revoked_at IS NULL` for the authenticated user. [Rationale: UR-F054]

**SR-F055.F01 (type F):** The system shall set `revoked_at = NOW()` on an `mcp_tokens` row when the owning user requests revocation, and shall log the event to `security_events`. [Rationale: UR-F055]

**SR-F056.F01 (type F):** The system shall return all `mcp_tokens` rows where `revoked_at IS NULL`, joined with owning user name, to users with effective `admin` on root. [Rationale: UR-F056]

**SR-F057.F01 (type F):** The system shall set `revoked_at = NOW()` on any `mcp_tokens` row when a System Admin requests revocation, regardless of owner, and shall log the event to `security_events`. [Rationale: UR-F057]

**SR-F058.F01 (type F):** The system shall, immediately after a user generates an MCP token, display a guided onboarding wizard presenting: (1) the MCP server URL; (2) the raw token with a copy button and a warning that it will not be shown again; (3) step-by-step instructions for adding the MCP server in Claude.ai; (4) a connection-test button that invokes `get_node_tree()` and confirms success. [Rationale: UR-F058]

**SR-F069.F01 (type F):** The system shall expose an MCP server at `/mcp` accepting Bearer token authentication (SR-F053.F02) and providing the following tools, each enforcing node authorization via recursive CTE identical to the REST API:
- `get_time_records(node_id?, user_id?, date_from?, date_to?)` — returns time records visible to the token owner; if `user_id` is supplied and differs from the token owner, the token owner must have at least `view` on the relevant nodes and only aggregated daily totals are returned
- `get_node_tree()` — returns the node subtree visible to the token owner
- `get_tracking_status()` — returns the token owner's current tracking state
- `get_member_summaries(node_id?, date_from?, date_to?, interval?)` — returns per-member aggregated totals on nodes visible to the token owner for the requested interval bucket (day/week/month/year); SR-F052.F01 semantics apply

[Rationale: UR-F069]

## Epic 10 — Cross-cutting

**SR-F061.F01 (type F):** The frontend shall support four languages: English (en), German (de), French (fr), and Spanish (es). All UI text shall be externalised into per-language translation files maintained in the source repository and open to community contribution. ngx-translate shall be used as the i18n runtime so that translations are loaded at runtime without separate builds per language. [Rationale: UR-F061; GDPR Art. 12 — information must be in the language of the audience]

**SR-F061.F02 (type F):** The frontend shall determine the language to render by matching the browser's `Accept-Language` header against supported languages (en, de, fr, es), falling back to English if no match is found. This applies to all screens, including the GDPR notice (SR-F060.F02) and the login page (SR-F060.F03). [Rationale: UR-F061; GDPR Art. 12]

**SR-F061.F03 (type F):** The following screens shall be treated as highest-priority for translation completeness and accuracy, as they carry GDPR or legal significance: GDPR first-login notice (SR-F060.F02); account anonymisation confirmation wizard (SR-F047.F03); user removal confirmation wizard (SR-F008.F02); About page GDPR data summary (SR-F048.F01). [Rationale: UR-F061; GDPR Art. 12]

**SR-F068.F01 (type F):** The system shall maintain a persistent SSE connection per authenticated session and push events as follows: tracking state changes → all sessions of the same user; node tree changes (create, edit, reorder, deactivate, reactivate, move) → all sessions of users with at least `view` on the affected node; authorization changes on a node → all sessions of the affected user; request creation and status changes → all sessions of users with effective `admin` on the relevant node and all ancestors. [Rationale: UR-F068]

## Cross-cutting — constraints

**SR-C002.F01 (type F):** The system shall reject an OIDC login callback where the provider/subject pair is not in `user_oauth_providers` and no valid (non-expired) `pending_invitations` row matches the provider-returned email (and bootstrap conditions are not met), redirecting to `/login?error=not_invited` in all cases. The response shall not distinguish between "no invitation found" and "invitation expired". The frontend shall, when rendering the login page with `?error=not_invited`, display the message: "No pending invitation was found for your account. Please contact your company administrator." with a button to return to the sign-in options. [Rationale: UR-C002; invitation-only registration]

**SR-C006.C01 (type C):** The system shall not persist the email address returned by the OIDC provider beyond the duration of the login request transaction. [Rationale: UR-C006; GDPR data minimisation]

**SR-C007.F01 (type F):** The system shall delete all `security_events` rows where `occurred_at < NOW() - INTERVAL '90 days'` on a scheduled daily basis. [Rationale: UR-C007]

**SR-C010.C01 (type C):** The system shall, on a scheduled daily basis, transition all users to the Ready for Scrubbing state (SR-F070.F01) whose `pending_invitations.expires_at < NOW()`. [Rationale: UR-C010; GDPR storage limitation]

**SR-C011.C01 (type C):** The system shall enforce token-bucket rate limiting via bucket4j on all OAuth2 endpoints and all API endpoints. Requests exceeding the limit shall receive HTTP 429. Each breach shall be recorded as a `security_events` row. [Rationale: UR-C011]

**SR-C012.C01 (type C):** The system shall set the following headers on all HTTP responses: `Content-Security-Policy` (same-origin default, explicitly named external sources only); `Strict-Transport-Security: max-age=31536000; includeSubDomains`; `X-Frame-Options: DENY`; `X-Content-Type-Options: nosniff`; `Referrer-Policy: no-referrer`. [Rationale: UR-C012]

**SR-C013.C01 (type C):** The system shall enforce CSRF protection on all state-mutating endpoints (POST, PUT, PATCH, DELETE) via Spring Security's CSRF token mechanism. [Rationale: UR-C013]
