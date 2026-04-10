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
from the provider (no picture URL stored); insert a `user_oauth_providers` row referencing the new
`user_profile`; delete the `pending_memberships` row; discard the email. If SR-001 bootstrap
conditions are met, additionally insert a `node_authorizations` row granting `admin` on root.
[Rationale: UR-001 bootstrap path; C-2; GDPR data minimisation]

**SR-009:** The system shall delete a `pending_memberships` row when a Node Admin of root requests
cancellation. [Rationale: UR-007]

**SR-009a:** The system shall delete all `pending_memberships` rows where `expires_at < NOW()` on
a scheduled daily basis. [Rationale: GDPR storage limitation — invitation email addresses must not
be retained indefinitely]

**SR-010:** The system shall, when a Node Admin of root removes a user, execute within a single
transaction: set `ended_at = NOW()` on any active `time_entries` row for that user; delete all
`node_authorizations` rows for that user; delete the `user_profile` row for that user (cascades to
`user_oauth_providers`, `quick_access`, `node_colors`); set `users.is_active = false`. The `users`
row and all `time_entries` rows are retained. [Rationale: UR-008]

**SR-011:** The system shall return all `node_authorizations` rows for a given user, each
annotated with the full path from root to the granted node. [Rationale: UR-009]

**SR-012:** The system shall return the single `company_settings` row — name, timezone,
freeze_date, `retention_years`, `node_retention_extra_years`, `purge_schedule`,
`privacy_notice_url` — to users with effective `admin` on root. [Rationale: UR-010]

**SR-013:** The system shall update the `company_settings` row with supplied values and reject any
request where: `retention_years < 2`; `node_retention_extra_years < 0`; or `purge_schedule ∉
{'annual', 'semi_annual', 'quarterly'}`. `privacy_notice_url`, if supplied, must be a valid HTTPS
URL; it may be set to null to remove it. [Rationale: UR-011]

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

**SR-018:** The system shall update `nodes.name`, `nodes.description`, `nodes.color`,
`nodes.icon`, and/or `nodes.logo` for a node when a Node Admin of that node or any ancestor
submits new values. Logo uploads shall be rejected if the payload exceeds 256 KB or the MIME type
is not one of `image/png`, `image/jpeg`, `image/svg+xml`, `image/webp`. [Rationale: UR-016]

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
descending `started_at` order, annotated with: an overlap flag on any entry whose time range
overlaps with any other entry for the same user; a gap flag on any pair of consecutive entries
where the gap between `ended_at` of one and `started_at` of the next exceeds zero. [Rationale: UR-025]

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
client-supplied IANA timezone string, and optional `description` when a user creates a retroactive
entry. The system shall reject the operation if: `nodes.is_active = false` for the target node;
the target node has at least one active child; the requesting user's effective authorization is
below `track`; or `started_at >= ended_at`. [Rationale: UR-031]

**SR-033:** The system shall update `node_id`, `started_at`, `ended_at`, and/or `description` on a
`time_entries` row owned by the requesting user. The system shall reject the operation if:
`started_at` falls before `company_settings.freeze_date`; `started_at >= ended_at`; or, when
`node_id` is being changed, the new node fails any constraint from SR-028. [Rationale: UR-032]

**SR-034:** The system shall delete a `time_entries` row owned by the requesting user and shall
reject the operation if `started_at` falls before `company_settings.freeze_date`.
[Rationale: UR-033]

**SR-035:** The system shall insert a new `time_entries` row copied from an existing row owned by
the requesting user, substituting the user-supplied `started_at` and `ended_at` and copying the
`description` from the original, subject to the same constraints as SR-032. [Rationale: UR-034]

**SR-036:** (removed — per-user node colors replaced by company-wide node color/icon/logo managed
via SR-018) [Rationale: UR-035 superseded]

## Epic 4 — Reporting & export

**SR-037:** The system shall return `time_entries` rows matching the supplied filters (date range,
`user_id`, node), restricted to nodes for which the requesting user has at least `view` via
recursive visible CTE. When a node filter is supplied, the result shall include entries for all
nodes in the subtree of the selected node that are also visible to the requesting user. The
response shall contain either aggregated totals per node (summary mode) or individual rows
(detailed mode) as requested. All timestamps shall be converted to the company timezone for
display. [Rationale: UR-036, UR-037]

**SR-038:** The system shall annotate pairs of `time_entries` rows for the same user whose time
ranges overlap with an overlap flag in detailed report mode, and annotate consecutive entries with
a gap flag where the gap between them is greater than zero. [Rationale: F4.3]

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
name and language preference. [Rationale: UR-043]

