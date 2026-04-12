# Architecture

## Overview

```
Browser (Angular SPA)
  │  HTTPS (Caddy reverse proxy + Let's Encrypt)
  ▼
Spring Boot (embedded Tomcat)
  │  JDBC
  ▼
PostgreSQL
```

Single deployable unit: one Spring Boot JAR + one PostgreSQL instance + one Caddy process, orchestrated by Docker Compose. The Angular SPA is served as static files from Spring Boot (embedded in the JAR via the Maven frontend plugin — no separate Node server in production).

---

## Backend — Spring Boot

### Technology versions

| Dependency | Version |
|---|---|
| Java | 25 (LTS) |
| Spring Boot | 4.x |
| Spring Data JDBC | via Spring Boot BOM |
| Flyway | via Spring Boot BOM |
| Spring Security | via Spring Boot BOM |
| bucket4j | 8.x (Spring Boot integration) |
| PostgreSQL driver | via Spring Boot BOM |

### Package layout

```
com.trawhile
  config/
    SecurityConfig.java        — Spring Security, OAuth2, CSRF, session, headers
    SchedulingConfig.java      — @EnableScheduling
    RateLimitConfig.java       — bucket4j beans
    TrawhileConfig.java        — @ConfigurationProperties("trawhile"); validated on startup (SR-088)
  domain/
    Node.java                  — record; maps to nodes table
    TimeEntry.java
    User.java
    UserProfile.java
    NodeAuthorization.java
    Request.java
    SecurityEvent.java
    PurgeJob.java
    PendingInvitation.java
    QuickAccess.java
    NodeColor.java
  repository/
    NodeRepository.java        — Spring Data JDBC CrudRepository
    TimeEntryRepository.java
    UserRepository.java
    UserProfileRepository.java
    NodeAuthorizationRepository.java
    RequestRepository.java
    SecurityEventRepository.java
    PurgeJobRepository.java
    PendingInvitationRepository.java
    QuickAccessRepository.java
    NodeColorRepository.java
    AuthorizationQueries.java  — NamedParameterJdbcTemplate; all recursive CTEs (Q1–Q4)
  service/
    AuthorizationService.java  — thin wrapper over AuthorizationQueries; used by all services
    NodeService.java
    TimeEntryService.java
    TrackingService.java
    ReportService.java
    UserService.java
    RequestService.java
    AccountService.java
    SecurityEventService.java
    ReportExportService.java
  lifecycle/
    ActivityPurgeJob.java      — @Scheduled; activity purge driver
    NodeDeletionJob.java       — @Scheduled; node deletion driver
    PurgeJobCoordinator.java   — resumes active jobs on startup; shared batch logic
  sse/
    SseEmitterRegistry.java    — ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>
    SseEvent.java              — event type enum + payload wrapper
    SseDispatcher.java         — pushes typed events to relevant sessions
  security/
    OAuth2LoginSuccessHandler.java   — handles login vs. provider-linking flows
    TrawhileOidcUserService.java     — matches pending_invitations, stores session data for GDPR flow
    AuthorizationCheckFilter.java    — not used; checks are in service layer
  web/
    AuthController.java        — POST /api/v1/auth/logout
    SettingsController.java
    UserController.java
    InvitationController.java
    NodeController.java
    AuthorizationController.java
    RequestController.java
    TrackingController.java
    TimeEntryController.java
    QuickAccessController.java
    ReportController.java
    AccountController.java
    SecurityEventController.java
    AboutController.java
    SseController.java
    dto/
      request/                 — one class per request body
      response/                — one class per response shape
```

---

## Key technical decisions

### 1. Data access — Spring Data JDBC + NamedParameterJdbcTemplate

**Simple CRUD**: Spring Data JDBC `CrudRepository` for all tables. Domain objects are plain Java records annotated with `@Table` and `@Id`. No JPA, no lazy loading, no proxies.

**Recursive CTEs**: All authorization queries (Q1–Q4 from schema.sql) and the purge queries are written as raw SQL in `AuthorizationQueries.java`, executed via `NamedParameterJdbcTemplate`. This is explicit, testable, and gives full control over the SQL.

