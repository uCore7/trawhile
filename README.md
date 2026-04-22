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

# Run the backend test suite
./scripts/mvn-local.sh test

# Or start the full stack in Docker (production-like)
make development-up
```

`./scripts/mvn-local.sh spring-boot:run` automatically skips the frontend Maven plugin because the native dev flow serves Angular separately via `ng serve`.

`./scripts/mvn-local.sh test` runs the backend test suite via the repository Maven wrapper settings.

### Traceability

Use the local traceability checker to compare requirements, planned test cases, implemented `@Tag("TE-...")` tests, and executed backend test reports.

```bash
# Structural traceability only (good while fixing missing coverage)
./scripts/check-traceability.py --no-execution

# Full backend traceability, including executed test reports in target/surefire-reports
./scripts/mvn-local.sh test
./scripts/check-traceability.py

# Alternative policies / output formats
./scripts/check-traceability.py --rule-profile strict
./scripts/check-traceability.py --json
./scripts/check-traceability.py --show-methods
```

Default behavior follows the repository process rules: `UR-F` and `UR-Q` must have at least one SR, and `SR-F` and `SR-Q` must have at least one TE. Use `--rule-profile strict` if you want the checker to require coverage for every non-retired UR and every SR.

This script is intended for local use while closing traceability gaps; it is not wired into CI yet.

### Codex in a Docker sandbox

This repository also includes a `.devcontainer/` setup for running Codex inside Docker while still giving the agent full access inside that container. This follows the current Codex guidance for containerized environments: if Docker is your intended isolation boundary, run Codex inside the container with `danger-full-access` instead of trying to stack a second Linux sandbox inside it.

What the devcontainer does:

- starts a `workspace` container for your editor / terminal work
- starts a dedicated PostgreSQL `db` service reachable as `db:5432`
- sets `SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/trawhile` inside the workspace container
- installs the Codex CLI with `npm`
- seeds a container-local Codex config on container startup with:

```toml
sandbox_mode = "danger-full-access"
approval_policy = "never"
```

Terminal-first workflow:

```bash
make devcontainer-up
```

Interactive TUI mode:

```bash
make devcontainer-codex
```

Batch / agentic mode from a task file:

```bash
make devcontainer-codex-task TASK=tasks/00-base-it.md
make devcontainer-codex-task TASK=tasks/00-base-it.md ARGS='--json'
```

Useful companion commands:

```bash
make host-auth-check
make devcontainer-shell
make devcontainer-logs
make devcontainer-down
```

This path does not require VS Code. It uses `docker compose` directly and keeps Codex entirely inside the `workspace` container.

This setup is intentionally focused on ChatGPT-managed Codex auth inside the devcontainer. The recommended flow is to validate the host `auth.json` first, run `codex login` on the host only if that check fails, then sync the host auth file into the container.

The host and devcontainer keep separate `CODEX_HOME` directories by design. If `codex` works on the host but the devcontainer does not, sync the host auth cache into the container before debugging anything else.

On systems where Codex stores credentials in the OS credential store instead of `~/.codex/auth.json`, switch the host to file-based storage before you sync:

```toml
# ~/.codex/config.toml on the host
cli_auth_credentials_store = "file"
```

The browser-based ChatGPT login flow is often awkward in containers because Codex opens a localhost callback server inside the container. The current Codex docs explicitly recommend device-code authentication for remote or headless environments and for setups where the localhost callback is blocked.

Reliable ChatGPT-managed flow:

First try:

```bash
make host-auth-check
make devcontainer-up
make devcontainer-sync-auth
make devcontainer-codex-task TASK=tasks/00-base-it.md
```

If `make host-auth-check` fails, repair host auth first:

```bash
codex login
make host-auth-check
```

If you are upgrading from an older version of this devcontainer setup and see `Permission denied` for `${CODEX_HOME}/config.toml`, restart the devcontainer so Docker creates the fresh Codex home volume used by the current setup:

```bash
make devcontainer-down
make devcontainer-up
```

The task runner also accepts extra `codex exec` arguments when run directly inside the container:

```bash
./scripts/run-codex-task.sh tasks/00-base-it.md --json
./scripts/run-codex-task.sh tasks/00-base-it.md --output-last-message /tmp/codex-last.txt
```

If you prefer a raw Docker command instead of `make`, the equivalent startup is:

```bash
docker compose -f .devcontainer/docker-compose.yml up -d --build
docker compose -f .devcontainer/docker-compose.yml exec \
  workspace bash -lc "cd /workspaces/timetracker && exec codex"
```

Raw batch command:

```bash
docker compose -f .devcontainer/docker-compose.yml exec \
  workspace bash -lc "cd /workspaces/timetracker && ./scripts/run-codex-task.sh tasks/00-base-it.md"
```

If you do use the Codex IDE extension later, keep the repository attached to the devcontainer before you start the agent so the agent runs inside the container too.

Once inside the devcontainer, use the normal project commands:

```bash
./scripts/mvn-local.sh spring-boot:run
cd src/main/frontend && npm install && npx ng serve
./scripts/mvn-local.sh test
```

Inside this setup you do not need `make development-db`, because the devcontainer already starts PostgreSQL for you.

Security note: this setup is intentionally powerful. It mounts `/var/run/docker.sock` so Testcontainers and other Docker-backed flows can work from inside the workspace container. That also means code running in the devcontainer can control the host Docker daemon. Use this only for trusted repositories.

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

`./scripts/mvn-local.sh test` now skips the frontend Maven plugin automatically. Database-backed tests still need Docker/Testcontainers access; in restricted agent sandboxes, request escalation for `./scripts/mvn-local.sh test` rather than treating blocked DB sockets as an application bug.

Run `make help` for all available targets.

The OICD configuration for development is at https://console.cloud.google.com/auth/clients?project=trawhile-dev

## License

MIT
