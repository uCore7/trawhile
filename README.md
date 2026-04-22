# trawhile

Self-hosted time OSS time tracking service, for a single company or team. This is a work in progress, and in the early stages: no release yet.

Trawhile is organized around a flexible hierarchy of work items of your choice - for instance, projects, work packages and tasks. Trawhile is simple and designed to help members, work item admins and sysadmins, all at the same time, so that time tracking does not become a time-consuming activity in itself.

## Why trawhile

### Members

- No fuss - no app, no registration (use Apple, Google, Microsoft Entra or Keycloak for login), simple-to-use and works great on mobile, does not annoy you with emails
- Does not waste your time - quickly find and fix overlapping time records or gaps between them
- Lets you know your work - See your activity at a glance or in detail, with powerful filters, and export in a variety of ways
- Gives you ownership of your data - informs you of your rights, and no one except you can create or alter your time records. Know when old time records will be deleted automatically
- Protects your data - others can see your aggregated totals but not your individual time records, your email address is not stored in the database, use the name you like, anonymize your time records and purge your personal data when leaving company or team

### Work item admins - project managers, work package managers, scrum masters

- Tailor things to your needs - structure the hierarchy of work items and make changes to the structure at any time
- Get meaningful reports quickly, for controlling and accounting, and export them in various ways
- Choose the granularity that is right for you - assign members' view, track, and admin permissions on work items - permissions are hierarchical and composable
- Stay in control - view member permissions at a glance, see who has what permissions on a work item
- Hook-up AI via MCP for downstream processing of time records

### Sysadmins

- Cost-effective - No monthly fee except for the hosting
- Single-step deployment - with Docker
- Straightforward configuration with application.yml and .env - meaningful defaults enable you to start right away and customize later, no access to an SMTP server needs to be provided
- No manual certs provisioning - let's encrypt certificates are built-in
- Know when things go awry - comprehensive Prometheus metrics are built-in, and a ready-to-import Grafana dashboard and AlertManager rules are provided
- GDPR compliant and CRA-ready - right to erasure, configurable retention and automatic purge, SBOM, rate limiting and secure HTTP headers, security audit log with 90-day retention and automatic purge
- Friendly with neighbors - OpenAPI spec for quick integration with project management tools

## Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4, Spring Data JDBC, Spring Security (OIDC login) |
| Database | PostgreSQL 18 |
| Frontend | Angular 21, PrimeNG 21, Tailwind CSS 4 |
| Auth | Google, Apple, Microsoft Entra ID, Keycloak (OIDC) |
| Deployment | Docker Compose, Caddy (TLS) |

## Deployment

**1. Secrets** — copy `.env.example` to `.env` and fill in the values:

```
DB_PASSWORD=
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
APPLE_CLIENT_ID=
APPLE_CLIENT_SECRET=
MICROSOFT_CLIENT_ID=
MICROSOFT_CLIENT_SECRET=
KEYCLOAK_CLIENT_ID=
KEYCLOAK_CLIENT_SECRET=
KEYCLOAK_ISSUER_URI=
BOOTSTRAP_ADMIN_EMAIL=
DOMAIN=
```

Only configure the providers you want to enable; leave the rest empty.

**2. System configuration** — copy `config/application.yml.example` to `config/application.yml` and adjust as needed. The defaults work for a quick start; at minimum set `trawhile.name` and `trawhile.timezone`.

**3. Start**:

```bash
docker compose up -d
```

The first OIDC login matching `BOOTSTRAP_ADMIN_EMAIL` is routed through the bootstrap registration flow and is granted admin on the root node after GDPR acknowledgement completes.

## Development

```bash
# Start PostgreSQL only (Spring Boot + Angular run natively with hot reload)
make development-db
./scripts/mvn-local.sh spring-boot:run
cd src/main/frontend && ng serve

# Or start the full stack in Docker (production-like)
make development-up
```

`./scripts/mvn-local.sh spring-boot:run` automatically skips the frontend Maven plugin because the native dev flow serves Angular separately via `ng serve`.

If you already created a PostgreSQL data volume with an older image/layout, recreate it once after pulling these changes:

```bash
docker compose -f docker-compose.dev.yml down -v
make development-db
```

To keep Maven and Maven Wrapper caches inside the project instead of `~/.m2`, use:

```bash
./scripts/mvn-local.sh test
./scripts/mvn-local.sh -DskipTests -Dskip.npm=true -Dskip.installnodenpm=true prepare-package
```

This uses `.mvn/home` for the wrapper distribution cache and `.mvn/repository` for the Maven local repository.

Run `make help` for all available targets.

The OICD configuration for development is at https://console.cloud.google.com/auth/clients?project=trawhile-dev

## License

MIT