```java
// Example: Q3 guard
public boolean hasAuthorization(UUID userId, UUID nodeId, AuthLevel required) {
    String sql = """
        WITH RECURSIVE ancestors AS (
          SELECT id, parent_id FROM nodes WHERE id = :nodeId
          UNION ALL
          SELECT n.id, n.parent_id FROM nodes n
          JOIN ancestors a ON n.id = a.parent_id
        )
        SELECT EXISTS (
          SELECT 1 FROM ancestors a
          JOIN node_authorizations na ON na.node_id = a.id
          WHERE na.user_id = :userId
          AND   na.authorization >= CAST(:required AS auth_level)
        )
        """;
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql,
        Map.of("userId", userId, "nodeId", nodeId, "required", required.name()),
        Boolean.class));
}
```

**Transaction boundaries**: `@Transactional` on service methods, not repositories. Controllers are never transactional.

### 2. SSE — Spring MVC SseEmitter

WebFlux is not used. The app uses Spring MVC (servlet stack). SSE is implemented with `SseEmitter` — adequate for a small user base.

```java
// SseEmitterRegistry: one registry bean, application-scoped
private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters
    = new ConcurrentHashMap<>();

public SseEmitter register(UUID userId) {
    SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);  // no timeout
    emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
    emitter.onCompletion(() -> remove(userId, emitter));
    emitter.onTimeout(() -> remove(userId, emitter));
    emitter.onError(e -> remove(userId, emitter));
    return emitter;
}
```

`SseDispatcher` is called by services after every state mutation. It determines which userIds should receive the event (per SR-062), then iterates the registry and sends. Sends are `synchronized (emitter)` to prevent concurrent writes to the same emitter from different threads. Dead emitters (`IOException` on send) are removed immediately and never retried — the client `EventSource` reconnects automatically.

**No reactive dependencies added.** `spring-boot-starter-web` only.

### 3. Purge jobs — @Scheduled + chunked transactions

Two job classes, each driven by a daily `@Scheduled(cron = "0 59 23 * * *")` check.

**Startup resume**: `PurgeJobCoordinator` implements `ApplicationRunner`. On startup it reads both `purge_jobs` rows. If either has `status = 'active'`, it delegates to the corresponding job to resume. The job reads the stored `cutoff_date` — no recomputation.

**Activity purge** (chunked):
```
loop:
  DELETE FROM time_entries WHERE started_at < :cutoff LIMIT 1000  → deletedEntries
  DELETE FROM requests WHERE created_at < :cutoff LIMIT 1000      → deletedRequests
  UPDATE purge_jobs SET deleted_counts = ..., last_updated_at = NOW() WHERE job_type = 'activity'
  COMMIT
  if deletedEntries + deletedRequests == 0: break
SET status = 'idle', completed_at = NOW()
COMMIT
```

Each iteration is its own `@Transactional(propagation = REQUIRES_NEW)` call so the outer method can loop without holding a transaction open across the entire purge.

**Node deletion** (iterative bottom-up):
```
loop:
  find deactivated nodes WHERE deactivated_at < :cutoff
    AND id NOT IN (SELECT parent_id FROM nodes WHERE parent_id IS NOT NULL)  -- leaf only
    AND NOT EXISTS (SELECT 1 FROM time_entries WHERE node_id = id)
    AND NOT EXISTS (SELECT 1 FROM requests WHERE node_id = id)
  LIMIT 100
  if none found: break
  DELETE FROM nodes WHERE id IN (...)  -- cascades to node_authorizations
  UPDATE purge_jobs SET deleted_counts = ..., last_updated_at = NOW()
  COMMIT
SET status = 'idle', completed_at = NOW()
COMMIT
```

The `NOT EXISTS` checks are on the node itself, not the full subtree. This is correct because deletion is bottom-up: a node only becomes a leaf once all its children have been deleted in earlier iterations — by that point the subtree is already gone, so a subtree scan would be redundant.

Each iteration is also `REQUIRES_NEW`. The outer coordinator loop is not transactional.

### 4. OAuth2 flows — login vs. provider linking

Spring Security handles the OAuth2 callback at `/login/oauth2/code/{provider}`.

