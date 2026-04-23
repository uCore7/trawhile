# Task impl/13 — Command rollout: authorization-shaped mutations

## Prerequisites

- `tasks/00-base-it.md` merged
- `tasks/impl/11-persistence-foundation.md` merged
- `tasks/impl/12-persistence-sensitive-reads.md` merged
- `tasks/tests/13-persistence-commands.md` merged (tests exist and are failing)

## Guardrails

- **Do not touch `src/test/`** — never create, edit, delete, or rename any file under `src/test/`. If a test appears wrong, report it and stop.
- **No git write operations** — do not run `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, or `git branch -D`.
- **No Flyway migrations** — never create or modify files under `src/main/resources/db/migration/`.
- **No frontend files** — do not create or modify files under `src/main/frontend/`.

## Scope

Introduce the first mutation-oriented jOOQ command layer and migrate the most important node-scoped mutations to it. The point is to encode authorization in the mutation SQL itself rather than in a separate Java-side pre-check plus generic update/delete.

## Read first (in order)

1. `docs/architecture.md` — repository command direction
2. `docs/decisions.md` — persistence-layer decision
3. `src/main/java/com/trawhile/service/NodeService.java`
4. `src/main/java/com/trawhile/service/RequestService.java`
5. `src/main/java/com/trawhile/service/TrackingService.java`
6. The failing tests:
   - `src/test/java/com/trawhile/repository/command/NodeCommandsIT.java`
   - `src/test/java/com/trawhile/repository/command/RequestCommandsIT.java`
   - `src/test/java/com/trawhile/repository/command/QuickAccessCommandsIT.java`

## Create / modify

| Area | What to do |
|---|---|
| `src/main/java/com/trawhile/repository/command/NodeCommands*.java` | Introduce verb-shaped node mutation commands using authorized target CTEs and `RETURNING` where appropriate. |
| `src/main/java/com/trawhile/repository/command/RequestCommands*.java` | Introduce request-close command(s) scoped by admin authorization. |
| `src/main/java/com/trawhile/repository/command/QuickAccessCommands*.java` | Introduce owner-scoped quick-access mutations that do not rely on generic repository CRUD. |
| `src/main/java/com/trawhile/service/NodeService.java` | Migrate appropriate mutation paths to the new command layer. Preserve invariants such as last-admin protection and subtree checks. |
| `src/main/java/com/trawhile/service/RequestService.java` | Migrate request-closing write path to the new command layer. |
| `src/main/java/com/trawhile/service/TrackingService.java` | Migrate quick-access write paths to the new command layer. |

## Design requirements

- For node-scoped mutations, prefer:
  - `WITH authorized_target AS (...)`
  - `UPDATE ... FROM`
  - `DELETE ... USING`
  - `INSERT ... SELECT`
  - `RETURNING`
- Keep business invariants explicit. Encoding authorization in SQL does **not** remove the need for invariant checks such as:
  - cannot revoke the last admin
  - cannot move a node into its own subtree
  - cannot deactivate a node with active descendants
- Make the command APIs verb-oriented, not CRUD-oriented.

## Acceptance criteria

`./scripts/mvn-local.sh -Dtest=NodeCommandsIT,RequestCommandsIT,QuickAccessCommandsIT,NodeIT,RequestIT,QuickAccessIT test` passes.

## Watch out for

- Be deliberate about “not found” versus “unauthorized” semantics when an authorized mutation affects zero rows. Preserve the externally intended behavior.
- Do not regress SSE dispatch; services still need to emit events after successful mutations.
- This task is about the command layer. Do not reopen the read-layer design by reintroducing generic repository access in the migrated paths.
