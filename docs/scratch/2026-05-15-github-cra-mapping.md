# How GitHub supports implementing CRA

How GitHub's feature set maps to the CRA obligations that fall on ST-7 (trawhile Manufacturer).

## Inbound side — vulnerability intake (CRA Annex I §1(2)(d), §2(5)–(6))

- **Private Vulnerability Reporting (PVR)**: a built-in private intake channel on every repo's Security tab. Researchers submit reports without exposing the issue publicly. Maintainers triage in private. This directly satisfies "an effective mechanism, accessible to the public, for reporting of vulnerabilities."
- **SECURITY.md** at the repo root: GitHub surfaces it prominently and links it from the Security tab. This is the natural place to publish the *Coordinated Vulnerability Disclosure policy* required by CRA Annex I §2(5).
- **CVE numbering**: GitHub is a CVE Numbering Authority (CNA), so a manufacturer can request a CVE ID directly from within a private advisory draft — no separate CNA workflow.

## Outbound side — advisory publication (CRA Annex I §2(5), §2(7)–(8))

- **GitHub Security Advisories (GHSA)**: published per repo. Each advisory has a stable ID (GHSA-xxxx-xxxx-xxxx), CVE linkage, affected version ranges, severity (CVSS), patched-versions list, and free-text mitigation guidance. This is the artifact that satisfies "information about fixed vulnerabilities."
- **Release coupling**: an advisory can be tied to a specific release tag. Users browsing Releases see the advisory next to the patched version — fulfilling §2(8)'s requirement that updates be "accompanied by advisory messages providing users with the relevant information."
- **Atom/RSS feed per repo**: every repo's advisories have a public feed (`/security/advisories.atom`). ST-4 can subscribe their incident-response tooling to it — this is what makes UR-F073's notification path automatic rather than depending on ST-4 remembering to check.
- **Repo watching with "Custom → Security alerts only"**: lighter-weight alternative to RSS for human-driven workflows.

## Lifecycle and traceability (CRA Article 13, Annex I Part I & II §1)

- **Dependency graph + SBOM export**: GitHub computes the dependency tree and can export SPDX or CycloneDX SBOMs on demand. This feeds UR-F048's "downloadable SBOM" requirement.
- **Dependabot alerts**: notifies ST-7 when a dependency itself has a CVE. This isn't an inbound-from-researchers channel; it's ST-7's own due-diligence pipeline supporting §2(1) ("identify vulnerabilities"). Different actor, same regulatory section.
- **Signed releases / Sigstore / SLSA**: provenance attestations on artifacts support §1 ("appropriate level of cybersecurity… on the basis of risk") and the conformity-assessment paper trail.
- **Branch protection + required reviews + CI gates**: process controls that support §2(3) ("effective and regular tests").

## What GitHub does NOT cover (gaps ST-7 must handle out-of-band)

- **Article 14 incident reporting to ENISA/CSIRT**: 24h initial notification, 72h detailed, 14d final. GitHub doesn't notify regulators — that's ST-7's direct legal obligation, separate from GHSA publication. The two workflows often run in parallel: file the ENISA notification within 24h, then drop the advisory publicly once embargo lifts.
- **Conformity assessment** (Article 32, Annex IV): the formal procedure that produces the CE-marking technical file. GitHub holds the evidence (commits, advisories, tests, SBOMs); the assessment itself is a process around the repo, not a feature in it.
- **Single point of contact** (Article 13(2)): a public contact address for market-surveillance authorities. SECURITY.md typically lists this, but the obligation is to make it reachable, not to host it.
- **Support-period commitment** (Article 13(8)): the "for how long do we patch this product" promise — published on the About page or SECURITY.md, but it's a manufacturer commitment, not a GitHub feature.
- **Public end-user information** (Article 13(19), Annex II): some of this can live in GitHub Pages or the SECURITY.md, but it's content, not a feature.

## Net practical workflow for trawhile, in CRA terms

