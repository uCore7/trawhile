# 0019. Bucket reports server-side with caller-supplied IANA timezone

## Status

- Proposed, 2026-06-02

## Context

This decision answers: how does the backend produce report buckets whose boundaries align with the user's local day, week, month, or year, given that the backend must aggregate (never expose raw time records) and that the project commits to UTC at storage and on the API wire format?

UR-00-C10 establishes: storage and API wire format are UTC; user-facing time-window semantics are user-local; the frontend converts user-local boundaries to UTC instants and supplies time-zone information to the backend where bucket alignment depends on it. UR-04-F01 and UR-04-F04 forbid per-record exposure in reports — the backend cannot satisfy the per-user-local bucketing constraint by sending raw records to the frontend for client-side bucketing, because UR-04-F04 in particular protects the privacy of other members' records that the caller has aggregated visibility into but no per-record visibility.

Concretely: a user in CET clicking "this week" expects bucket boundaries at Monday 00:00 local through Sunday 23:59:59 local; the records in the database carry UTC timestamps, and a naive `date_trunc('week', started_at)` in UTC produces week boundaries shifted by one or two hours (the shift depends on DST). A record that started Sunday 23:30 CET belongs to the user's local week but to the previous ISO week in UTC. PostgreSQL exposes `AT TIME ZONE` precisely for this; the question is how the time-zone identifier reaches the backend.

Three candidates for getting the time zone to the backend:

- **Persist per-user time zone on the user profile.** The backend reads the caller's TZ from the identity store on each report request. Couples report logic to identity persistence; introduces a profile field that must be set somewhere (admin UI? OIDC claim mapping?); stale when a user travels; the in-flight request and the stored profile can disagree.

- **Per-request time-zone parameter (caller-supplied).** The frontend reads the browser's resolved IANA name (`Intl.DateTimeFormat().resolvedOptions().timeZone`) and sends it as a query parameter on report endpoints that bucket. The backend uses the supplied identifier in `date_trunc(... AT TIME ZONE ...)` for the duration of the request only; nothing is stored. The TZ is implicitly "the user's current browser TZ" — correct when travelling, correct on DST transitions because IANA names carry DST rules, no profile persistence.

- **Frontend supplies the bucket boundaries themselves.** The backend takes a list `buckets: [{ start, end }, ...]` of UTC instants and groups against them. Backend stays TZ-agnostic; frontend computes user-local boundaries and converts each to UTC. Most flexible, but the wire payload grows with bucket count and the backend's GROUP BY ladder is replaced by per-bucket `CASE` or `LATERAL` joins — less natural SQL.

Constraints from the deployment and surrounding decisions:

- Backend is the single source of truth for report results (UR-04-F01 / UR-04-F04 minimisation). Per-request TZ keeps the backend authoritative without coupling to identity persistence.
- PostgreSQL handles IANA names natively. `date_trunc('week', started_at AT TIME ZONE 'Europe/Zurich')` is correct under DST and across year boundaries. No application-side conversion logic is needed.
- Reports are an Account-Holder-only surface (session-only per UR-00-C08(a)) and are not reached by API keys, so caller-supplied TZ does not introduce a new API-client trust surface beyond the browser session that already authenticated the user.

### Privacy side channel from caller-supplied TZ

A caller-supplied TZ on `/api/reports/member-summaries` (UR-04-F04) interacts with the aggregation in a way that creates a measurable side channel against the no-per-record-exposure property.

The attack: a caller fires the same member-summary query repeatedly with different IANA TZ values. Each TZ shifts day, week, month, and year bucket boundaries by the zone's offset. Comparing per-bucket totals across many TZ values lets the caller binary-search when individual target-user records actually started or ended, narrowing the inferred timestamp to the resolution of the smallest IANA offset increment.

Today's IANA offset grid is 15 minutes:

| Zone | Offset |
|---|---|
| `Asia/Kolkata`, `Asia/Tehran`, `Asia/Kabul`, `Asia/Yangon`, `America/St_Johns` | hour + 30 min |
| `Asia/Kathmandu`, `Pacific/Chatham`, `Australia/Eucla` | hour + 45 min |

So an attacker willing to fire ~96 multi-TZ queries can resolve individual record start times to 15 minutes despite never seeing a single record row.

Three responses to this side channel were considered:

1. **Validate caller-supplied TZ to be hour-aligned.** Reject IANA names whose current offset has non-zero minutes. Caps the leak at 1 hour. Breaks reports for users in India, Iran, Afghanistan, Nepal, Myanmar, Newfoundland, and several smaller zones — they get HTTP 400 and have no good fallback. Bad UX for those populations.

