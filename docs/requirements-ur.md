# User requirements (UR)

Format: "The [stakeholder] shall be able to [capability]. [Goal: reason]"

## Stakeholders

| ID | Stakeholder |
|---|---|
| ST-1 | User — any registered company member |
| ST-2 | Node Admin — user with `admin` authorization on a node |
| ST-3 | Node Admin of root — Node Admin whose scope covers the full tree |
| ST-4 | System Admin — `is_system_admin = true`; infrastructure operator |
| ST-5 | Works Council — employee representative body; GDPR stakeholder |
| ST-6 | Data Protection Officer — GDPR compliance authority |
| ST-7 | Company — the organization using the instance to track time |

## Goals

| ID | Goal |
|---|---|
| G-1 | Enable companies to track time against a self-defined hierarchical node structure |
| G-2 | Provide fine-grained, recursively inherited authorization over node visibility and tracking |
| G-3 | Support accurate reporting and CSV export of tracked time |
| G-4 | Comply strictly with GDPR; collect only data necessary for operation |
| G-5 | Comply with EU Cyber Resilience Act (CRA) security and transparency requirements |
| G-6 | Operate securely with no persistent secrets beyond OAuth2 provider credentials |

## Assumptions and constraints

| ID | Statement |
|---|---|
| A-1 | One deployed instance serves exactly one company |
| A-2 | Authentication is delegated entirely to GitHub and/or Google OAuth2 |
| A-3 | No email server is required; invitation emails are generated as mailto: links only |
| A-4 | The company timezone is the reference for date boundaries in reports and freeze date evaluation |
| C-1 | All timestamps are stored in UTC |
| C-2 | No email addresses are stored for registered users |
| C-3 | Security event logs are retained for exactly 90 days |
| C-4 | Data retention period is configurable with a minimum of 2 years |
| C-5 | The system must be deployable via Docker Compose on a single VPS |

## Epic 1 — Company administration

**Bootstrap**
- UR-001: The System Admin shall be able to designate a first Node Admin of root via an environment
  variable, so that the system can be bootstrapped without prior in-app configuration. [Goal: G-2]

**Root Node Admin management**
- UR-002: The System Admin shall be able to grant `admin` authorization on the root node to an
  existing user. [Goal: G-2]
- UR-003: The System Admin shall be able to revoke `admin` authorization on the root node from a
  user, provided at least one other root Node Admin exists. [Goal: G-2]

**User management**
- UR-004: The Node Admin of root shall be able to view all registered users. [Goal: G-1]
- UR-005: The Node Admin of root shall be able to view all pending invitations. [Goal: G-1]
- UR-006: The Node Admin of root shall be able to invite a person by email address. [Goal: G-1]
- UR-007: The Node Admin of root shall be able to cancel a pending invitation. [Goal: G-1]
- UR-008: The Node Admin of root shall be able to remove a user from the company. [Goal: G-1, G-4]
- UR-009: The Node Admin of root shall be able to view all node authorization assignments of a user
  across the tree. [Goal: G-2]

**Company settings**
- UR-010: The Node Admin of root shall be able to view company settings (name, timezone, freeze
  date, retention years, node retention extra years, purge schedule). [Goal: G-1]
- UR-011: The Node Admin of root shall be able to update company settings. [Goal: G-1]

**Data**
- UR-012: The Node Admin of root shall be able to export all company data to CSV. [Goal: G-3]
- UR-013: The Node Admin of root shall be able to import company data from CSV on a clean
  instance. [Goal: G-1]

## Epic 2 — Node administration

- UR-014: The Node Admin shall be able to view the details and direct children of any node within
  their scope. [Goal: G-1]
- UR-015: The Node Admin shall be able to create a child node under any node within their
  scope. [Goal: G-1]
- UR-016: The Node Admin shall be able to edit the name and description of any node within their
  scope. [Goal: G-1]
- UR-017: The Node Admin shall be able to reorder the child nodes of any node within their
  scope. [Goal: G-1]
- UR-018: The Node Admin shall be able to deactivate any node within their scope that has no
  active children. [Goal: G-1]
- UR-019: The Node Admin shall be able to reactivate any deactivated node within their
  scope. [Goal: G-1]
