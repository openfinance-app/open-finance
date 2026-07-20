# AGENTS.md — Guidelines for agent contributors

Concise, actionable rules for automated or human agents working in Open-Finance.

## Project snapshot
- Backend: Java 21, Spring Boot 3.2, JPA/Hibernate, SQLite (WAL mode), Flyway, JWT
- Frontend: React 19 + TypeScript 5, Vite, Tailwind 4, shadcn/ui, Radix UI — lives in `openfinance-ui/`
- State: TanStack Query (server), Zustand (client), react-hook-form + zod (forms)
- Build: Maven 3.9 (backend), npm/Bun (frontend)

---

## Build, lint & run tests

### Backend (Maven) — run from repo root
```
mvn clean install              # full build with tests
mvn clean install -DskipTests  # fast build, skip tests
mvn test                       # all tests
mvn -Dtest=AssetServiceTest test                    # single class
mvn -Dtest=AssetServiceTest#shouldReturnAsset test  # single method
mvn -Dtest=MessageKeyCoverageTest test              # verify i18n key coverage
mvn clean test jacoco:report   # tests + coverage report
mvn spotless:apply             # format Java (Google AOSP style, run before committing)
mvn spotless:check             # verify formatting
mvn spotbugs:check             # static analysis
mvn spring-boot:run            # start locally (port 8080)
```

### Frontend (Vitest) — run from `openfinance-ui/`
```
npm install                    # install deps (or bun install)
npm run dev                    # dev server (port 5173)
npm test                       # all tests (Vitest — NOT Jest)
npm test -- path/to/File.test.tsx              # single test file
npm test -- -t "pattern"                       # tests matching name pattern
npm run type-check             # tsc --noEmit
npm run lint                   # ESLint (auto-fixes on git commit via lint-staged; no lint:fix script)
npm run format:check           # prettier check (no standalone format script — lint-staged runs it)
npm run build                  # production build
npx playwright test            # E2E tests
```

### CI
See `.github/workflows/backend-ci.yml` and `frontend-ci.yml` for environment details.

---

## Git & commit rules
- Only create commits when the user explicitly asks. Use Conventional Commits: `type(scope): short subject`.
- Never run destructive git commands (`--hard` reset, force push) without explicit permission.
- Do not commit secrets or credentials; warn the user and exclude them.
- When a change affects DB schema, add a Flyway migration in `src/main/resources/db/migration/`.

---

## Java / Spring Boot style

**Package layout:** `org.openfinance.{config,controller,service,repository,entity,dto,mapper,exception,util,validator}`

**Imports:** group `java.*` → third-party → `org.springframework.*` → `org.openfinance.*`; no wildcard imports.

**Naming:** Classes `PascalCase`, methods/variables `camelCase`, constants `UPPER_SNAKE_CASE`, packages lowercase.

**Types:** Prefer explicit types over `var`. Use `BigDecimal` for all monetary values — never `float`/`double`.

**Services:** Define an interface (e.g., `AssetService`) and an implementation (`AssetServiceImpl`). Annotate with `@Service`, `@Transactional` (class-level for writes); add `@Transactional(readOnly = true)` on read methods.

**Injection:** Constructor injection only (use Lombok `@RequiredArgsConstructor`).

**Lombok:** Use `@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor` to reduce boilerplate.

**MapStruct:** Mapper interfaces live in `org.openfinance.mapper`; annotate with `@Mapper(componentModel = "spring")`. MapStruct runs before Lombok — `lombok-mapstruct-binding` ensures correct annotation processor order.

**Validation:** Jakarta Validation annotations (`@NotNull`, `@Valid`, `@Min`, `@Digits`, etc.) at controller boundaries and entity fields.

**Optional:** Return `Optional<T>` for possibly-missing entities; prefer `.orElseThrow()` with a domain exception.

**Formatting:** `mvn spotless:apply` (Google Java Format AOSP, 1.17.0). Keep methods < 50 lines; single responsibility.

### Error handling & logging (backend)
- Domain exceptions under `exception/` extending `RuntimeException` with meaningful messages.
- Map to HTTP responses via `@ControllerAdvice`; return structured error DTOs. Never expose stack traces.
- Log levels: `ERROR` unexpected failures, `WARN` recoverable conditions, `INFO` lifecycle events, `DEBUG` entry/params.

---

## TypeScript / React style

**Frontend root:** `openfinance-ui/` — all frontend paths below are relative to `openfinance-ui/src/`.

**File structure:** `{components,pages,hooks,services,types,utils,context,assets}`

**Path alias:** `@/` resolves to `openfinance-ui/src/` — always use `@/` instead of relative `../../`.

**Naming:** Components `PascalCase`; utility files kebab-case; types `PascalCase`.

**TypeScript:** `"strict": true`. Avoid `any`; use `unknown` if type is truly unknown. Explicit parameter and return types.