2. **Snap the backend-computed offset to the nearest hour.** Accept any IANA name; round the offset before bucketing. Reports work everywhere, but Indian / Iranian / Nepali users see bucket boundaries off from their local midnight by 30–45 minutes. Confusing.

3. **Accept the bounded leak as policy.** Allow any IANA name. Document explicitly that aggregated reports admit a sub-hour inference under deliberate multi-TZ querying. Cost: ~15-minute granularity inference is theoretically possible by an attacker who is willing to fire dozens of report queries with different TZ values, which is loud in audit logs (each query is an authenticated, observable request). Benefit: every user gets bucket boundaries that align with their actual local time.

Trawhile's threat model and audience (small-company time tracking, internal users, no high-stakes adversarial scenario) makes (3) the proportionate choice. UR-04-F04 has been clarified to acknowledge this bounded leak as accepted policy.

## Decision

Adopt **per-request IANA time-zone parameter, applied uniformly to all report endpoints that bucket by time**. The currently-relevant endpoints are `GET /api/reports/aggregate` (own reports, UR-04-F01) and `GET /api/reports/member-summaries` (UR-04-F04). Both accept a required `tz` query parameter carrying an IANA time-zone identifier (e.g., `Europe/Zurich`, `America/New_York`, `Asia/Kolkata`). The frontend obtains the value via `Intl.DateTimeFormat().resolvedOptions().timeZone`.

The backend uses the supplied identifier in `date_trunc(<bucketSize> AT TIME ZONE <tz>)` for the bucket aggregation in the SQL query. The backend validates the parameter against `pg_timezone_names` and rejects unknown identifiers with HTTP 400 before executing any aggregation. The backend does **not** restrict the parameter to hour-aligned zones or otherwise quantize the offset; sub-hour zones are accepted and produce locally-correct bucket boundaries for callers in those regions.

The TZ parameter is required only on endpoints whose result shape depends on bucket alignment. Endpoints that return single totals over a fixed UTC interval (no time bucketing) do not accept the parameter. Single-record reads (tracking history, current tracking status) do not accept the parameter either — they return UTC instants and the frontend renders in local time as a presentation concern.

User profiles store no time-zone field.

The sub-hour inference side channel described above is accepted policy and clarified in UR-04-F04. It is preferred over the alternatives (no user-local alignment, or per-user persisted TZ with administrative tooling) because user-local report buckets matter more for daily use than perfect privacy against an attacker willing to fire dozens of multi-time-zone queries.

## Consequences

Backend report logic is TZ-aware only at the SQL `AT TIME ZONE` boundary; no application-code TZ math is introduced. PostgreSQL's IANA support handles DST and historical TZ rule changes correctly.

Reports automatically follow a travelling user: closing the laptop in Zurich and opening it in Tokyo produces correct local-bucket reports the moment the browser's resolved TZ updates. No staleness window.

Backend has no persistent TZ state for any user; if user-profile TZ ever becomes desirable for some other reason (e.g., choosing display formats outside reports), the decision can be revisited without affecting report logic.

A caller can infer individual target-user record start times to ~15-minute granularity by firing the same member-summary query with multiple IANA TZ values. This is documented in UR-04-F04 as bounded accepted policy. The audit log naturally surfaces multi-TZ querying patterns; operators concerned about this exfiltration vector can add alerting on per-account report-query rate with varying `tz` values.

Two report endpoints (`/api/reports/aggregate`, `/api/reports/member-summaries`) carry a required `tz` parameter. The previously-defined `interval` enum is simplified to bucket sizes only (`hour`, `day`, `week`, `month`, `year`) — the period-selector values (`ytd`, `mtd`) move into the frontend, which computes the corresponding `from` / `to` instants. The `hour` bucket size is accepted by the backend because the side channel described above already permits ~15-minute timing inference, so a direct hourly bucket on a single query is not finer-grained than what is already inferable; the frontend chooses which bucket sizes to offer for a given date range and the backend imposes no restriction. SR-04-F01.F02 (the UTC-date evaluation rule for `ytd` / `mtd`) is superseded and dropped. SR-04-F01.C01 (full-day UTC boundary validation) is reframed: `from` and `to` remain UTC instants computed by the frontend, and the backend imposes no boundary alignment check on them.

If the caller-supplied TZ is unknown to PostgreSQL (typo, deprecated zone), the report request fails fast with HTTP 400 rather than silently bucketing against a fallback. This is louder than silent fallback to UTC and matches the user expectation that a misconfigured client should be visible.
