# Test task tests/13 — Command rollout: authorization-shaped mutations

## Role

You are a **test writer**. Derive the tests from the specs, the persistence design docs, and this task brief. Do not read `src/main/java/`.

## Prerequisites

- `tasks/00-base-it.md` merged
- `tasks/impl/11-persistence-foundation.md` merged
- `tasks/impl/12-persistence-sensitive-reads.md` merged

## Guardrails

- **Do not touch `src/main/`** — never create, edit, delete, or rename any file under `src/main/`.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, or `git branch -D`.
- **Mutation contract tests only** — focus on SQL-level authorization of updates/deletes/inserts via authorized target subqueries.

## Read (in order)

1. `docs/requirements-sr.md` — SR-F015.F01, SR-F016.F01, SR-F017.F01, SR-F018.F01, SR-F019.F01, SR-F020.F01, SR-F021.F01, SR-F022.F01, SR-F039.F01, SR-F042.F01
2. `docs/schema.sql` — nodes, node authorizations, requests, quick access, time records
3. `docs/architecture.md` — repository command direction
4. `docs/decisions.md` — persistence-layer decision
5. `src/test/java/com/trawhile/BaseIT.java`
6. `src/test/java/com/trawhile/TestFixtures.java`
7. `src/test/java/com/trawhile/TestSecurityHelper.java`

## Create

```text
src/test/java/com/trawhile/repository/command/
  NodeCommandsIT.java
  RequestCommandsIT.java
  QuickAccessCommandsIT.java
```

## Rules for every test method

- Use PostgreSQL/Testcontainers-backed integration tests.
- Annotate each test method with `@Tag("persistence-commands")`.
- Assert database post-state after every mutation.
- Assert unauthorized mutation attempts leave the database unchanged.

## Expected command contracts

Assume the implementation task will introduce command classes shaped around verbs rather than CRUD, for example:

- `NodeCommands.createChild(...)`
- `NodeCommands.updateNodeMetadata(...)`
- `NodeCommands.deactivateNode(...)`
- `NodeCommands.reactivateNode(...)`
- `NodeCommands.moveNode(...)`
- `NodeCommands.grantAuthorization(...)`
- `NodeCommands.revokeAuthorization(...)`
- `RequestCommands.closeRequest(...)`
- `QuickAccessCommands.addEntry(...)`
- `QuickAccessCommands.removeEntry(...)`

## Key assertions

### `NodeCommandsIT`

- Admin-authorized mutations succeed and persist the expected post-state.
- Unauthorized callers cannot mutate node metadata or authorization state.
- The command layer does not need a broad pre-read of the target to protect the mutation.
- Authorized `move` / `deactivate` / `reactivate` commands update exactly the intended node rows.

### `RequestCommandsIT`

- A node admin can close a request within scope.
- A caller outside admin scope cannot close the request.
- Unauthorized attempts leave the request row unchanged.

### `QuickAccessCommandsIT`

- A user can add/remove entries on their own profile only.
- Commands do not mutate another user’s quick-access rows.
- Commands that reference an invisible or unauthorized node do not create or delete rows.

## Acceptance criteria

`./scripts/mvn-local.sh -Dtest=NodeCommandsIT,RequestCommandsIT,QuickAccessCommandsIT test` passes after implementation.

## Watch out for

- Decide assertions around “0 rows changed” carefully. The command contract may intentionally collapse “not found” and “unauthorized” at the SQL layer; test the externally intended semantics, not guessed internals.
- For node authorization commands, include a “last admin” scenario so invariant-preserving logic is not lost in the refactor.
