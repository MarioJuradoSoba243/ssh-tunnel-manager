# AGENTS.md

## Purpose

This repository uses OpenSpec as the source of truth for product behavior and architecture.

Agents must implement the approved specifications and design without silently changing them to fit an implementation.

The primary change is located at:

```text
openspec/changes/create-ssh-tunnel-manager/
```

Relevant artifacts include:

```text
proposal.md
specs/**/spec.md
design.md
tasks.md
```

## Source of truth

`proposal.md`, `specs/**/spec.md`, and `design.md` define the approved product behavior and architecture.

Code MUST conform to these artifacts.

If an implementation request contradicts them, stop the affected work and report the contradiction instead of changing the specification implicitly.

## OpenSpec artifact policy

Do not modify these files without explicit user approval:

```text
openspec/changes/**/proposal.md
openspec/changes/**/specs/**/spec.md
openspec/changes/**/design.md
```

`tasks.md` is operational and may be updated to:

- mark tasks complete;
- record concise implementation notes;
- reflect the actual completion state.

Do not change task scope merely to make implementation easier.

If implementation reveals a missing product or architecture decision, stop that portion of work and surface the decision that must be made.

## SSH engine gate

Apache MINA SSHD is a candidate, not an unconditional final dependency.

Do NOT implement the complete SSH subsystem until the mandatory SSH spike has passed.

The spike must verify at least:

- direct password authentication;
- one jump host with independent credentials;
- two jump hosts with independent credentials on all three SSH hosts;
- host-key/fingerprint validation for every SSH host;
- explicit approval of unknown host keys;
- Local Port Forwarding;
- destination host different from the final SSH host;
- keep-alive;
- connection-loss detection;
- clean cancellation and shutdown;
- connection timeout;
- unreachable destination handling.

If the spike fails materially, stop and report the failure.

Do not automatically switch to JSch, Plink, OpenSSH, or another engine without an approved design change.

## Architecture boundaries

The application is a modular monolith implemented as a single Maven project.

The intended dependency direction is:

```text
UI
 ↓
Application
 ↓
Domain
 ↑
Infrastructure
```

Rules:

- `domain` MUST NOT depend on JavaFX.
- `domain` MUST NOT depend on JDBC or SQLite.
- `domain` MUST NOT depend on Apache MINA SSHD or another SSH implementation.
- `domain` MUST NOT depend on `ui` or `infrastructure`.
- `application` MUST NOT depend on `ui`.
- Controllers MUST NOT contain domain rules, JDBC code, SSH implementation code, or transaction management.
- Infrastructure implements ports/interfaces owned by inner layers.
- ArchUnit tests protect these boundaries.

Do not weaken architecture tests merely to make a dependency compile.

## Simplicity

Implement the simplest solution that satisfies the specs and design.

Do not introduce speculative abstractions, plugin systems, factories, strategy hierarchies, generic frameworks, or extensibility mechanisms unless required by current specifications or the approved design.

Prefer clear code over clever code.

## Dependency policy

Do not introduce structural frameworks or major dependencies that are absent from `design.md` without explicit approval.

In particular, do not add by convenience:

- Spring or Spring Boot;
- Hibernate or JPA;
- Lombok;
- Reactor or RxJava;
- Guice or Dagger;
- Flyway;
- MaterialFX or another UI framework/theme layer.

Small support dependencies may be introduced only when necessary, with a clear reason and compatible architecture.

All dependency and Maven plugin versions must be pinned. Do not use `LATEST`, floating versions, or version ranges.

## Dependency injection

Use constructor injection.

The object graph is assembled explicitly from the application bootstrap.

Do not introduce a dependency injection container.

## JavaFX rules

JavaFX is presentation only.

- Do not perform SSH operations on the JavaFX Application Thread.
- Do not perform expensive SQLite work on the JavaFX Application Thread.
- Do not perform backups, imports, exports, or other blocking I/O on the JavaFX Application Thread.
- Scene graph mutations must happen on the JavaFX Application Thread.
- Controllers should remain thin.
- Business behavior belongs in ViewModels, Application use cases, Domain, or Infrastructure as appropriate.

## Runtime lifecycle

`TunnelRuntimeManager` is the single coordinator of live tunnel state.

Controllers, ViewModels, group services, and other components must not open SSH connections directly.

`start` and `stop` must remain idempotent.

Operations for the same tunnel must be serialized.

Different tunnels may operate concurrently.

Initial connections and reconnections share the configured concurrency limit.

Retry policy and backoff belong to runtime/application logic, not the SSH engine.

A reconnection rebuilds the complete SSH route.

Execution generations/tokens must prevent stale callbacks from mutating a newer runtime execution.

## Groups

Groups are bulk actions, not owners of tunnels.

Starting a group must not duplicate a tunnel that is already active.

Stopping a group stops its member tunnels even when they also belong to other groups.

Do not introduce reference counting or ownership semantics unless the specs/design are explicitly changed.

## SSH connections

In v1, each running tunnel owns an independent SSH route/session chain.

Do not share SSH sessions between tunnels.

The SSH engine must remain behind the `SshTunnelEngine` abstraction.

SSH implementation details must not leak into Domain or UI.

## Persistence

Use SQLite through JDBC and the approved Xerial driver.

Repository integration tests use real temporary SQLite databases, not mocked JDBC.

Use:

```sql
PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;
```

where specified by the design.

Use short-lived JDBC connections per operation.

Multi-record domain changes must be transactional.

