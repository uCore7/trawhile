# Epics and features

## Context

Self-hosted, open source, one instance = one company. Authorization is explicit — a user needs at
least `view` on a node (via recursive ancestor check) to see it. All views and dialogs always
render; node pickers populate only with what the user can see. Live synchronisation via SSE applies
to all state across all sessions — it is a general UX principle, not specific to any one epic.

## Role model

- **User** — any person in the `users` table (matched a pending invitation, completed OAuth2 login)
- **System Admin** — `is_system_admin = true`; set at bootstrap; manages root Node Admin assignments
  and security log access
- **Node Admin** — user with `admin` authorization on a node, effective recursively over all
  descendants
- Authorization levels: `view` ⊂ `track` ⊂ `admin`

## Epic 1 — Company administration

**Bootstrap**
- F1.1 — On first startup, if `BOOTSTRAP_ADMIN_EMAIL` is set and no root Node Admin exists, grant
  that user `admin` on root on first OAuth2 login

**Root Node Admin management (System Admin)**
- F1.2 — Grant `admin` authorization on root to an existing user
- F1.3 — Revoke `admin` authorization on root from a user (blocked if last `admin` on root)

**User management (Node Admin of root)**
- F1.4 — View all users
- F1.5 — View all pending invitations
- F1.6 — Invite a user by email (creates pending membership)
- F1.7 — Cancel a pending invitation
- F1.8 — Remove a user (stops active tracking session, removes all node authorization assignments)
- F1.9 — View authorization assignments of a user across the tree

**Company settings (Node Admin of root)**
- F1.10 — View company settings (name, timezone, freeze date, retention years, purge schedule)
- F1.11 — Update company settings

**Data (Node Admin of root)**
- F1.12 — Export all company data to CSV
- F1.13 — Import company data from CSV (blocked if any nodes beyond root, time entries, or users
  beyond the bootstrap admin exist)

## Epic 2 — Node administration (Node Admin of the node or any ancestor)

- F2.1 — View node details and children
- F2.2 — Create a child node
- F2.3 — Edit node (name, description)
- F2.4 — Reorder child nodes (`sort_order` in schema)
- F2.5 — Deactivate a node (blocked if active children exist; active time entry on the node itself
  does not block deactivation)
- F2.6 — Reactivate a node
- F2.7 — Move a node to a different parent (requires `admin` on both node and destination parent;
  destination must not be within own subtree)
- F2.8 — Grant authorization to a user on a node (`view` / `track` / `admin`; user must exist)
- F2.9 — Revoke authorization from a user on a node (blocked if last `admin` on that node)
- F2.10 — View authorization assignments on a node (direct and inherited from ancestors,
  visually distinguished)

## Epic 3 — Time tracking (users with at least `track` on a leaf node)

**Current status**
- F3.1 — View current tracking status (node path, elapsed time, start time)
- F3.2 — View recent time entry history (overlapping entries flagged)

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
- F3.10 — Create a time entry retroactively (node, start time, end time)
- F3.11 — Edit a time entry (node, start time, end time); blocked if entry is in frozen period
- F3.12 — Delete a time entry; blocked if entry is in frozen period
- F3.13 — Duplicate a time entry (prompts for new start and end time)

**Personal preference**
- F3.14 — Set a personal color for a node (inline in tree navigation)

## Epic 4 — Reporting & export (all users, filtered to visible nodes)

- F4.1 — View time report with composable filters: date range, user, node (full subtree of
  selected node, limited to visible nodes)
- F4.2 — Toggle between summary view (totals per node) and detailed view (individual entries)
- F4.3 — Flag overlapping entries in detailed view
- F4.4 — Export current report view to CSV

## Epic 5 — Requests (users with at least `view` on a node)

- F5.1 — Submit a request on any visible node (system template or free text)
- F5.2 — View request history on a node (own requests visible to all; all requests visible to
  Node Admins of the node or any ancestor)
- F5.3 — Close a request (Node Admin of the node or any ancestor)

**System request templates**
- Grant authorization at [node]
- Create child node under [node]
- Other (free text)

## Epic 6 — Account (all users)

