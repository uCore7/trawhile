# System requirements (SR)

Format: "The system shall [behaviour/property]. [Rationale: reason]"

62 SRs total. Epics 1–8 plus cross-cutting authentication/security.

## Epic 1 — Company administration

**SR-001:** The system shall detect on startup whether any `node_authorizations` row exists with
`authorization = 'admin'` on the root node. If none exists and `BOOTSTRAP_ADMIN_EMAIL` is set,
the system shall, as part of the SR-008 registration transaction for the first matching OAuth2
login, additionally insert a `node_authorizations` row granting `admin` on the root node to the
newly created `users` row. [Rationale: UR-001]

**SR-002:** The system shall not persist the email address returned by the OAuth2 provider beyond
the duration of the login request transaction. [Rationale: C-2; GDPR data minimisation]

**SR-003:** The system shall expose root Node Admin grant and revoke operations exclusively to
users with `is_system_admin = true`. [Rationale: UR-002, UR-003]

**SR-004:** The system shall reject a root Node Admin revoke operation if the target user is the
last `admin`-level `node_authorizations` row on the root node. [Rationale: UR-003]

**SR-005:** The system shall return all `users` rows LEFT JOIN `user_profile`, including name
(fixed placeholder string if `user_profile` is absent), picture URL, and active status, to users
with `is_system_admin = true` or effective `admin` on root. [Rationale: UR-004]

**SR-006:** The system shall return all `pending_memberships` rows, including email, invited-by
user name, and `invited_at`, to users with effective `admin` on root. [Rationale: UR-005]

**SR-007:** The system shall insert a `pending_memberships` row when a Node Admin of root submits
an email address not already present in `pending_memberships` and not linked to any existing
`user_oauth_providers` row. [Rationale: UR-006]

**SR-008:** The system shall, on OAuth2 callback where no `user_oauth_providers` row exists for
the provider/subject pair but a `pending_memberships` row matches the provider-returned email,
execute within a single transaction: insert a `users` row; insert a `user_profile` row with name
and picture URL from the provider; insert a `user_oauth_providers` row referencing the new
`user_profile`; delete the `pending_memberships` row; discard the email. If SR-001 bootstrap
conditions are met, additionally insert a `node_authorizations` row granting `admin` on root.
[Rationale: UR-001 bootstrap path; C-2; GDPR data minimisation]

**SR-009:** The system shall delete a `pending_memberships` row when a Node Admin of root requests
cancellation. [Rationale: UR-007]

**SR-010:** The system shall, when a Node Admin of root removes a user, execute within a single
transaction: set `ended_at = NOW()` on any active `time_entries` row for that user; delete all
`node_authorizations` rows for that user; delete the `user_profile` row for that user (cascades to
`user_oauth_providers`, `quick_access`, `node_colors`); set `users.is_active = false`. The `users`
row and all `time_entries` rows are retained. [Rationale: UR-008]

**SR-011:** The system shall return all `node_authorizations` rows for a given user, each
annotated with the full path from root to the granted node. [Rationale: UR-009]

**SR-012:** The system shall return the single `company_settings` row — name, timezone,
freeze_date, `retention_years`, `node_retention_extra_years`, `purge_schedule` — to users with
effective `admin` on root. [Rationale: UR-010]

**SR-013:** The system shall update the `company_settings` row with supplied values and reject any
request where: `retention_years < 2`; `node_retention_extra_years < 0`; or `purge_schedule ∉
{'annual', 'semi_annual', 'quarterly'}`. [Rationale: UR-011]

**SR-014:** The system shall serialise all `nodes`, `time_entries`, `requests`, and
`node_authorizations` rows to CSV on request by a Node Admin of root. For users with a
`user_profile` row the name shall be included; for anonymised users (no `user_profile`) a fixed
placeholder string shall appear in its place. No original personal data of anonymised users shall
be reconstructed. [Rationale: UR-012]

**SR-015:** The system shall reject a CSV import request if any `nodes` rows other than the root
node, any `time_entries` rows, or any `users` rows beyond the bootstrap admin already exist.
[Rationale: UR-013]

## Epic 2 — Node administration

**SR-016:** The system shall return the details and direct children of a node to any user whose
effective authorization on that node is at least `view`, resolved via recursive ancestor CTE.
[Rationale: UR-014]

**SR-017:** The system shall insert a `nodes` row as a child of the specified parent when a Node
Admin of that parent or any ancestor submits a valid name. The new node shall have `is_active =
true`, `sort_order` set to one greater than the current maximum sibling `sort_order`, and
`deactivated_at = NULL`. [Rationale: UR-015]

