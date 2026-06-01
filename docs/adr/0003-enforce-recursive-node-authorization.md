# 0003. Enforce recursive node authorization

## Status

- Accepted, 2026-05-15

## Context

This decision answers: how is recursive node authorization enforced consistently for external-actor reads and mutations?

Authorization is based on an arbitrarily deep node tree. Access granted at an ancestor node can affect descendant visibility and permissions.

External-actor persistence paths need a unified way to enforce this tree authorization in both reads and mutations. Duplicating recursive predicates in each query would create drift and make authorization review difficult.

The authorization mechanism must support both query-time visibility decisions and mutation guards. It should avoid broad data reads followed by Java-side filtering, because those reads increase non-disclosure risk and make it harder to verify that every external-actor access path enforces the same rule.

## Alternatives Considered

One alternative is to perform recursive authorization in application services. Services would load the relevant node, traverse ancestors or descendants through persistence calls, and then decide whether the operation is allowed. This keeps authorization logic in Java and may be easy to step through in a debugger. However, it separates the authorization decision from the SQL statement that reads or mutates the data. That makes structural enforcement weaker: every use case must remember to call the right check before every relevant query or command, and queries can still accidentally over-read data that is later filtered in Java.

Another alternative is to compose the recursive authorization SQL separately in each external read and command. This keeps the decision close to the data and allows the database to evaluate the tree relation efficiently, but it duplicates recursive predicates across many query shapes. Over time, small differences between those predicates would be hard to review and could produce inconsistent visibility or mutation rules.

A third alternative is to maintain a flattened effective-authorization table. Reads and commands could then join against precomputed rows instead of evaluating recursion at query time. This can be fast for reads, but it introduces invalidation and repair complexity whenever authorizations or tree structure change. The write path becomes more fragile because authorization correctness depends on keeping derived rows synchronized with the source tree and grants.

A fourth alternative is to centralize recursive authorization in shared PostgreSQL authorization functions and use those functions from external read and command SQL. This keeps the logic set-based and close to the relational data, gives the optimizer a chance to evaluate joins and predicates in one statement, and provides a single reviewable place for the tree authorization rule. The trade-off is that authorization correctness depends on database-specific functions, indexes, query plans, and PostgreSQL-backed tests.

## Decision

Evaluate recursive node authorization in PostgreSQL using shared authorization functions.

Use those functions as the single reusable database-side authorization mechanism for external-actor persistence access.

External read and command SQL enforces authorization by joining or left-joining against those functions. If a join shape is not suitable, the SQL must use a named wrapper or predicate derived from the same authorization functions.

## Consequences

PostgreSQL evaluates the recursive tree logic where the data lives, at the cost of database-specific functions, indexes, query plans, and PostgreSQL-backed tests.

Authorization behaviour can be reused by service checks, external read queries, and external command statements.

External persistence paths have one reviewable authorization mechanism instead of query-specific recursive logic or Java-side post-filtering.

Authorized reads and guarded mutations can often be executed in one database round trip, with the database applying indexes, joins, recursive evaluation, and mutation predicates together.

Correctness depends on PostgreSQL-backed tests that cover allowed access, denied access, inheritance from ancestors, and tree mutations.

Performance depends on suitable indexes and query plans for the authorization functions and their join patterns.

Changing the RDBMS would require reimplementing and retesting the authorization SQL functions and related SQL procedures for the new database dialect.
