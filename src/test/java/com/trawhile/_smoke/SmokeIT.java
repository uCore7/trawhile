package com.trawhile._smoke;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.trawhile.BaseIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Validates the {@link BaseIT} infrastructure end to end:
 * a Spring context starts, the Testcontainers PostgreSQL is reachable, and
 * Flyway-applied seed data is present and intact.
 *
 * <p>No {@code @Tag} — this is shared-infrastructure validation, not a planned TE.</p>
 */
class SmokeIT extends BaseIT {

    @Autowired
    private DataSource dataSource;

    /**
     * Confirms the application context wired the container's DataSource.
     */
    @Test
    void springContextProvidesTheContainerBackedDataSource() {
        assertNotNull(dataSource, "DataSource must be autowired by the Spring context");
    }

    /**
     * Confirms Flyway ran and the seed root node from {@code V1__create_schema.sql} is present.
     */
    @Test
    void flywaySeedsTheRootNode() {
        Integer rootCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM nodes WHERE parent_id IS NULL",
            Integer.class
        );
        assertEquals(1, rootCount, "Exactly one root node (parent_id IS NULL) must be seeded by V1");
    }

    /**
     * Confirms all four purge job singletons from V1 are seeded.
     */
    @Test
    void flywaySeedsThePurgeJobSingletons() {
        Integer purgeJobCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM purge_jobs",
            Integer.class
        );
        assertEquals(4, purgeJobCount,
            "All four purge_jobs singletons (time_record_retention, node_retention, "
            + "invitation_expiry, open_record_auto_close) must be seeded by V1");
    }
}
