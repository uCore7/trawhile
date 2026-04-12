# Epics and features

## Context

Self-hosted, open source, one instance = one company. Authorization is explicit — a user needs at least `view` on a node to see it. All views and dialogs always render; node pickers populate only with what the user can see. Live synchronisation applies to all state across all sessions — it is a general UX principle, not specific to any one epic.

## Role model

Roles are additive and form a hierarchy: System Admin IS-A Node Admin IS-A User. A System Admin retains all Node Admin and User capabilities.

- **User** — any registered member (matched a pending invitation, completed first login)
- **Node Admin** — a User holding `admin` authorization on a node, effective recursively over all descendants
- **System Admin** — a User holding `admin` authorization on the root node; a specialisation of Node Admin whose scope covers the full tree; first designated via a bootstrap admin email configured at deployment
- Permission levels: `view` ⊂ `track` ⊂ `admin`

## Epic 1 — System administration

**Bootstrap**
- F1.0 — On first login (any user), display GDPR notice screen before any other view: built-in summary of stored data, retention period, and right to anonymise; optional link to company Privacy Notice if configured (shown only to users with at least one node authorization); user must acknowledge to proceed; rendered in the user's preferred language
- F1.1 — On first startup, if a bootstrap admin is configured and no System Admin exists, the designated person is granted System Admin rights on first login
- F1.1a — After the bootstrap admin's first login (after GDPR notice), display a first-run prompt to invite initial members; the prompt may be dismissed and the invitation flow accessed again from user management at any time

**User management (System Admin)**
- F1.4 — View all users
- F1.5 — View all pending invitations (shows email, invited-by, invited-at)
- F1.6 — Invite a user by email: registers the invitee; generates a mailto: link for the admin to send manually; Node Admins may assign authorizations to the pending user immediately
- F1.6a — Resend invitation: resets the invitation expiry; generates a fresh mailto: link; does not affect the user record or assigned authorizations
- F1.7 — Withdraw a pending invitation; also expires automatically after 90 days (both trigger account cleanup)
- F1.8 — Remove a user via guided confirmation wizard (triggers account cleanup)
- F1.9 — View authorization assignments of a user across the tree; from this view, admins can also grant or revoke authorizations on any node within their scope (same operation as F2.8/F2.9, different entry point — node picker instead of user picker)

**System settings (all users, read-only)**
- F1.10 — View current system configuration: instance name, timezone, freeze offset, retention period, node retention extra period, privacy notice URL; read-only; operator-configured

**Observability (System Admin)**
- F1.11 — Monitor system health via a Prometheus-compatible metrics endpoint covering infrastructure (JVM, HTTP traffic, database connection pool) and application-level events (purge job outcomes, security event rates, OAuth2 login failures, rate limit rejections, active SSE connections, active tracking sessions, MCP tool usage)
- F1.12 — Ship ready-to-use monitoring artifacts in a `monitoring/` directory: an importable Grafana dashboard JSON, an AlertManager rules file covering critical operational alerts, and a Prometheus scrape configuration snippet; all maintained in sync with the application's metric definitions

## Epic 2 — Node administration (Node Admin of the node or any ancestor)

- F2.1 — View node details and children
- F2.2 — Create a child node
- F2.3 — Edit node (name, description, color, icon, logo)
- F2.4 — Reorder child nodes
- F2.5 — Deactivate a node (blocked if active children exist; active time entry on the node itself does not block deactivation)
- F2.6 — Reactivate a node
- F2.7 — Move a node to a different parent (requires `admin` on both node and destination parent; destination must not be within own subtree)
- F2.8 — Grant authorization to a user on a node (`view` / `track` / `admin`; user must exist); also accessible from the user view (F1.9) via node picker
- F2.9 — Revoke authorization from a user on a node (blocked if last `admin` on that node); also accessible from the user view (F1.9)
- F2.10 — View authorization assignments on a node (direct and inherited from ancestors, visually distinguished)

## Epic 3 — Time tracking (users with at least `track` on a leaf node)

**Current status**
- F3.1 — View current tracking status (node path, elapsed time, start time)
- F3.2 — View recent time entry history (overlapping entries flagged, gaps between consecutive entries flagged)

**Tracking**
- F3.3 — Start tracking by navigating the node tree (active leaf nodes with `track` only)
- F3.4 — Start tracking via quick-access list (up to 9 entries)
- F3.5 — Switch to a different node (atomic: stops current, starts new)
- F3.6 — Stop tracking

**Quick-access management**
- F3.7 — Add a node to quick-access list
- F3.8 — Remove a node from quick-access list
- F3.9 — Reorder quick-access list
- Note: non-trackable nodes remain in list, annotated with a non-trackable flag; not auto-removed

**Manual entry management**
- F3.10 — Create a time entry retroactively (node, start time, end time, optional description)
- F3.11 — Edit a time entry (node, start time, end time, description); blocked if entry is in frozen period
- F3.12 — Delete a time entry; blocked if entry is in frozen period
- F3.13 — Duplicate a time entry (prompts for new start and end time; description copied)

**Personal preference**
- F3.14 — (removed — per-user node colors replaced by company-wide node color/icon/logo)

## Epic 4 — Reporting & export (all users, filtered to visible nodes)

