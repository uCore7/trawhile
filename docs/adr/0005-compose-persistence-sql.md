# 0005. Compose persistence SQL

## Status

- Accepted, 2026-05-15

## Context

This decision answers: how are SQL statements for persistence access composed and checked in application code?

The application has a canonical PostgreSQL schema in `spec/schema.sql`. Its most important persistence paths depend on authorization functions, recursive queries, generated schema knowledge, precise parameter binding, and reviewable SQL shape.

JPA/Hibernate would obscure too much of this model through entity graphs, lazy loading, generated SQL, and persistence-context behaviour. Those trade-offs are especially risky for recursive authorization checks and external-actor data access.

Plain JDBC and Spring Data JDBC keep SQL concepts visible, but broad hand-written SQL strings and table-shaped repositories have produced too many runtime failures around column names, casts, joins, and parameter binding. They also leave some database access without generated schema checks.

jOOQ preserves a SQL-first programming model while providing generated schema objects, typed expression composition, and stronger feedback during compilation.

## Decision

Use a SQL-first persistence architecture based on jOOQ.

Do not use JPA/Hibernate.

Use generated schema objects and the jOOQ DSL for new persistence components, including authorization primitives, external reads, external commands, and internal system persistence.

Use plain SQL strings only when jOOQ cannot reasonably express the required PostgreSQL construct or when the generated schema cannot model it, such as selected runtime PostgreSQL functions or recursive/function-table helpers. Keep such exceptions small, named, and PostgreSQL-backed tested.

Keep `spec/schema.sql` as the canonical schema. Generated jOOQ schema inputs remain derived artifacts.

Existing Spring Data JDBC repositories and plain-SQL JDBC components are transitional implementation only and may remain until migration work is completed.

## Consequences

Persistence code converges on one SQL-first abstraction without JPA persistence-context semantics.

SQL composition benefits from generated schema objects and stronger compile-time feedback, reducing runtime SQL errors from string SQL and bind mistakes.

The project accepts jOOQ code generation as a normal build concern.

The project gives up JPA conveniences such as automatic dirty checking, entity relationships, and persistence-context caching.

Migration can proceed incrementally. Architecture documentation may describe jOOQ as the target even while some implementation classes are still transitional.
