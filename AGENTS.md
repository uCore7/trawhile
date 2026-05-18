# Agent instructions — trawhile

This file is the tool-agnostic entry point for agents. `docs/process.md` owns the full development process, artifact dependency chain, and phase rules.

## Start Here

- Start with the assigned task file under `tasks/`.
- The task file defines the role, scope, prerequisites, and concrete documents to read.
- Read the assigned task file first, then `docs/process.md`, then the upstream canonical documents named by the task.
- If there is no assigned task file, read only the canonical documents that are upstream of the work being requested. Do not use downstream artifacts as sources for upstream work.
- Keep changes scoped to the assigned task.

## Upstream Discipline

- Requirements-engineering work uses problem-space sources only, such as `docs/glossary.md` and `docs/requirements-ur.md`.
- Architecture work may use requirements and glossary sources; ADR work also uses `docs/architecture.md` and `docs/adr/`.
- System-requirements work may use requirements, architecture, and ADRs.
- Technical-specification work may use requirements, architecture, ADRs, and system requirements before writing spec artifacts.
- Test, implementation, and cleanup work follow the read list in the assigned task file.
- If a task, requirement, architecture document, ADR, or implementation detail conflicts, stop and report the conflict.

## Execution Guardrails

- Use `./scripts/mvn-local.sh ...`, not bare `mvn` or `./mvnw`.
- For native app startup, start PostgreSQL first with `make development-db`, then run `./scripts/mvn-local.sh spring-boot:run`.
- If the sandbox blocks Docker, local DB sockets, Redis, or required test containers, request escalation for the exact command instead of treating the failure as an application bug.
- Do not run git write operations: `git commit`, `git push`, `git pull`, `git fetch`, `git merge`, `git rebase`, `git reset`, `git stash`, `git branch -D`, or commands that modify git state or communicate with a remote.
- Test agents under `tasks/tests/` must not modify `src/main/`.
- Implementation and cleanup agents under `tasks/impl/` or `tasks/cleanup/` must not modify `src/test/`.
- Implementation and cleanup agents must not modify frontend files unless explicitly assigned a frontend task.
- Do not create or modify Flyway migrations under `src/main/resources/db/migration/`; schema changes start in chat mode from `spec/schema.sql`.

## Implementation Guardrails

- Put `@Transactional` on service methods only.
- External-actor service methods check authorization explicitly through `AuthorizationService`.
- Do not use `@PreAuthorize`.
- Do not add JPA.
- New persistence work follows the jOOQ-backed outbound persistence adapter structure from the architecture and ADRs.