**Components:** Functional + hooks only. `useMemo`/`useCallback` for expensive values or stable references.

**State:** TanStack Query (`useQuery`/`useMutation`) for all server state. Zustand for global UI state.

**Forms:** `react-hook-form` + `zod` schema; resolver from `@hookform/resolvers/zod`.

**HTTP:** `apiClient` (axios) with `X-Encryption-Key` header read from `sessionStorage.getItem('encryption_key')`.

**Styling:** Tailwind utility classes; keep layout/visual logic out of data-fetching hooks.

**Prettier config:** `singleQuote: true`, `semi: true`, `tabWidth: 2`, `trailingComma: "es5"`, `printWidth: 100`, `arrowParens: "avoid"`, `endOfLine: "lf"`.

### Frontend testing patterns
- **Test runner is Vitest**, not Jest — use `vi.fn()`, `vi.mock()`, `vi.spyOn()` (not `jest.*`).
- Wrap all renders with `renderWithProviders()` from `@/test/test-utils` (provides QueryClient, I18next, Router, Auth, etc.).
- Call `mockAuthentication()` in `beforeEach` to seed `auth_token` (localStorage) and `encryption_key` (sessionStorage).
- Stub heavy child components with `vi.mock('@/components/...')` returning minimal `<select>`/`<input>` elements.
- Prefer accessible queries: `screen.getByRole`, `screen.getByLabelText`; avoid `getByTestId`.
- Use `waitFor` for async state assertions.
- Mock `Element.prototype.scrollIntoView = vi.fn()` in `beforeEach` for components that auto-scroll.

### Backend testing patterns
- Unit tests: `@ExtendWith(MockitoExtension.class)`, `@Mock` / `@InjectMocks`, `@DisplayName`. Assert with AssertJ (`assertThat`) and `assertThrows`.
- Repository/slice tests: `@DataJpaTest` + `@AutoConfigureTestDatabase(replace=ANY)` + H2 in-memory.
- Integration tests: `@SpringBootTest` + `MockMvc` or rest-assured; supply required filter params (e.g., `accountId`).
- Use builder pattern for test data. Verify side-effects with `Mockito.verify()`.

---

## Internationalization (i18n)
- Supported locales: English (`en`) and French (`fr`).
- Backend keys: `src/main/resources/i18n/messages*.properties` and `ValidationMessages*.properties`.
- Frontend translations: `openfinance-ui/public/locales/{en,fr}/`.
- Key naming: dot notation — e.g., `category.income.salary`, `error.validation.required`.
- Adding translations: add English key → add French key → run `mvn -Dtest=MessageKeyCoverageTest test`.
- Controllers: use `LocaleContextHolder.getLocale()`; services accept a `Locale` parameter.
- Import parsers (`service/parser/`) run async without a request locale: they accept an `ImportParseContext` (built by `ImportService` from `UserSettings`) for message locale and CSV date-order preference. Format conventions always win over user preference (QIF = MM/DD).
- DTOs: use message keys (e.g., `nameKey`) instead of hardcoded strings.
- French `date-fns` locale uses typographic apostrophe (U+2019); use `String.fromCharCode(8217)` in test assertions.

---

## Repo-specific agent behaviors
- Never revert others' changes without explicit request; respect a dirty working tree.
- SQLite WAL mode: `busy_timeout=5000` (JDBC URL) handles writer contention (`SQLITE_BUSY`) but **not** stale snapshots (`SQLITE_BUSY_SNAPSHOT`). Fix snapshot errors by using `Propagation.NOT_SUPPORTED` on the top-level method and extracting User writes into a separate `@Service` with its own `@Transactional`.
- Concurrent writes to tables with unique constraints (e.g., `net_worth`) require a warm-up request first in parallel tests.
- SSE/EventSource cannot send JWT headers — use synchronous POST endpoints for authenticated AI endpoints.
- New `InsightType` enum values require a Flyway migration to drop and recreate the `CHECK` constraint (SQLite has no `ALTER TABLE DROP CONSTRAINT`).

---

## References & helpers
- **`.agents/LESSONS_LEARNED.md` — read before writing any test**; contains critical gotchas on SQLite WAL, Langchain4J API, i18next async, Radix UI in jsdom, and more.
- CI workflows: `.github/workflows/backend-ci.yml`, `.github/workflows/frontend-ci.yml`.
- Devcontainer: `.devcontainer/` for reproducible dev environment.
- Cursor/Copilot rules: none present (`.cursorrules`, `.cursor/`, `.github/copilot-instructions.md` do not exist).

## Quick verification checklist
1. Reproduce the failure with the exact single-test command above.
2. Make the minimal fix; rerun the specific test, then the full suite.
3. Run `mvn spotless:apply` (Java) or ensure lint-staged ran (TS) before committing.
4. If asked to commit, follow the git rules above and use a Conventional Commit message.
