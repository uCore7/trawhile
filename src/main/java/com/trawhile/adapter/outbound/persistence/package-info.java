/**
 * Outbound persistence adapter. jOOQ-backed implementations of the persistence
 * port interfaces in {@link com.trawhile.port.outbound}. Generated jOOQ types
 * live in {@link com.trawhile.adapter.outbound.persistence.jooq} (build-time
 * artifact from {@code src/main/resources/db/codegen/jooq-schema.sql}).
 *
 * <p>Authorization-sensitive ports encode authorization inside the SQL statement
 * (architecture §8.2) to avoid check-then-mutate gaps.</p>
 */
package com.trawhile.adapter.outbound.persistence;
