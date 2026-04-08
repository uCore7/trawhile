# Security Policy

## Supported versions

Only the latest release is supported with security fixes.

## Reporting a vulnerability

Please do **not** open a public GitHub issue for security vulnerabilities.

Report privately via GitHub's [Security Advisories](../../security/advisories/new) feature.
Include:
- A description of the vulnerability and its potential impact
- Steps to reproduce or a proof-of-concept
- Affected versions if known

You can expect an acknowledgement within 72 hours and a status update within 7 days.

## Scope

This is a self-hosted application. The operator is responsible for:
- Keeping the host OS and Docker runtime up to date
- Protecting the `.env` file and database credentials
- Configuring a firewall; only ports 80 and 443 should be publicly reachable

## Security design notes

- Authentication is delegated entirely to GitHub and Google OAuth2 — no passwords are stored
- No email addresses are stored for registered users
- All HTTP responses include secure headers (CSP, HSTS, X-Frame-Options)
- CSRF protection is enabled on all mutating endpoints
- Rate limiting is applied on all OAuth2 and API endpoints
- Dependencies are checked automatically in CI (OWASP Dependency Check, npm audit)
- A CycloneDX SBOM is generated on every build