Do not persist runtime tunnel state such as `OPEN`, `CONNECTING`, or `RECONNECTING` as restorable application state.

Use the custom versioned migration mechanism described in `design.md`.

Create a backup before schema migration.

## Credentials

Passwords are recoverable application data in v1 and may be stored in SQLite and full backups/exports as specified.

Do not introduce DPAPI, JNA, a master password, or another credential protection mechanism unless the design changes.

Passwords MUST NEVER be:

- written to logs;
- included in diagnostic bundles;
- printed with `System.out`/`System.err`;
- exposed in exception messages intentionally.

Avoid retaining unnecessary password copies in memory.

## Logging

Use SLF4J as the logging API and Log4j2 as the implementation.

Do not use `System.out.println` or `System.err.println` as application logging.

Use standard levels:

```text
TRACE
DEBUG
INFO
WARN
ERROR
```

Use IDs/context such as `tunnelId`, `hostId`, `groupId`, and `operationId` when useful.

Do not log high-frequency noise at INFO unless it has operational value.

Never log passwords at any level.

## Error handling

Do not silently swallow exceptions.

Avoid patterns such as:

```java
try {
    // ...
} catch (Exception ignored) {
}
```

Errors should be:

- propagated;
- translated to an application result;
- handled explicitly;
- and/or logged at the appropriate boundary.

A failure in a notification/event listener must not fail a live tunnel or prevent other listeners from receiving an event.

## Event handling

Update authoritative runtime state before publishing lifecycle events.

Event listeners must be isolated from one another.

Do not introduce an external message broker or heavyweight event framework.

## Testing

Tests are part of implementation, not a later cleanup phase.

Use:

- JUnit 5;
- AssertJ;
- Mockito where useful;
- ArchUnit;
- real SQLite for repository integration tests.

Prefer real domain objects and small fakes over excessive mocking.

Do not remove, disable, weaken, or rewrite an existing test only to make the build pass.

If a test contradicts an explicitly updated specification, surface that conflict and update it as part of the approved change.

## SSH integration tests

SSH tests must use controlled/reproducible infrastructure.

Do not depend on:

- company servers;
- corporate VPN;
- personal SSH hosts;
- private external systems.

Use the approved Docker-based integration topology where appropriate.

Changes to the SSH engine must run the relevant SSH integration tests.

## Code formatting and quality

Use Spotless with google-java-format.

Before completing work, run the repository's canonical formatting and verification commands.

Expected baseline:

```text
.\mvnw.cmd spotless:apply
.\mvnw.cmd verify
```

Use the actual configured Maven goals if the project later wraps these commands differently.

Do not introduce Checkstyle, PMD, SpotBugs, Sonar, or similar gates unless approved.

## Commits

Create small, coherent commits grouped by unit of work.

A commit should have one understandable purpose.

Avoid mixing unrelated refactors, formatting churn, features, and fixes.

Use clear commit messages such as:

```text
feat: add SQLite migration runner
test: cover migration rollback
fix: prevent stale tunnel callback state update
docs: document tunnel group workflow
```

## Comments and JavaDoc

Do not add JavaDoc to every class, getter, or trivial method.

Document:

- important public contracts;
- non-obvious lifecycle rules;
- concurrency assumptions;
- decisions that are difficult to infer from code.

Comments should explain why, not restate what the code already says.

## TODO policy

Do not leave generic TODOs behind when completing a task.

If work is intentionally deferred to another OpenSpec task, reference it explicitly, for example:

```java
// Deferred to task 4.3: host-key approval UI.
```

Do not use TODO comments as a substitute for completing the current task.

## User documentation

User-facing documentation is part of the product.

The Spanish user guide lives under:

```text
docs/user-guide/es/
```

The repository structure must remain ready for a future English guide under:

```text
docs/user-guide/en/
```

When a change modifies user-visible behavior, update the relevant user documentation in the same unit of work.

Examples that require documentation updates include:

- first-run workflow;
- host creation/editing;
- tunnel creation, testing, starting, stopping, and editing;
- jump servers;
- fingerprint approval/change handling;
- groups and bulk actions;
- Hub, favorites, recent items, search, and filters;
- projects, environments, and tags;
- global settings and per-tunnel overrides;
- configurable keyboard shortcuts;
- import/export;
- automatic backups and restoration;
- installed vs portable behavior;
- logs and diagnostic export;
- common errors and recovery steps.

Do not document a planned feature as if it were already implemented.

Purely internal refactors with no observable user impact do not require user-guide changes.

The repository root `README.md` is for project/development information. User documentation belongs under `docs/user-guide/`.

## OpenSpec task completion

Update `tasks.md` as work is completed.

A task is not complete until, as applicable:

- requested behavior is implemented;
- relevant tests are added or updated;
- Spotless formatting is correct;
- `mvnw verify` passes;
- ArchUnit rules pass;
- passwords are absent from logs/diagnostics;
- OpenSpec behavior and architecture are respected;
- relevant Spanish user documentation is created or updated;
- `tasks.md` reflects the completion state.

For SSH-related work, the applicable SSH integration tests must also pass.

For packaging/distribution work, the applicable `jpackage` build must also succeed.

If required validation cannot be executed in the current environment, report exactly what could not be executed and why. Do not claim the task is fully verified.

## Scope changes discovered during implementation

If implementation reveals a missing product decision, architectural conflict, or required scope change:

1. stop the affected portion of work;
2. describe the issue clearly;
3. identify the OpenSpec artifact that would need revision;
4. do not invent new behavior;
5. do not silently modify OpenSpec artifacts.
