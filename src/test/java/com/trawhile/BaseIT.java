package com.trawhile;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared base class for every backend integration test (`IT` test type in
 * {@code spec/test-plan.md}). Provisions one PostgreSQL container per test
 * suite via Testcontainers' reused-container pattern, binds it to Spring's
 * {@code spring.datasource.*} properties, and resets data state between tests
 * while preserving the seeded rows from {@code V1__create_schema.sql}.
 *
 * <p>This class carries no {@code @Tag("TE-...")} because it ships no
 * behavioural test of its own — it is shared test infrastructure for the
 * IT suite (architecture §8.3).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class BaseIT {

    /**
     * Single PostgreSQL container shared across the suite. The matching major
     * version is {@code postgres:17}; bumping it requires reviewing
     * {@code spec/schema.sql} for syntax compatibility.
     *
     * <p>{@code withReuse(true)} keeps the container alive across JVM
     * invocations so successive {@code mvn test} runs reuse it; Testcontainers
     * still owns the lifecycle within a single JVM.</p>
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("trawhile")
        .withUsername("trawhile")
        .withPassword("trawhile-test")
        .withReuse(true);

    /**
     * Binds the container's connection coordinates to the Spring datasource
     * properties before the application context is built. Flyway then runs
     * {@code V1__create_schema.sql} against this container exactly once per
     * suite (the container is reused) on the first context startup.
     */
    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
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