**SR-043a:** The system shall update `user_profile.language` when the authenticated user submits a
language preference, and shall reject values outside {'en', 'de', 'fr', 'es'}. [Rationale: UR-061]

**SR-043b:** The system shall persist the authenticated user's last report filter state
(date range, interval bucket, node filter, user filter) as JSONB in
`user_profile.last_report_settings` whenever the user changes any report filter. On loading the
report view, the frontend shall restore the last saved settings from this field. This ensures
consistent report state across devices and sessions. [Rationale: multi-device UX; UR-036]

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
user (cascades to `user_oauth_providers`, `quick_access`). The `users` row and all
`time_entries` rows shall be retained with `time_entries.user_id` continuing to reference the
`users` row. Anonymisation is irreversible. The user may re-register only via a new invitation;
re-registration creates a new `users` row unlinked from the anonymised stub.
[Rationale: UR-047; GDPR right to erasure]

**SR-048:** The system shall serve an About page containing: the deployed application version; a
list of third-party component names and their licenses; a link to download the CycloneDX SBOM
generated at build time; a link to download the OpenAPI specification for the REST API; and the
built-in GDPR data summary (identical content to SR-057a). The About page and all download links
shall be accessible without authentication. If `privacy_notice_url` is configured in `company_settings`, a link to the company Privacy Notice
shall additionally be shown, but only to authenticated users who have at least one effective node
authorization (i.e. at least `view` on any node via recursive CTE). [Rationale: UR-048; CRA;
GDPR transparency — built-in data summary is generic and public; the Privacy Notice URL is
company-internal and restricted to users with actual access rights]

## Epic 7 — Security & audit

**SR-049:** The system shall insert a `security_events` row for each of the following: successful
OAuth2 login; failed OAuth2 login; Node Admin grant; Node Admin revoke; account anonymisation;
user removal; rate limit breach; authorization failure on a protected endpoint; activity purge
execution (one row per job run, recording cutoff date and deleted counts); node deletion execution
(same); MCP token generation; MCP token revocation; MCP token use (one row per request).
[Rationale: UR-049; CRA; GDPR accountability]

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

**SR-072:** The system shall, when the pre-notification period is active (SR-056 conditions hold),
include the upcoming activity cutoff date in the response to SR-027 (time entry history) and
annotate each returned `time_entries` row with a `will_be_purged` flag when `started_at` falls
before that cutoff. The cutoff date shall also be pushed via SSE to all authenticated sessions
(not only Node Admins and System Admins) so that the UI can display it without polling.
[Rationale: UR-059; GDPR transparency — members have the right to know when their data will be
deleted]

## Cross-cutting — authentication and security

**SR-057:** The system shall, on OAuth2 callback where a `user_oauth_providers` row exists for the
provider/subject pair, the linked `users` row has `is_active = true`, and a `user_profile` row
exists, create an authenticated session via Spring Security and return a session cookie. If
`user_profile.gdpr_notice_accepted = false`, the session shall be flagged so that the frontend
redirects to the GDPR notice screen before any other view. [Rationale: returning-user login path]

**SR-057a:** The system shall, when a user submits acknowledgement of the GDPR notice, set
`user_profile.gdpr_notice_accepted = true`. The response to the acknowledgement request shall
include the current `privacy_notice_url` from `company_settings` (null if not configured) so the
frontend can display it alongside the built-in data summary. The built-in summary shall state:
the name, profile picture, and OAuth provider identifier are stored; no email address is stored;
data is retained for the configured period and then purged automatically; the user may anonymise
their account at any time. [Rationale: GDPR transparency; UR-060]

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
and all ancestors; purge notification changes (counts, dates) → all sessions of Node Admins of root and System
Admins; activity cutoff date visibility changes (pre-notification start/end) → all authenticated
sessions. [Rationale: live sync is a general UX principle; SR-072]

## Cross-cutting — permissions explanatory text

**SR-083:** The permissions UI (grant, revoke, and view authorization assignments) shall display
clear explanatory text describing what each permission level means in plain language. The
explanations shall be shown before and during grant/revoke operations, not only in help text.
Required explanations:

| Permission level | Explanation required |
|---|---|
| `view` | Can see this work item and all work items beneath it, and view time summaries for the team |
| `track` | Can track time on this work item and all trackable work items beneath it, plus everything `view` allows |
| `admin` | Can manage work items, assign and revoke permissions, and close requests within this work item and all beneath it, plus everything `track` allows. This permission is inherited: granting admin here gives admin on all descendants |

