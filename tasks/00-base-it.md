# Task 00 — Test infrastructure

Must be completed and merged before any other task begins. All other tasks depend on the classes produced here.

## Scope

Create the shared test base class, security test helpers, and common test fixture factories. No production code changes.

## Guardrails

- **Do not touch `src/main/java/`** — never create, edit, delete, or rename any production source file. This task produces test infrastructure only.
- **No Flyway migrations** — never create or modify files under `src/main/resources/db/migration/`.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or any command that modifies git state or remote. Read-only commands (`git status`, `git log`, `git diff`, `git show`) are permitted.

## Read first

1. `docs/architecture.md` — package layout, Spring Security configuration (§7)
2. `src/main/java/com/trawhile/config/SecurityConfig.java` — understand the security chain
3. `src/main/java/com/trawhile/domain/User.java`, `UserProfile.java`, `UserOauthProvider.java` — principal shape
4. `src/main/resources/db/migration/V1__create_schema.sql` — Flyway migration the container will run

## Create

```
src/test/java/com/trawhile/
  BaseIT.java              — abstract base; starts Postgres container, configures datasource
  TestFixtures.java        — static factory methods for common DB state (users, nodes, etc.)
  TestSecurityHelper.java  — builds authenticated MockMvc request post-processors
```

## BaseIT requirements

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
public abstract class BaseIT {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16").withReuse(true);

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        // Disable StartupValidator so tests can control provider config
        r.add("BOOTSTRAP_ADMIN_EMAIL", () -> "");
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        // Truncate all tables in dependency order; reset sequences
        jdbc.execute("TRUNCATE mcp_tokens, quick_access, time_records, requests, " +
            "security_events, node_authorizations, user_oauth_providers, user_profile, " +
            "pending_invitations, users, nodes RESTART IDENTITY CASCADE");
        // Re-insert the root node (seeded by V1 migration, removed by truncate)
        jdbc.execute("INSERT INTO nodes (id, name, is_active, sort_order) " +
            "VALUES ('00000000-0000-0000-0000-000000000001', 'root', true, 0)");
        // Re-insert purge_jobs rows
        jdbc.execute("INSERT INTO purge_jobs (job_type, status) VALUES ('activity','idle'),('node','idle')");
    }
}
```

## TestSecurityHelper requirements

The app uses Spring Security OIDC login. Tests must authenticate as a specific `users` row. Provide:

```java
public class TestSecurityHelper {
    // Returns a RequestPostProcessor that authenticates MockMvc requests
    // as the given userId, using OidcLogin with name = userId.toString()
    public static RequestPostProcessor authenticatedAs(UUID userId) { ... }

    // Convenience: returns a RequestPostProcessor for a user with admin on root
    public static RequestPostProcessor adminUser(UUID userId) { ... }
}
```

Use `SecurityMockMvcRequestPostProcessors.oidcLogin()` (from `spring-security-test`). The principal's `getName()` must return `userId.toString()` — this is how controllers resolve the current user.

## TestFixtures requirements

Static helper methods that insert rows directly via `JdbcTemplate` and return the inserted ID. Examples:

```java
public class TestFixtures {
    public static UUID insertUser(JdbcTemplate jdbc) { ... }
    public static UUID insertUserWithProfile(JdbcTemplate jdbc, String name) { ... }
    public static UUID insertNode(JdbcTemplate jdbc, UUID parentId, String name) { ... }
    public static void grantAuth(JdbcTemplate jdbc, UUID userId, UUID nodeId, String level) { ... }
    public static UUID insertPendingInvitation(JdbcTemplate jdbc, String email, UUID userId) { ... }
    public static UUID insertTimeRecord(JdbcTemplate jdbc, UUID userId, UUID nodeId,
                                       OffsetDateTime startedAt, OffsetDateTime endedAt) { ... }
}
```

All methods insert via `jdbc.update(...)` — never via the service layer.

## Acceptance criteria

- `mvn test` with an empty test class extending `BaseIT` passes
- Container starts once per JVM run (reuse = true) — verify by checking log output shows single container start
- Each test gets a clean slate via `@BeforeEach` truncation
- `TestFixtures.insertUser` + `TestFixtures.grantAuth` + `TestSecurityHelper.authenticatedAs` work together in a minimal smoke test

## Watch out for

- The root node UUID (`00000000-0000-0000-0000-000000000001`) is referenced by auth queries — must be re-inserted after each truncate
- `purge_jobs` rows are required by `PurgeJobCoordinator` on startup — if the Spring context starts mid-test they must exist
- `StartupValidator` will fail if no OAuth providers are configured — suppress via `BOOTSTRAP_ADMIN_EMAIL` or by providing a mock `ClientRegistrationRepository` bean in a test-only `@TestConfiguration`
- Flyway runs once when the container first starts; `TRUNCATE ... RESTART IDENTITY CASCADE` resets data without re-running migrations
- Testcontainers `withReuse(true)` requires `~/.testcontainers.properties` with `testcontainers.reuse.enable=true` — document this in the PR description