`TrawhileOidcUserService` (extends `DefaultOAuth2UserService`) is called for every OAuth2 callback. It implements the branching logic:

```
if HttpSession has attribute "LINKING_PROVIDER" = true:
    → link mode: insert user_oauth_providers for current authenticated user
    → clear session attribute
    → redirect to /account
else:
    → check user_oauth_providers for provider+subject
    → if found: create session (cascade guarantees user_profile exists), redirect to /
    → if not found: check pending_invitations by email
        → if found: store session data, redirect to /gdpr-notice (SR-008)
        → if not found: redirect /login?error=not_invited
```

The session attribute `LINKING_PROVIDER` is set before redirecting to `/oauth2/authorization/{provider}` from the account page. Spring's OAuth2 filter picks it up on the callback.

**Bootstrap detection**: `OAuth2UserService` checks for the SR-001 condition after creating the user row. If no root `admin` exists and `BOOTSTRAP_ADMIN_EMAIL` matches, inserts `node_authorizations` for root within the same transaction.

### 5. Authorization checks — service layer

No Spring Security method security (`@PreAuthorize`). Authorization is checked explicitly at the top of each service method using `AuthorizationService`:

```java
// In NodeService:
public NodeResponse deactivateNode(UUID nodeId, UUID requestingUserId) {
    authorizationService.requireAdmin(requestingUserId, nodeId);  // throws 403 if not
    // ... business logic
}
```

`requireAdmin` calls Q3 (recursive ancestor CTE). Throwing a typed exception (e.g. `AccessDeniedException`) which a global `@ControllerAdvice` maps to HTTP 403.

This approach is explicit, easy to test, and avoids annotation magic hiding authorization logic from code review.

### 6. Error handling

Single `@ControllerAdvice` (`GlobalExceptionHandler`) maps exceptions to `Problem` responses:

| Exception | HTTP |
|---|---|
| `AccessDeniedException` | 403 |
| `EntityNotFoundException` | 404 |
| `BusinessRuleViolationException` | 409 |
| `ValidationException` | 422 |
| `AuthenticationException` | 401 |

All exceptions carry a `code` string (e.g. `LAST_ADMIN`, `FROZEN_ENTRY`, `NODE_HAS_CHILDREN`) matching the `Problem.code` field in the OpenAPI spec.

### 7. Spring Security configuration

```
- OAuth2 login: enabled, custom OAuth2UserService + success handler
- CSRF: enabled, SameSite=Strict cookie
- Session: always (HttpSessionCreationPolicy.ALWAYS)
- No session timeout (server.servlet.session.timeout = 0 in application.yml)
- Frame options: DENY
- Content security policy: set in SecurityConfig
- HSTS: set in SecurityConfig
- Rate limiting: bucket4j filter applied before security chain
```

CSRF token is delivered via a cookie (`X-XSRF-TOKEN` header pattern) so the Angular SPA can read and send it without server-side rendering.

### 8. Database migrations — Flyway

All migrations in `src/main/resources/db/migration/`, named `V{n}__{description}.sql`.

| Migration | Content |
|---|---|
| V1__create_schema.sql | Full schema + seeds: root node, two purge_jobs rows (activity, node) |

No settings table exists in the database; all system configuration is read from `application.yml` via `TrawhileConfig` (`@ConfigurationProperties("trawhile")`).

---

## Frontend — Angular

### Technology versions

| Dependency | Version |
|---|---|
| Angular | 21.x |
| PrimeNG | 21.x |
| Tailwind CSS | 4.x |
| TypeScript | 5.x |
| ngx-translate | 16.x |

### Project structure

