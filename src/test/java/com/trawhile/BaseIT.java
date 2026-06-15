package com.trawhile;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base class for every backend integration test (`IT` test type in
 * {@code spec/test-plan.md}). Provisions one PostgreSQL container per JVM
 * via Testcontainers, binds it to Spring's {@code spring.datasource.*}
 * properties via {@link ServiceConnection}, and resets data state between
 * tests while preserving the seeded rows from {@code V1__create_schema.sql}.
 *
 * <p>This class carries no {@code @Tag("TE-...")} because it ships no
 * behavioural test of its own — it is shared test infrastructure for the
 * IT suite (architecture §8.3).</p>
 *
 * <p><strong>Test-class isolation: one JVM per class.</strong> The Surefire
 * plugin runs every test class in its own JVM ({@code forkCount=1
 * reuseForks=false} in {@code pom.xml}). Nothing is shared across class
 * boundaries by construction: no Spring TestContext cache, no JVM-singleton
 * library state (Logback root logger, Brave {@code current()}, SLF4J MDC,
 * {@code SecurityContextHolder}), no Testcontainers container reference.
 * Inside a single class, normal Spring Boot test conventions apply — the
 * Spring context and the container are shared across test methods of that
 * one class, and {@link #resetDataState()} keeps application data clean
 * between methods.</p>
 *
 * <p><strong>Why fork-per-class is the invariant:</strong> a previous,
 * shared-JVM setup repeatedly produced cross-class-pollution failures —
 * Spring's cached DataSource bound to a stale container port, Brave's
 * tracing config silently lost after an unrelated test class ran, etc.
 * Each one cost real diagnostic cycles. Forking eliminates the entire
 * class of bug for the price of ~10–15 s of per-class JVM + Spring + container
 * startup. The cost lives at the suite level only; single-class runs
 * ({@code mvn test -Dtest=FooIT}) are unaffected.</p>
 *
 * <p><strong>Container lifecycle — singleton-within-JVM, NOT
 * JUnit-managed:</strong> the container is started exactly once per JVM in
 * a static initializer and is NOT annotated {@code @Container}. The
 * {@code @Testcontainers} JUnit extension is also deliberately NOT applied
 * here. This is the pattern the Testcontainers docs call "singleton
 * containers". With fork-per-class, every class gets its own fresh
 * container; within a class, the same container serves every test method.
 * Cleanup on JVM exit is handled by Testcontainers' Ryuk reaper — no
 * shutdown hook is required.</p>
 *
 * <p><strong>Why not {@code .withReuse(true)}:</strong> that mode caches
 * the container reference in {@code ~/.testcontainers.properties} so it
 * survives JVM exit. It fails silently with "Connection refused" when the
 * underlying Docker container is reaped externally (system reboot,
 * {@code docker prune}, OOM kill). Trading the per-JVM startup cost for
 * predictable behaviour is a clear win for both CI and the agentic
 * pipeline.</p>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.security.oauth2.client.registration.google.client-id=base-it-google-client",
        "spring.security.oauth2.client.registration.google.client-secret=base-it-google-secret"
    }
)
public abstract class BaseIT {

    /**
     * Singleton PostgreSQL container shared across the JVM. The matching major
     * version is {@code postgres:17}; bumping it requires reviewing
     * {@code spec/schema.sql} for syntax compatibility.
     *
     * <p>{@link ServiceConnection} wires the container's connection coordinates
     * into Spring's {@code spring.datasource.*} properties via Spring Boot's
     * Testcontainers integration. No {@code @DynamicPropertySource} is needed.</p>
     */
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("trawhile")
        .withUsername("trawhile")
        .withPassword("trawhile-test");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * Application tables that hold data written by tests, in child-before-parent
     * FK order. {@code purge_jobs} is intentionally absent because it holds
     * only seeded singleton rows and no test writes to it; {@code nodes} is
     * handled separately so the root node seed survives.
     */
    private static final List<String> APP_DATA_TABLES_CHILD_FIRST = List.of(
        "webhook_deliveries",
        "webhook_subscriptions",
        "api_keys",
        "quick_access",
        "time_records",
        "node_authorizations",
        "pending_invitations",
        "user_oauth_providers",
        "user_profile",
        "users"
    );

    /**
     * Resets per-test data state to the post-migration baseline:
     * <ul>
     *   <li>Deletes every row from the application data tables in FK-respecting order.</li>
     *   <li>Deletes every {@code nodes} row except the seeded root, preserving
     *       the singleton {@code parent_id IS NULL} entry from V1.</li>
     *   <li>Leaves {@code purge_jobs} untouched (its rows are seed singletons).</li>
     * </ul>
     * The Flyway-applied schema and the V1 seed rows survive intact.
     */
    @AfterEach
    void resetDataState() {
        for (String table : APP_DATA_TABLES_CHILD_FIRST) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
        jdbcTemplate.execute("DELETE FROM nodes WHERE parent_id IS NOT NULL");
    }
}