The UI shall also clarify that permissions are inherited downward — a permission granted on a
work item is automatically effective on all work items beneath it. The explanations shall be
translated per SR-073–074. [Rationale: UR-021, UR-022; users must understand consequences of
granting admin before doing so]

## Cross-cutting — company settings explanatory text

**SR-082:** The company settings UI shall display clear explanatory text alongside each
configuration parameter, describing in plain language what the parameter controls and what
changing it will actually cause. Minimum required explanations:

| Parameter | Explanation required |
|---|---|
| `name` | Displayed in the browser tab title and throughout the UI |
| `timezone` | Reference timezone for date boundaries in reports, freeze date evaluation, and purge scheduling |
| `freeze_date` | Time entries starting before this date cannot be edited or deleted by anyone, including admins |
| `retention_years` | Time entries and requests older than this will be permanently deleted on the next scheduled purge; minimum 2 years |
| `node_retention_extra_years` | Deactivated work items are kept for this many additional years beyond the activity retention period before being deleted |
| `purge_schedule` | How often automatic deletion runs: annually (Dec 31), twice a year (Jun 30 + Dec 31), or quarterly (Mar 31, Jun 30, Sep 30, Dec 31) |
| `privacy_notice_url` | URL of the company's Privacy Notice shown to authenticated members with access rights; leave empty if none |

The explanations shall be translated per SR-073–074. [Rationale: UR-011; sysadmins must
understand consequences before changing retention or purge settings]

## Cross-cutting — browser tab title

**SR-081:** The frontend shall set the browser tab title to `{company_settings.name} — trawhile`
once company settings are loaded. Before settings are available the title shall be `trawhile`.
[Rationale: sysadmin configurability; multi-tab usability]

## Cross-cutting — work item picker widget

**SR-080:** The frontend shall provide a reusable work item picker widget used wherever a work
item selection is required (tracking start, retroactive entry, report filter, request submission,
quick-access management). The widget shall operate entirely on the client-side node tree already
held in memory — no backend requests shall be made during interaction. The widget shall support:
(1) hierarchical swipe/scroll navigation — swiping up/down moves through siblings; swiping
right drills into a child; swiping left returns to the parent; (2) full-text search filtering
the in-memory tree by node name, showing matching nodes with their ancestor path, updating
instantly on each keystroke. Both interaction modes shall work on desktop (keyboard/mouse) and
mobile (touch gestures). [Rationale: UR-026; general UX principle — work item selection is a
high-frequency action and must be fast and mobile-friendly]

## Cross-cutting — internationalisation

**SR-073:** The frontend shall support four languages: English (en), German (de), French (fr), and
Spanish (es). All UI text shall be externalised into per-language translation files maintained in
the source repository and open to community contribution. ngx-translate shall be used as the
i18n runtime so that translations are loaded at runtime without separate builds per language.
[Rationale: UR-061; GDPR Art. 12 — information must be in the language of the audience]

**SR-074:** The frontend shall determine the language to render as follows: (1) `user_profile.language`
if the user is authenticated and has set a preference; (2) the best match from the `Accept-Language`
request header against supported languages; (3) English as the final fallback. This chain applies
to all screens, including the GDPR notice (SR-057a) which may appear before a preference is set.
[Rationale: UR-061; SR-057a fallback requirement]

**SR-075:** The following screens shall be treated as highest-priority for translation completeness
and accuracy, as they carry GDPR or legal significance: GDPR first-login notice (SR-057a);
account anonymisation confirmation wizard; user removal confirmation wizard; CSV import
confirmation wizard; About page GDPR data summary (SR-048). [Rationale: GDPR Art. 12; UR-061]

## Cross-cutting — guided wizards

**SR-076:** The account anonymisation flow shall be implemented as a multi-step confirmation
wizard: (1) explain consequences in the user's language (irreversible, data replaced, time entries
retained anonymously, re-registration requires new invitation); (2) require the user to type a
confirmation phrase; (3) execute SR-047 only on confirmed submission. [Rationale: UR-047]

**SR-077:** The user removal flow shall be implemented as a multi-step confirmation wizard
presented to the Node Admin of root: (1) show the user's name, authorization assignments, and
active tracking state; (2) explain consequences (irreversible without re-invitation, tracking
stopped, all authorizations removed); (3) require explicit confirmation before executing SR-010.
[Rationale: UR-008]

