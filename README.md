# trawhile

Self-hosted time OSS time tracking service, for a single company or team. This is a work in progress, and in the early stages: no release yet.

Trawhile is organized around a flexible hierarchy of work items of your choice - for instance, projects, work packages and tasks. Trawhile is simple and designed to help members, work item admins and sysadmins, all at the same time, so that time tracking does not become a time-consuming activity in itself.

## Why trawhile

### Members

- No fuss - no app, no registration (use your Google or Apple account), simple-to-use and works great on mobile, does not annoy you with emails
- Does not waste your time - quickly find and fix overlapping time entries or gaps between them
- Let's you know your work - See your activity at a glance or in detail, with powerful filters, and export in a variety of ways
- Gives you ownership of your data - no one except you can create or alter your time entries. Know when they will be deleted automatically
- And protects your data - others can see your daily summary but not your individual time entries, personal data like your email address is not stored in the database, use any name you like, anonymize your time entries and purge your personal data when leaving company or team

### Work item admins - project managers, work package managers, scrum masters

- Adapts to your workflow - structure the hierarchy of work items to your needs and make changes to the structure at any time
- Get meaningful reports quickly, for controlling and accounting - time summaries per-member or per-work item, burn-down charts, and export them in various ways
- Choose the granularity that is right for you - assign members' view, track, and admin permissions on work items - permissions are hierarchical and composable
- Stay in control - view member permissions at a glance, see who has what permissions on a work item

### Sysadmins

- Cost-effective - No monthly fee except for the hosting
- Quick setup - Self-contained, standard deployment with Docker, easy to hook-up with Prometheus and Grafana, no SMTP server required
- No manual certs provisioning - let's encrypt certificates are built-in
- No configuration required - meaningful defaults enable you to start right away and customize later
- Facilitiates database backup and restore
- GDPR and CRA compliant - right to erasure, configurable retention and automatic purge, SBOM, rate limiting and secure HTTP headers, security audit log with 90-day retention and automatic purge
- Invite members, block members

## Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4, Spring Data JDBC, Spring Security (OAuth2) |
| Database | PostgreSQL 18 |
| Frontend | Angular 21, PrimeNG 21, Tailwind CSS 4 |
| Auth | GitHub OAuth2, Google OAuth2, Apple Sign In |
| Deployment | Docker Compose, Caddy (TLS) |

## Deployment

Copy `.env.example` to `.env` and fill in the values:

```
DB_PASSWORD=
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
APPLE_CLIENT_ID=
APPLE_CLIENT_SECRET=
BOOTSTRAP_ADMIN_EMAIL=
DOMAIN=
```

Then:

```bash
docker compose up -d
```

The first OAuth2 login matching `BOOTSTRAP_ADMIN_EMAIL` is granted admin on the root node.

## Development

```bash
# Start PostgreSQL only (Spring Boot + Angular run natively with hot reload)
make development-db
mvn spring-boot:run
cd src/main/frontend && ng serve

# Or start the full stack in Docker (production-like)
make development-up
```

Run `make help` for all available targets.

## License

MIT