**SR-018:** The system shall update `nodes.name` and/or `nodes.description` for a node when a
Node Admin of that node or any ancestor submits new values. [Rationale: UR-016]

**SR-019:** The system shall update `sort_order` values on the direct children of a given node
when a Node Admin of that node or any ancestor submits a complete new ordering.
[Rationale: UR-017]

**SR-020:** The system shall set `nodes.is_active = false` and `nodes.deactivated_at = NOW()` when
a Node Admin requests deactivation, and shall reject the operation if any direct or indirect child
node has `is_active = true`. An active `time_entries` row on the node itself shall not block
deactivation; the running entry may complete normally. [Rationale: UR-018]

**SR-021:** The system shall set `nodes.is_active = true` and `nodes.deactivated_at = NULL` for a
deactivated node when a Node Admin of that node or any ancestor requests reactivation.
[Rationale: UR-019]

**SR-022:** The system shall update `nodes.parent_id` and set `nodes.sort_order` to append after
existing siblings at the destination when a Node Admin submits a move operation, provided: (a) the
requesting user has effective `admin` on the node being moved; (b) the requesting user has
effective `admin` on the destination parent node; (c) the destination node is not the node itself
or any of its descendants. [Rationale: UR-020]

**SR-023:** The system shall insert or update a `node_authorizations` row granting the specified
level (`view`, `track`, or `admin`) to the specified existing user on the specified node, when the
requesting user has effective `admin` on that node via recursive ancestor CTE.
[Rationale: UR-021]

**SR-024:** The system shall delete a `node_authorizations` row when a user with effective `admin`
on the node or any ancestor requests revocation, and shall reject the operation if the row being
deleted is the last `admin`-level authorization on that node. [Rationale: UR-022]

**SR-025:** The system shall return all effective authorization assignments on a node to any user
with effective `admin` on that node or any ancestor, distinguishing rows where
`node_authorizations.node_id` equals the queried node (direct) from those where it is an ancestor
node (inherited). [Rationale: UR-023]

## Epic 3 — Time tracking

**SR-026:** The system shall return the `time_entries` row where `ended_at IS NULL` for the
requesting user, including the full node path from root to the tracked node and elapsed time
computed as `NOW() - started_at`. If no active row exists the system shall return an empty
tracking state. [Rationale: UR-024]

**SR-027:** The system shall return the most recent `time_entries` rows for the requesting user in
descending `started_at` order, annotated with an overlap flag on any entry whose time range
overlaps with any other entry for the same user. [Rationale: UR-025]

**SR-028:** The system shall insert a `time_entries` row with `ended_at = NULL` and the
client-supplied IANA timezone string stored in `timezone` when a user starts tracking. The system
shall reject the operation if any of the following hold: `nodes.is_active = false` for the target
node; the target node has at least one child with `is_active = true`; the requesting user's
effective authorization on the target node is below `track`. [Rationale: UR-026]

**SR-029:** The system shall, when a user starts tracking while an active `time_entries` row
already exists for that user, execute within a single transaction: set `ended_at = NOW()` on the
existing active row; insert a new `time_entries` row subject to the same constraints as SR-028.
[Rationale: UR-028]

**SR-030:** The system shall set `ended_at = NOW()` on the active `time_entries` row for the
requesting user when they stop tracking, and shall return an empty tracking state thereafter.
[Rationale: UR-029]

**SR-031:** The system shall manage `quick_access` rows for the requesting user as follows: insert
a row on add (reject if count would exceed 9); delete a row on remove; update `sort_order` values
on reorder. When returning the quick-access list, each entry whose referenced node has `is_active =
false` or has at least one active child shall be annotated with a `non_trackable` flag; such
entries are not automatically removed. [Rationale: UR-027, UR-030]

**SR-032:** The system shall insert a `time_entries` row with the supplied `started_at`, `ended_at`,
and client-supplied IANA timezone string when a user creates a retroactive entry. The system shall
reject the operation if: `nodes.is_active = false` for the target node; the target node has at
least one active child; the requesting user's effective authorization is below `track`; or
`started_at >= ended_at`. [Rationale: UR-031]

**SR-033:** The system shall update `node_id`, `started_at`, and/or `ended_at` on a `time_entries`
row owned by the requesting user. The system shall reject the operation if: `started_at` falls
before `company_settings.freeze_date`; `started_at >= ended_at`; or, when `node_id` is being
changed, the new node fails any constraint from SR-028. [Rationale: UR-032]

**SR-034:** The system shall delete a `time_entries` row owned by the requesting user and shall
reject the operation if `started_at` falls before `company_settings.freeze_date`.
[Rationale: UR-033]