```
src/app/
  core/
    auth/
      auth.guard.ts            — redirects to /login if no session
      auth.service.ts          — current user state, logout
    http/
      csrf.interceptor.ts      — reads XSRF-TOKEN cookie, sets X-XSRF-TOKEN header
      error.interceptor.ts     — maps HTTP errors to typed errors
    sse/
      sse.service.ts           — EventSource wrapper; dispatches typed events
    api/                       — one service per controller
      settings.service.ts
      node.service.ts
      tracking.service.ts
      time-entry.service.ts
      report.service.ts
      account.service.ts
      ...
  features/
    tracking/                  — Epic 3: tracking widget, quick-access, time entry list
    nodes/                     — Epic 2: tree navigation, node admin
    reports/                   — Epic 4
    requests/                  — Epic 5: per-node request button + history
    account/                   — Epic 6
    admin/                     — Epics 1, 7, 8: user management, settings, security log
  shared/
    components/
      node-picker/             — node picker widget (reused in tracking, reports, requests)
      node-path/               — breadcrumb display
    pipes/
      duration.pipe.ts         — seconds → HH:mm:ss
      company-tz.pipe.ts       — UTC date-time → company timezone
```

### SSE service

```typescript
// sse.service.ts
// Maintains one EventSource. On 401 or network error, backs off and retries.
// Dispatches typed events to RxJS Subjects consumed by feature services.

readonly tracking$ = new Subject<TrackingStatus>();
readonly nodeChange$ = new Subject<NodeChangeEvent>();
readonly authorization$ = new Subject<AuthorizationEvent>();
readonly request$ = new Subject<RequestEvent>();
readonly quickAccess$ = new Subject<QuickAccessEntry>();
readonly mcpTokenRevoked$ = new Subject<{ tokenId: string }>();
```

Feature components subscribe to the relevant Subject. No polling anywhere.

**Stateless reconnect:** The browser `EventSource` reconnects automatically on dropped connections. On each reconnect (`EventSource.onopen`), the SSE service triggers a full re-fetch of current state (tracking status, node tree) via REST before resuming live event processing. No server-side event buffer or `Last-Event-ID` replay. This is consistent with the server-as-source-of-truth principle.

### State management

No NgRx or other state library. Angular signals + services with `signal()` / `computed()` for reactive state. Each feature service holds its own state; the SSE service pushes updates into it. This is sufficient for a small app with a clear server-as-source-of-truth model.

### Authentication flow

1. `AuthGuard` checks a `/api/v1/account` call on app init. If 401 → navigate to `/login`.
2. `/login` page shows a sign-in button for each configured OIDC provider (Google, Apple, Microsoft Entra ID, Keycloak) pointing to `/oauth2/authorization/{registrationId}`. Only providers with a configured client-id are rendered.
3. Spring redirects back after OAuth2; session cookie is set; app navigates to `/`.
4. `csrf.interceptor.ts` reads the `XSRF-TOKEN` cookie on every mutating request.

### Build and serving

- `ng build` outputs to `src/main/resources/static/` (configured in `angular.json`).
- Maven frontend plugin runs `ng build` as part of `mvn package`.
- Spring Boot serves static files from classpath `/static/`. All non-API paths return `index.html` (Angular router handles routing client-side).

---

## Docker Compose layout

```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: trawhile
      POSTGRES_USER: tt
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data

  app:
    image: trawhile:latest
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/trawhile
      SPRING_DATASOURCE_USERNAME: tt
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET}
      BOOTSTRAP_ADMIN_EMAIL: ${BOOTSTRAP_ADMIN_EMAIL}
    volumes:
      - ./config/application.yml:/app/config/application.yml  # system config (SR-088)
    depends_on:
      - db

  caddy:
    image: caddy:2
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data
    depends_on:
      - app

volumes:
  pgdata:
  caddy_data:
```

Caddyfile:
```
{$DOMAIN} {
  reverse_proxy app:8080
}
```

Caddy handles TLS termination and automatic Let's Encrypt certificate renewal.

---

## CI/CD — GitHub Actions

```
on: [push, pull_request]

jobs:
  build:
    steps:
      - mvn verify
          # includes:
          # - compile + test
          # - SpotBugs + Find Security Bugs (fail on HIGH/CRITICAL)
          # - OWASP Dependency Check (fail on HIGH/CRITICAL)
          # - CycloneDX SBOM generation (backend)
      - npm audit --audit-level=high (Angular)
      - ng build (production)
      - docker build

  deploy:       # main branch only
    needs: build
    steps:
      - docker push registry
      - ssh to VPS: docker compose pull && docker compose up -d
```

`SECURITY.md` and `/.well-known/security.txt` are static files served by Spring Boot.