**SR-078:** The CSV import flow shall be implemented as a guided confirmation wizard: (1) show the
current instance state (node count, user count, time entry count) and the preconditions required
for import; (2) require explicit confirmation; (3) execute SR-015 only on confirmed submission.
[Rationale: UR-013]

**SR-079:** The bootstrap setup wizard shall be displayed to the System Admin immediately after
their first login (after GDPR notice acknowledgement) if no company name has been set. Steps:
(1) set company name and timezone; (2) optionally set Privacy Notice URL; (3) invite first
members (generates mailto: links). The wizard may be skipped and accessed again from company
settings. [Rationale: UR-062]

## Epic 4 addition — member daily summaries

**SR-063:** The system shall return, for a caller-supplied date range (which must align to full
days in the company timezone) and node, the total duration tracked per member per interval
bucket on that node and all its descendants. Supported interval buckets: day, week, month, year,
year-to-date, month-to-date. Each bucket row shall include a `has_data_quality_issues` flag that
is true when any of the member's time entries within that bucket overlap with another entry, or
when any gap exists between consecutive entries within that bucket. The result is restricted to
members for whom the requesting user has at least `view` on the relevant nodes via recursive CTE.
Individual `time_entries` rows shall not be exposed to any user other than the entry owner; only
the per-member per-bucket aggregate and quality flag are returned. [Rationale: UR-052; GDPR data
minimisation — aggregated totals over full-day intervals are the minimum granularity necessary for
team coordination; quality flag enables controllers to identify unreliable data without exposing
entry detail]

## Cross-cutting — observability

**SR-064:** The system shall expose a Prometheus-compatible metrics scrape endpoint at
`/actuator/prometheus` via Spring Boot Actuator and Micrometer. The endpoint shall be accessible
without authentication only from localhost; all other access shall require authentication.
[Rationale: sysadmin observability; NFR]

## Epic 9 — MCP integration

**SR-065:** The system shall, when an authenticated user generates an MCP token, insert an
`mcp_tokens` row containing: `user_id`; a user-supplied label; `token_hash` (SHA-256 of the raw
token); `created_at`; `expires_at` (nullable); `revoked_at` (NULL). The raw token shall be
returned exactly once in the response and never stored. [Rationale: UR-053]

**SR-066:** The system shall, on each MCP request bearing a Bearer token, compute the SHA-256
hash of the token, locate the matching `mcp_tokens` row, and reject the request with HTTP 401 if:
no row matches; `revoked_at IS NOT NULL`; or `expires_at IS NOT NULL AND expires_at < NOW()`.
On success, the system shall update `last_used_at = NOW()` and resolve the owning user for
authorization checks. [Rationale: UR-053; token security]

**SR-067:** The system shall delete (soft-revoke) an `mcp_tokens` row by setting `revoked_at =
NOW()` when the owning user requests revocation, and shall log the event to `security_events`.
[Rationale: UR-055]

**SR-068:** The system shall return all `mcp_tokens` rows where `revoked_at IS NULL`, joined with
owning user name, to users with `is_system_admin = true`. [Rationale: UR-056]

**SR-069:** The system shall set `revoked_at = NOW()` on any `mcp_tokens` row when a System Admin
requests revocation, regardless of owner, and shall log the event to `security_events`.
[Rationale: UR-057]

**SR-070:** The system shall expose an MCP server at `/mcp` accepting Bearer token authentication
(SR-066) and providing the following tools, each enforcing node authorization via recursive CTE
identical to the REST API:
- `get_time_entries(node_id?, user_id?, date_from?, date_to?)` — returns time entries visible to
  the token owner; if `user_id` is supplied and differs from the token owner, the token owner must
  have at least `view` on the relevant nodes and only aggregated daily totals are returned
- `get_node_tree()` — returns the node subtree visible to the token owner
- `get_tracking_status()` — returns the token owner's current tracking state
- `get_member_summaries(node_id?, date_from?, date_to?, interval?)` — returns per-member
  aggregated totals on nodes visible to the token owner for the requested interval bucket
  (day/week/month/year); SR-063 semantics apply
[Rationale: F9.6]

**SR-071:** The system shall, immediately after a user generates an MCP token, display a guided
onboarding wizard presenting: (1) the MCP server URL; (2) the raw token with a copy button and a
warning that it will not be shown again; (3) step-by-step instructions for adding the MCP server
in Claude.ai; (4) a connection-test button that invokes `get_node_tree()` and confirms success.
[Rationale: UR-058]
