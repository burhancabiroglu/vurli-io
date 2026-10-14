# Working guide for AI agents

## Orientation

- Communicate with the user in Turkish. Docs are Turkish; code identifiers are English.
- Start with `docs/progress.md`, then read only the docs relevant to the task. `README.md` has the map.
- Decisions live in `docs/decisions.md`. Items marked "Açık" are not decided: offer options instead of assuming. The latest user instruction wins over older docs.
- Product rules: `docs/product.md`. Structure and data model: `docs/architecture.md`. Threats and required tests: `docs/security.md`.

## Engineering

- Build and test: `./gradlew build` (Gradle Kotlin DSL; the Java 25 toolchain is resolved automatically). It needs Docker running, because Testcontainers starts PostgreSQL 18. `ModularityTests` checks module boundaries. Test counts are in `build/test-results/test/*.xml`.
- Modules are the top-level packages under `io.vurli`. A module's base package is its API; subpackages are internal.
- Schema changes go only through Flyway migrations in `src/main/resources/db/migration`. Hibernate is set to `validate`.
- `./gradlew bootRun` uses the `dev` profile: seed workspace from `db/dev` and an unauthenticated `/dev/api/links`. Anything `dev`-only must stay behind `@Profile("dev")`; `RedirectIntegrationTests` checks that the dev API is closed without it.
- Every change to a link must invalidate `LinkCache` after its transaction commits.
- Make one small behavior change at a time, together with its tests. Integration tests use real PostgreSQL via Testcontainers.
- Never weaken a test or a security control to get a passing result.
- Keep secrets and real personal data out of code, logs, fixtures and prompts.
- Commit messages and PR descriptions are written in English. Commits and PRs are authored only by the user: never add `Co-Authored-By` or any other AI attribution line.

## Handoff

- At the end of a session, update `docs/progress.md`: what changed, which checks ran (exact commands and results), and the next step.
- Report only checks that actually ran.
- Keep docs short. Update the existing doc that owns a topic instead of adding a new file.

## Code review priorities

1. Workspace isolation.
2. URL validation and redirect integrity.
3. Cache invalidation after a block or edit.
4. Unique constraints and transaction boundaries.
5. Password and token handling.
6. CSRF and XSS in the panel.
7. Secret exposure in CI.
