# trawhile

Self-hosted time tracking service for a single company. One instance, one team.

## What it does

- Track time against a hierarchical node tree (projects, work packages, tasks, etc.)
- Fine-grained authorization per node, recursively inherited
- Reports and CSV export, filtered to what each user can see
- Request workflow per node
- GDPR-compliant: data minimization, right to erasure, configurable retention and purge

## Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3, Spring Data JDBC, Spring Security (OAuth2) |
| Database | PostgreSQL 16 |
| Frontend | Angular 18, PrimeNG 17, Tailwind CSS 3 |
| Auth | GitHub OAuth2, Google OAuth2 |
| Deployment | Docker Compose, Caddy (TLS) |

## Deployment

Copy `.env.example` to `.env` and fill in the values:

```
DB_PASSWORD=
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
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
# Backend (requires Java 21 and a running PostgreSQL instance)
./mvnw spring-boot:run

# Frontend
cd src/main/frontend
npm install
ng serve
```

## License

MIT