- UR-020: The Node Admin shall be able to move a node to a different parent, provided they have
  admin rights on both the node and the destination parent, and the destination is not within the
  node's own subtree. [Goal: G-1]
- UR-021: The Node Admin shall be able to grant `view`, `track`, or `admin` authorization on any
  node within their scope to any existing user. [Goal: G-2]
- UR-022: The Node Admin shall be able to revoke a user's authorization on any node within their
  scope, provided the user is not the last `admin` of that node. [Goal: G-2]
- UR-023: The Node Admin shall be able to view all authorization assignments on a node,
  distinguishing direct assignments from those inherited from ancestors. [Goal: G-2]

## Epic 3 — Time tracking

- UR-024: The User shall be able to view their current tracking status, including the node being
  tracked, elapsed time, and start time. [Goal: G-1]
- UR-025: The User shall be able to view their recent time entry history, with overlapping entries
  visually flagged. [Goal: G-1]
- UR-026: The User shall be able to start tracking a task by navigating the node tree. [Goal: G-1]
- UR-027: The User shall be able to start tracking a task from a personal quick-access list of up
  to 9 nodes. [Goal: G-1]
- UR-028: The User shall be able to switch to a different node atomically, without an explicit
  stop step. [Goal: G-1]
- UR-029: The User shall be able to stop tracking. [Goal: G-1]
- UR-030: The User shall be able to add, remove, and reorder nodes in their quick-access
  list. [Goal: G-1]
- UR-031: The User shall be able to create a time entry retroactively for any node they can
  track. [Goal: G-1]
- UR-032: The User shall be able to edit the node, start time, and end time of any of their own
  time entries, provided the entry is not in a frozen period. [Goal: G-1]
- UR-033: The User shall be able to delete any of their own time entries, provided the entry is
  not in a frozen period. [Goal: G-1]
- UR-034: The User shall be able to duplicate a time entry, specifying a new start and end
  time. [Goal: G-1]
- UR-035: The User shall be able to assign a personal color to any node visible to
  them. [Goal: G-1]

## Epic 4 — Reporting & export

- UR-036: The User shall be able to view a time report filtered by date range, user, and node,
  limited to nodes visible to them. [Goal: G-3]
- UR-037: The User shall be able to toggle a time report between a summary view and a detailed
  view. [Goal: G-3]
- UR-038: The User shall be able to export the current report view to CSV. [Goal: G-3]

## Epic 5 — Requests

- UR-039: The User shall be able to submit a request against any node visible to them, using a
  system-defined template or free text. [Goal: G-1]
- UR-040: The User shall be able to view the history of their own submitted requests on any
  visible node. [Goal: G-1]
- UR-041: The Node Admin shall be able to view all requests submitted against nodes within their
  scope. [Goal: G-1]
- UR-042: The Node Admin shall be able to close any open request within their scope. [Goal: G-1]

## Epic 6 — Account

- UR-043: The User shall be able to view their stored profile information (name, picture). [Goal: G-4]
- UR-044: The User shall be able to link an additional OAuth2 provider to their
  account. [Goal: G-1]
- UR-045: The User shall be able to unlink an OAuth2 provider from their account, provided at
  least one other provider remains linked. [Goal: G-1]
- UR-046: The User shall be able to view all their node authorization assignments across the
  tree. [Goal: G-2]
- UR-047: The User shall be able to anonymise their own account, replacing all personal data with
  a placeholder while preserving time entry history. [Goal: G-4]
- UR-048: The User shall be able to view the About page, including application version,
  third-party licenses, and a downloadable SBOM. [Goal: G-5]

## Epic 7 — Security & audit

- UR-049: The System Admin shall be able to view the security event log, filtered by event type,
  user, and date range. [Goal: G-5]

## Epic 8 — Data lifecycle

- UR-050: The Node Admin of root shall be able to set the data retention period, node retention
  extra period, and purge schedule in company settings. [Goal: G-4, G-5]
- UR-051: The Node Admin of root and System Admin shall be able to view a unified pre-notification
  starting 6 weeks before each scheduled purge, showing upcoming purge and node deletion dates and
  affected record counts. [Goal: G-4]
