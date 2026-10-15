# Working guide for AI agents

## Orientation

- Communicate with the user in Turkish. Docs are Turkish; code identifiers are English.
- Start with `docs/progress.md`, then read only the docs relevant to the task. `README.md` has the map.
- Decisions live in `docs/decisions.md`. Items marked "Açık" are not decided: offer options instead of assuming. The latest user instruction wins over older docs.
- Product rules: `docs/product.md`. Structure and data model: `docs/architecture.md`. Threats and required tests: `docs/security.md`.

## Engineering

- Monorepo: `backend/` (Spring Boot, Gradle Kotlin DSL) and `frontend/` (Next.js, npm). Run commands from inside the folder.
- Backend build and test: `cd backend && ./gradlew build` (the Java 25 toolchain is resolved automatically). It needs Docker running, because Testcontainers starts PostgreSQL 18. `ModularityTests` checks module boundaries. Test counts are in `backend/build/test-results/test/*.xml`.
- Frontend checks: `cd frontend && npm ci && npm run lint && npm run typecheck && npm run build`. Production dependencies must pass `npm audit --omit=dev --audit-level=high`. Read `frontend/AGENTS.md` before writing Next.js code.
- The Next.js frontend is a BFF: it holds no business rules and never talks to the database. Every authorization decision is made in Spring. Only `frontend/src/lib/api.ts` (server-only) calls Spring; pages get the user from `lib/session.ts`. UI text lives in `src/lib/i18n/dictionaries/{tr,en}.json` and both files must keep the same keys (a unit test checks this).
- Frontend tests: `npm test` (Vitest) for pure logic in `src/lib`. Check pages in a real browser against a running backend; `src/proxy.ts` sets a nonce-based CSP, so watch the console for violations.
- Modules are the top-level packages under `io.vurli`. A module's base package is its API; subpackages are internal.
- Schema changes go only through Flyway migrations in `backend/src/main/resources/db/migration`. Hibernate is set to `validate`.
- `./gradlew bootRun` uses the `dev` profile and starts the root `compose.yaml` (PostgreSQL, Mailpit on http://localhost:8025). If one of those containers already runs, Spring does not start the others; run `docker compose up -d` from the root then.
- The JSON API lives under `/api/v1` and needs a session (except register, login, verify-email and password reset). State-changing requests must send the `X-Vurli-Client` header (CSRF defence, see `ClientHeaderFilter`). The session cookie is defined in `SessionCookieConfiguration`, not in `server.servlet.session.*` properties.
- `HostFilter` answers short links (single-segment paths) only on `vurli.hosts.public` and everything else only on `vurli.hosts.internal`. Tests run without the dev profile, so short-link requests use `http://vurli.io/...` and API requests the default `localhost`.
- Rate limits (K42) live in `abuse.RateLimiter` and are shared by every test in a Spring context. `TestcontainersConfiguration` gives each MockMvc request a random client address, so tests do not use up each other's limits; tests about limits set the address explicitly. The dev profile widens the per-address limits because all local requests share one address.
- End-to-end tests: `cd frontend && npm run build && npm run e2e` (Playwright) with the backend running via `bootRun`; they read real emails from Mailpit. The `E2E` workflow runs them in CI.
- Integration tests send real emails to a Mailpit container; read them with the `Mailpit` test helper. `TestAccounts` registers, verifies and signs in users the way a user would.
- Account responses must never reveal whether an address has an account.
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
