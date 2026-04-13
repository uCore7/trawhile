package com.trawhile.domain;

/**
 * Authorization level. Ordering: VIEW < TRACK < ADMIN (matches PostgreSQL auth_level ENUM).
 * Converters in JdbcConfig map between this enum and the lowercase PG ENUM values.
 */
public enum AuthLevel {
    VIEW, TRACK, ADMIN
}