1. ST-8 finds vuln → submits via **PVR** → ST-7 triages privately.
2. ST-7 develops fix on a private fork (GitHub supports temporary private branches for advisories).
3. ST-7 cuts a release and publishes the **GHSA** simultaneously, with the patched-version range filled in.
4. **Atom feed** + **release notes** push the advisory to ST-4's subscribed tooling.
5. ST-7 separately files **Article 14 notification** to ENISA (out-of-band, not via GitHub).
6. **SBOM** snapshot at release time is exported for the technical file.
7. ST-8 receives **GHSA-author credit** (optional attribution) — addresses ST-8's "optional attribution" interest.

This is essentially the workflow GitHub has been refining since 2019 for OSS maintainers under coordinated disclosure, which lines up well with CRA's coordinated-disclosure expectation. The largest remaining ST-7 process burden is Article 14 regulatory notification and the conformity-assessment paper trail — neither of which any code-hosting platform can fully automate.

## Notification routing — who gets notified

GitHub offers several layers of configurable notification routing, from per-user UI subscriptions up to fully programmable webhooks and Actions automations.

### Per-user, UI-level (zero config beyond clicking)

- **Watch with "Custom → Security alerts only"**: each user picks which repos to receive advisory notifications from. Delivered via web/email/mobile per the user's GitHub notification preferences.
- **Email notifications for the org's own GHSA database**: org members can opt in.

### Per-repo, pull-based feeds (anyone can consume)

- **Atom feed at `/security/advisories.atom`**: public, no auth needed. Any RSS reader, SIEM, or feed-watching tool can subscribe.
- **REST API `GET /repos/{owner}/{repo}/security-advisories`** and **GraphQL `securityAdvisories`**: same data, polled programmatically.

### Webhooks (event-driven push, configured per repo or per org)

- **`repository_advisory`** event — fires on publish, update, withdraw of a per-repo GHSA. This is the precise hook for "ST-7 published an advisory, notify someone."
- **`security_advisory`** event — fires for entries in the global GHSA database (broader, less relevant here).
- **`security_and_analysis`** event — fires when Security features are enabled/disabled on the repo.
- **`release`** event — useful if you want to couple advisory notification with release publication.

Webhooks deliver HTTP POSTs to a configured URL with a signed payload. The recipient can be Slack, PagerDuty, an in-house bot, an email-relay function, anything that accepts HTTP.

### GitHub Actions (most flexible — code, not just config)

- A workflow with `on: repository_advisory` runs whenever a GHSA is published.
- Inside the workflow you can do anything: post to Slack/Teams/Discord, send email via SendGrid/SES, file a Jira ticket, page on-call via PagerDuty/Opsgenie, push to Mastodon, update a status page, etc.
- This is how most projects implement "tell the whole community immediately when an advisory drops" — the workflow fans out to wherever the audience actually lives.

### Org/team-level routing (for ST-7's internal triage)

- **Security managers** team role: dedicated permission to triage advisories.
- **CODEOWNERS**: auto-assign reviewers when patches touch their code paths.
- **Custom roles + team subscriptions**: route advisory notifications to a "security-response" team instead of pinging all maintainers.

### For ST-4 specifically (the external operator)

ST-4 isn't part of ST-7's GitHub org, so they configure pull-side, not push-side:

- Subscribe to the Atom feed from their incident-response tooling (the most automatable path — feed-watchers exist for every SIEM and chatops platform).
- Or watch the repo with "Security alerts only" if they want notifications in their personal GitHub account.
- Or run their own GitHub Actions workflow on a fork that watches upstream advisories.

GitHub does **not** provide a "ST-7 enters ST-4's email and we'll notify them" feature — deployers subscribe themselves. That's actually consistent with CRA's "discoverable channel" framing rather than "manufacturer maintains a customer list," which would have GDPR implications.

### Implication for UR-F073

For UR-F073's "ST-4 shall be informed" requirement, the implementation path is: publish advisory via GHSA → ST-4 subscribes their pipeline to the Atom feed or watches the repo. The architecture decision is just which side of that subscription trawhile documents in the About page (almost certainly: link to the repo's security page, or to the Atom feed URL directly).
