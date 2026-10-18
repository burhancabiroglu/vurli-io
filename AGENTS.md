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
- Rate limits (K42) live in `ratelimit.RateLimiter` and are shared by every test in a Spring context. `TestcontainersConfiguration` gives each MockMvc request a random client address, so tests do not use up each other's limits; tests about limits set the address explicitly. The dev profile widens the per-address limits because all local requests share one address.
- Click statistics (`analytics`): clicks are queued in memory and written every second, so tests call `ClickWriter.flush()` before reading them. `DailySummaries.run(today)` runs the nightly summary on demand. The country database is not in the repository (`backend/scripts/download-geoip.sh`); without it clicks have no country and `MmdbCountryLookupTests` is skipped. Requests from automated User-Agents are not counted, so E2E checks that should not count send a monitor User-Agent.
- Admin panel (`admin` module, `admin.vurli.io`): tests use `http://admin.vurli.io/admin/...`, Spring Security's `csrf()` request post-processor, and `account.internal.TestTotp` to compute the code an authenticator app would show. Access needs the `ADMIN_SECOND_FACTOR` authority, never just `ROLE_ADMIN`. `vurli.admin.totp-key` is required; tests and the dev profile set a fixed key. `admin/AdminSessions` signs in a real administrator for tests. Every admin action writes the audit log with the acting administrator and a reason. The E2E admin test promotes its account with `docker compose exec postgres psql`, so Compose must be the one from the repository root.
- Modules: `ratelimit` (limits, client address) and `audit` (audit log) are separate from `abuse`, so `abuse` can depend on `link` without a cycle. Links ask `link.TargetSafety`; `abuse` implements it with Web Risk. Tests that need verdicts replace it with `@MockitoBean TargetSafety`; without `vurli.web-risk.api-key` every verdict is UNKNOWN and nothing is sent. Tables that reference `links` (clicks, summaries, reports) must be in any `truncate` of `links`.
- Load test: `cd backend && loadtest/run.sh` (Gatling, `src/gatling`, K52). Never part of `build`. Gradle's daemon runs on Java 25 (`gradle/gradle-daemon-jvm.properties`) because Gatling runs simulations in the daemon's JVM. Record new results in the architecture doc's scale section.
- End-to-end tests: `cd frontend && npm run build && npm run e2e` (Playwright) with the backend running via `bootRun`; they read real emails from Mailpit. The `E2E` workflow runs them in CI.
- Integration tests send real emails to a Mailpit container; read them with the `Mailpit` test helper. `TestAccounts` registers, verifies and signs in users the way a user would.
- Account responses must never reveal whether an address has an account.
- Every change to a link must invalidate `LinkCache` after its transaction commits.
- Make one small behavior change at a time, together with its tests. Integration tests use real PostgreSQL via Testcontainers.
- Never weaken a test or a security control to get a passing result.
- Keep secrets and real personal data out of code, logs, fixtures and prompts.
- Commit messages and PR descriptions are written in English. Commits and PRs are authored only by the user: never add `Co-Authored-By` or any other AI attribution line.

## Known pitfalls

Each of these has cost a failed build, a red CI run or a test that proved nothing.

- **Tests that test nothing:** prove every security or cache test with a negative check: remove the protection, see the test fail, restore it. Visit a link before blocking it, or a missing cache eviction goes unnoticed. MockMvc encodes URI template variables again (`%25` becomes a plain search), so pass such values with `.param()`.
- **Timing in limit tests:** buckets refill continuously. Assert on the rejected response itself, never on a follow-up request, and test exact `Retry-After` values in `RateLimiterTests` with the manual `TimeMeter`.
- **Diagnose CI from evidence:** read `gh run view <id> --log-failed` or the Backend job's test annotations before changing code. A guessed fix once left CI red.
- **Java and Spring:** annotation values must be compile-time constants (`"a".repeat(n)` does not compile; write the literal). A bean with two constructors needs `@Autowired` on the injection one. `Map.of` rejects null values. A lambda passed to `JdbcTemplate.query` can be ambiguous; cast it to `RowCallbackHandler`. `InetAddress.ofLiteral` accepts legacy IPv4 forms such as `1.2.3`, so client addresses are checked with a strict pattern.
- **Spring Security:** an access-denied handler that redirects also catches CSRF failures; those must stay 403 (`DelegatingAccessDeniedHandler`). MockMvc does not write `SameSite` into the `Set-Cookie` header; read it with `cookie.getAttribute("SameSite")` and confirm on the real server with `curl -i`.
- **Keys:** a Base64 AES key must decode to exactly 32 bytes; check it before use (the app refuses to start otherwise).
- **GeoIP tests:** use national research network addresses (ULAKBİM for TR, WIDE for JP), not anycast addresses such as public DNS resolvers.
- **Frontend and CSP:** no `dangerouslySetInnerHTML` and no inline `<style>`; components that emit inline `style` (Recharts) render only on the client. Do not disable lint rules such as React's `set-state-in-effect`; restructure instead. React 19 resets a form after its action, so echo typed values back in the error state, never passwords.
- **E2E reruns:** the local database keeps earlier runs, so text a test searches for (an audit reason, a link alias) must be unique per run.
- **Dependencies:** major bumps of `typescript`, `eslint` and `@types/node` are ignored in Dependabot because `eslint-config-next` does not support them yet. Never merge a PR with red CI.
- **Load test:** reuse connections (`shareConnections`); a new TCP connection per visit exhausts local ports on macOS after about 11k requests and measures the machine, not the app.
- **macOS shell:** BSD `sed` has no `\|`; use `perl`. Turkish text contains apostrophes that end single-quoted shell strings; use the Edit tool for docs. A scripted replacement that does not match fails silently, so check the file after editing.

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