**SR-035:** The system shall insert a new `time_entries` row copied from an existing row owned by
the requesting user, substituting the user-supplied `started_at` and `ended_at`, subject to the
same constraints as SR-032. [Rationale: UR-034]

**SR-036:** The system shall insert or update a `node_colors` row for the requesting user's
`user_profile` and the given node when a color value is submitted. [Rationale: UR-035]

## Epic 4 — Reporting & export

**SR-037:** The system shall return `time_entries` rows matching the supplied filters (date range,
`user_id`, node), restricted to nodes for which the requesting user has at least `view` via
recursive visible CTE. When a node filter is supplied, the result shall include entries for all
nodes in the subtree of the selected node that are also visible to the requesting user. The
response shall contain either aggregated totals per node (summary mode) or individual rows
(detailed mode) as requested. All timestamps shall be converted to the company timezone for
display. [Rationale: UR-036, UR-037]

**SR-038:** The system shall annotate pairs of `time_entries` rows for the same user whose time
ranges overlap with an overlap flag in detailed report mode. [Rationale: F4.3]

**SR-039:** The system shall serialise the current report result set to CSV and return it as a
file download. [Rationale: UR-038]

## Epic 5 — Requests

**SR-040:** The system shall insert a `requests` row when a user submits a request against a node
for which they have at least `view` authorization. The `template` field shall contain a
system-defined template identifier or a free-text marker; `body` may be supplied for any template.
[Rationale: UR-039]

**SR-041:** The system shall return `requests` rows for a given node as follows: all rows (open
and closed) to any user with effective `admin` on that node or any ancestor; only rows where
`requester_id = requesting_user_id` to all other users with at least `view`.
[Rationale: UR-040, UR-041]

**SR-042:** The system shall set `requests.status = 'closed'`, `resolved_at = NOW()`, and
`resolved_by = requesting_user_id` on a `requests` row when a user with effective `admin` on the
node or any ancestor submits a close operation. [Rationale: UR-042]

## Epic 6 — Account

**SR-043:** The system shall return the `user_profile` row for the authenticated user, including
name and picture URL. [Rationale: UR-043]

**SR-044:** The system shall insert a `user_oauth_providers` row linking the authenticated user's
`user_profile` to the new provider/subject pair, and shall reject the operation if that
provider/subject pair already exists for any user. [Rationale: UR-044]

**SR-045:** The system shall delete the specified `user_oauth_providers` row and shall reject the
operation if doing so would leave the user with no linked providers. [Rationale: UR-045]

**SR-046:** The system shall return all `node_authorizations` rows for the authenticated user,
each annotated with the full path from root to the granted node. [Rationale: UR-046]

**SR-047:** The system shall, when the authenticated user requests account anonymisation, execute
within a single transaction: set `ended_at = NOW()` on any active `time_entries` row for that
user; delete all `node_authorizations` rows for that user; delete the `user_profile` row for that
user (cascades to `user_oauth_providers`, `quick_access`, `node_colors`). The `users` row and all
`time_entries` rows shall be retained with `time_entries.user_id` continuing to reference the
`users` row. Anonymisation is irreversible. The user may re-register only via a new invitation;
re-registration creates a new `users` row unlinked from the anonymised stub.
[Rationale: UR-047; GDPR right to erasure]

**SR-048:** The system shall serve an About page containing: the deployed application version; a
list of third-party component names and their licenses; a link to download the CycloneDX SBOM
generated at build time. [Rationale: UR-048; CRA]

## Epic 7 — Security & audit

**SR-049:** The system shall insert a `security_events` row for each of the following: successful
OAuth2 login; failed OAuth2 login; Node Admin grant; Node Admin revoke; account anonymisation;
user removal; rate limit breach; authorization failure on a protected endpoint; activity purge
execution (one row per job run, recording cutoff date and deleted counts); node deletion execution
(same). [Rationale: UR-049; CRA; GDPR accountability]

**SR-050:** The system shall return `security_events` rows, with filtering by `event_type`,
`user_id`, and `occurred_at` range, exclusively to users with `is_system_admin = true`.
[Rationale: UR-049]

**SR-051:** The system shall delete all `security_events` rows where `occurred_at < NOW() -
INTERVAL '90 days'` on a scheduled daily basis. [Rationale: C-3]

## Epic 8 — Data lifecycle

**SR-052:** The system shall, on each day, evaluate whether an activity purge is due by checking:
the current date in the company timezone matches a scheduled activity purge date derived from
`purge_schedule`; and the `purge_jobs` row for `job_type = 'activity'` has either `status =
'idle'` or `completed_at` falling in a prior schedule period. If due and not yet started, the
system shall set `status = 'active'`, `cutoff_date = CURRENT_DATE - retention_years * INTERVAL '1
year'`, and `started_at = NOW()`. [Rationale: F8.3]