- F4.1 — View time report with composable filters: date range, user, node (full subtree of selected node, limited to visible nodes)
- F4.2 — Toggle between summary view (totals per node), detailed view (individual entries), and chart view; charts use the same active filters as the table views
- F4.3 — Flag overlapping entries in detailed view
- F4.4 — Export current report view to CSV
- F4.5 — View aggregated time per member on visible nodes over any full-day interval (daily, weekly, monthly, yearly, year-to-date, month-to-date); each bucket shows a data quality flag when overlaps or gaps exist in that member's entries for that period; filterable by flag; individual entry detail is never exposed to users other than the entry owner
- F4.6 — Chart: time per node — bar or pie, showing total duration per node for the current filter
- F4.7 — Chart: time over period — bar or line, showing total duration per time bucket (bucketed by the same interval as F4.5: day, week, month, etc.)
- F4.8 — Chart: per-member breakdown — stacked bar, showing each member's share of total time per time bucket, limited to members visible to the requesting user
- F4.9 — Export current view to PDF (exports whatever view is active — table or chart; chart is rendered as a raster image in the PDF)

## Epic 5 — Requests (users with at least `view` on a node)

- F5.1 — Submit a request on any visible node (system template or free text)
- F5.2 — View all requests on any visible node (requires at least `view` on the node)
- F5.3 — Close a request (Node Admin of the node or any ancestor)

**System request templates**
- Grant authorization at [node]
- Create child node under [node]
- Other (free text)

## Epic 6 — Account (all users)

- F6.1 — View profile (name — stored from OAuth2 provider at login)
- F6.2 — Link an additional configured OIDC provider (Google, Apple, Microsoft Entra ID, or Keycloak)
- F6.3 — Unlink an OAuth2 provider (blocked if only one linked)
- F6.4 — View own authorization assignments across the tree
- F6.5 — Anonymise own account via guided confirmation wizard (irreversible; deletes personal data while preserving time entry history; re-registration requires a new invitation and creates a new identity with no link to the anonymised account)
- F6.6 — View About page (application version, third-party licenses, downloadable SBOM, downloadable OpenAPI specification, GDPR data summary, optional Privacy Notice link); accessible without authentication

## Epic 7 — Security & audit (System Admin only)

- F7.1 — Log all security events (authentication, authorization failures, privilege changes, rate limit breaches, purge executions) with 90-day retention
- F7.2 — View security event log
- F7.3 — Filter security event log by event type, user, date range

## Epic 8 — Data lifecycle (automated; operator-configured)

- F8.1 — Configure the data retention period (minimum 2 years) and the additional grace period before deactivated nodes are deleted
- F8.3 — Nightly: purge time entries and requests older than the retention cutoff; then delete orphaned anonymous user stubs whose last time entries were just purged; logged to the security event log
- F8.4 — After each nightly activity purge completes: delete deactivated nodes with no remaining time entries or requests in their subtree that pre-date the node retention cutoff, bottom-up; logged to the security event log

**Purge mechanics**
- Both purge jobs are idempotent: if interrupted by an application restart, they resume from where they left off
- Security event log entries are purged independently after 90 days

## Epic 9 — MCP integration (Account / System Admin)

MCP (Model Context Protocol) allows AI assistants such as Claude.ai to query trawhile data on behalf of a user, respecting the same node authorization model as the UI.

**Token management (Account page)**
- F9.1 — Generate a named MCP access token; raw value shown once, then never again
- F9.2 — View own active tokens (label, creation date, last-used date)
- F9.3 — Revoke own token

**Token management (System Admin)**
- F9.4 — View all active tokens across all users (user, label, creation date, last-used date)
- F9.5 — Revoke any token

**MCP server**
- F9.6 — Expose MCP tools callable by a token-authenticated client:
  - `get_time_entries(node_id?, user_id?, date_from?, date_to?)` — returns entries visible to the token owner
  - `get_node_tree()` — returns the subtree visible to the token owner
  - `get_tracking_status()` — returns the token owner's current tracking state
  - `get_member_summaries(node_id?, date_from?, date_to?, interval?)` — returns per-member aggregated totals on visible nodes for the requested interval (day/week/month/year)
- F9.7 — Guided onboarding: after generating a token, display a step-by-step Claude.ai setup wizard (MCP server URL, token entry, connection test) so the user can complete setup without external documentation

**Security model**
- Token is non-recoverable after initial display
- MCP tools enforce the same authorization model as the REST API
- Token activity is logged to the security event log

## Key invariants

- At most one active tracking session per user at all times
- Tracking only permitted on active leaf nodes (no active children) with at least `track` authorization
- A deactivated node may have a running time entry from before deactivation; the entry may complete normally; no new entries may start on it
- Time entries before the effective freeze cutoff are immutable; no admin override
- Overlapping time entries are flagged but allowed
- Cannot deactivate a node with active children
- Cannot revoke the last `admin` authorization on any node
- Cannot move a node into its own subtree
- All timestamps stored in UTC; time entries carry the IANA timezone captured from the browser at tracking start
- A user must exist before receiving any node authorization
- Authorization is derived recursively upward: a grant on an ancestor is effective on all descendants
- Only users with `admin` on a node may grant or revoke authorizations on that node or any descendant
- Data retention period is at least 2 years
- Node retention extra period is non-negative
- Freeze offset does not exceed the retention period
- Node deletion only fires after the corresponding activity purge has completed
- Node deletion never fires if the subtree has remaining time entries or requests
- Both purge jobs are idempotent on restart: an interrupted job resumes from its stored checkpoint
- Anonymisation is irreversible; re-registration requires a new invitation and creates a new identity with no link to the anonymised account
- Non-trackable nodes remain in a user's quick-access list, annotated with a non-trackable flag