- F6.1 — View profile (name, picture — stored from OAuth2 provider at login)
- F6.2 — Link an additional OAuth2 provider (GitHub or Google)
- F6.3 — Unlink an OAuth2 provider (blocked if only one linked)
- F6.4 — View own authorization assignments across the tree
- F6.5 — Anonymise own account (irreversible: clears name, picture, OAuth2 links; time entries
  attributed to anonymous placeholder; re-registration requires new invitation and creates new
  identity)
- F6.6 — View About page (application version, third-party licenses, downloadable SBOM)

## Epic 7 — Security & audit (System Admin only)

- F7.1 — Log all security events (authentication, authorization failures, privilege changes, rate
  limit breaches, purge executions) with 90-day retention
- F7.2 — View security event log
- F7.3 — Filter security event log by event type, user, date range

## Epic 8 — Data lifecycle (Node Admin of root / System Admin)

- F8.1 — Set `retention_years` (≥ 2, default 7), `node_retention_extra_years` (≥ 0, default 1),
  and `purge_schedule` ('annual' | 'semi_annual' | 'quarterly') in company settings
- F8.2 — View unified pre-notification (active 6 weeks before each scheduled activity purge):
  next activity purge date + affected record counts, next node deletion date + affected node
  counts; flagged records and nodes visible inline throughout the UI (computed dynamically)
- F8.3 — On each scheduled activity purge date at 23:59 (company timezone): purge `time_entries`
  (on `started_at`) and `requests` (on `created_at`) beyond `retention_years` cutoff; logged to
  `security_events`
- F8.4 — One month after each activity purge: delete deactivated nodes with no remaining
  `time_entries` or `requests` in subtree and `deactivated_at` before
  `retention_years + node_retention_extra_years` cutoff, bottom-up; logged to `security_events`

**Schedule-derived purge dates**

| Schedule | Activity purge | Node deletion |
|---|---|---|
| annual | Dec 31 | Jan 31 |
| semi_annual | Jun 30, Dec 31 | Jul 31, Jan 31 |
| quarterly | Mar 31, Jun 30, Sep 30, Dec 31 | Apr 30, Jul 31, Oct 31, Jan 31 |

**Purge mechanics**
- Activity purge: chunked DELETEs in batches; `deleted_counts` updated after each batch commit
- Node deletion: iterative bottom-up loop; each iteration deletes currently-eligible leaf nodes,
  exposing new leaves for subsequent iterations
- Both jobs tracked in `purge_jobs` table (status: idle/active); `cutoff_date` stored at job
  start; idempotent on application restart
- `security_events` purged independently at 90 days on a scheduled daily basis

## Key invariants

- At most one active tracking session per user at all times (enforced by partial unique index)
- Tracking only permitted on active leaf nodes (no active children) with at least `track`
  authorization
- A deactivated node may have a running time entry from before deactivation; the entry may
  complete normally; no new entries may start on it
- Time entries before `freeze_date` are immutable; no admin override
- Overlapping time entries are flagged but allowed
- Cannot deactivate a node with active children
- Cannot revoke the last `admin` authorization on any node
- Cannot move a node into its own subtree
- All timestamps stored in UTC; time entries carry the IANA timezone captured from the browser
  at tracking start
- A user must exist before receiving any node authorization
- Authorization is derived recursively upward: a grant on an ancestor is effective on all
  descendants
- Only users with `admin` on a node may grant or revoke authorizations on that node or any
  descendant
- `retention_years >= 2`
- `node_retention_extra_years >= 0`
- `purge_schedule ∈ {'annual', 'semi_annual', 'quarterly'}`
- Node deletion only fires after the corresponding activity purge has completed
- Node deletion never fires if the subtree has remaining `time_entries` or `requests`
- `purge_jobs` has exactly two rows seeded by Flyway: 'activity' and 'node'
- On startup, any `purge_jobs` row with status = 'active' is resumed using stored `cutoff_date`
- Anonymisation is irreversible; re-registration requires a new invitation and creates a new
  `users` row unlinked from the anonymised stub
- Non-trackable nodes remain in a user's quick-access list, annotated with a non-trackable flag