**SR-053:** The system shall, when the activity `purge_jobs` row has `status = 'active'`, delete
`time_entries` rows where `started_at < cutoff_date` and `requests` rows where `created_at <
cutoff_date` in batches. After each batch the system shall update `deleted_counts` in the
`purge_jobs` row and commit. When no rows remain to delete, the system shall set `status = 'idle'`
and `completed_at = NOW()`. The job is idempotent: on application startup, if `status = 'active'`,
the job resumes using the stored `cutoff_date`. [Rationale: F8.3; idempotency]

**SR-054:** The system shall, on each day, evaluate whether a node deletion job is due by
checking: the current date in the company timezone matches a scheduled node deletion date derived
from `purge_schedule`; the `purge_jobs` row for `job_type = 'node'` has `status = 'idle'` or
`completed_at` in a prior schedule period; and the activity `purge_jobs` row for the corresponding
period has `completed_at` set. If due, the system shall set `status = 'active'`, `cutoff_date =
CURRENT_DATE - (retention_years + node_retention_extra_years) * INTERVAL '1 year'`, and
`started_at = NOW()`. [Rationale: F8.4]

**SR-055:** The system shall, when the node `purge_jobs` row has `status = 'active'`, iteratively
delete deactivated nodes in bottom-up order. In each iteration the system shall find deactivated
nodes where: `deactivated_at < cutoff_date`; the node has no children; no `time_entries` rows
reference any node in the subtree; no `requests` rows reference any node in the subtree. Deletion
cascades to `node_authorizations` via ON DELETE CASCADE. After each batch the system shall update
`deleted_counts` and commit. When no qualifying nodes remain, the system shall set `status =
'idle'` and `completed_at = NOW()`. The job is idempotent on restart using stored `cutoff_date`.
[Rationale: F8.4; idempotency]

**SR-056:** The system shall, starting 6 weeks before each scheduled activity purge date and
continuing until the purge completes, compute on demand: the count of `time_entries` rows with
`started_at` before the upcoming cutoff; the count of `requests` rows with `created_at` before
the upcoming cutoff; the count of deactivated nodes with `deactivated_at` before the upcoming node
cutoff and no remaining activity in their subtree. The system shall push this notification via SSE
to all active sessions of Node Admins of root and System Admins. Records and nodes within these
counts shall be rendered with a purge/deletion flag throughout the UI; this flag is computed
dynamically and not stored. [Rationale: UR-051; F8.2]

## Cross-cutting — authentication and security

**SR-057:** The system shall, on OAuth2 callback where a `user_oauth_providers` row exists for the
provider/subject pair, the linked `users` row has `is_active = true`, and a `user_profile` row
exists, create an authenticated session via Spring Security and return a session cookie.
[Rationale: returning-user login path]

**SR-058:** The system shall reject an OAuth2 login callback where the provider/subject pair is
not in `user_oauth_providers` and no `pending_memberships` row matches the provider-returned email,
returning HTTP 403 without creating any row. [Rationale: C-2; invitation-only registration]

**SR-059:** The system shall enforce token-bucket rate limiting via bucket4j on all OAuth2
endpoints and all API endpoints. Requests exceeding the limit shall receive HTTP 429. Each breach
shall be recorded as a `security_events` row. [Rationale: CRA; NFR]

**SR-060:** The system shall set the following headers on all HTTP responses:
`Content-Security-Policy` (same-origin default, explicitly named external sources only);
`Strict-Transport-Security: max-age=31536000; includeSubDomains`; `X-Frame-Options: DENY`;
`X-Content-Type-Options: nosniff`; `Referrer-Policy: no-referrer`. [Rationale: CRA; OWASP Top 10]

**SR-061:** The system shall enforce CSRF protection on all state-mutating endpoints (POST, PUT,
PATCH, DELETE) via Spring Security's CSRF token mechanism. [Rationale: OWASP Top 10]

**SR-062:** The system shall maintain a persistent SSE connection per authenticated session and
push events as follows: tracking state changes → all sessions of the same user; node tree changes
(create, edit, reorder, deactivate, reactivate, move) → all sessions of users with at least `view`
on the affected node; authorization changes on a node → all sessions of the affected user; request
creation and status changes → all sessions of users with effective `admin` on the relevant node
and all ancestors; purge notification changes → all sessions of Node Admins of root and System
Admins. [Rationale: live sync is a general UX principle]
