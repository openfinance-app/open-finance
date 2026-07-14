# Contributing to Open-Finance

Thank you for your interest in contributing! This guide covers everything you need to go from a fresh clone to a merged pull request.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Architecture](#architecture)
3. [API Reference](#api-reference)
4. [Coding Standards](#coding-standards)
5. [Internationalization](#internationalization)
6. [Database Migrations](#database-migrations)
7. [Testing](#testing)
8. [Commit Style](#commit-style)
9. [Pull Request Process](#pull-request-process)

---

## Getting Started

The recommended setup uses the pre-configured **DevContainer** — includes Java 21, Maven, Node.js, SQLite, and all VS Code extensions with zero manual installation.

**Requirements**: Docker + VS Code + [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)

```bash
git clone https://github.com/albilu/open-finance.git
code open-finance
# F1 → "Dev Containers: Reopen in Container"
# Wait ~10 min on first build, then:
mvn spring-boot:run               # backend  → http://localhost:8080
cd openfinance-ui && npm run dev  # frontend → http://localhost:3000
```

See [`.devcontainer/README.md`](.devcontainer/README.md) for full DevContainer details.

### Manual Setup (without DevContainer)

| Tool    | Minimum version |
| ------- | --------------- |
| Java    | 21              |
| Maven   | 3.9             |
| Node.js | 20              |
| SQLite  | 3.x             |

```bash
# Backend
mvn spring-boot:run

# Frontend
cd openfinance-ui
npm install
npm run dev
```

---

## Architecture

```
React UI (TypeScript)  →  Spring Boot REST API (:8080)
                              ├── Controllers → Services → JPA Repositories → SQLite (encrypted)
                              └── Integrations: Yahoo Finance · ECB Rates · Ollama LLM
```

### Backend Package Layout (`src/main/java/org/openfinance/`)

| Package       | Responsibility                                |
| ------------- | --------------------------------------------- |
| `controller/` | REST endpoints, request validation            |
| `service/`    | Business logic (interfaces + implementations) |
| `repository/` | JPA repositories                              |
| `entity/`     | JPA domain model                              |
| `dto/`        | Request/response objects                      |
| `config/`     | Spring configuration, security, caching       |
| `security/`   | JWT filter, encryption, key management        |
| `exception/`  | Domain exceptions + global handler            |
| `mapper/`     | MapStruct entity↔DTO mappers                  |
| `scheduler/`  | Background tasks (`@Scheduled`)               |

### Frontend Layout (`openfinance-ui/src/`)

`components/` · `pages/` · `services/` · `hooks/` · `context/` · `types/` · `utils/`

**Path alias**: `@/` resolves to `openfinance-ui/src/` — always use it instead of relative paths.

### Tech Stack

| Layer    | Technology                                              |
| -------- | ------------------------------------------------------- |
| Backend  | Java 21 · Spring Boot 3.2 · JPA/Hibernate · Flyway      |
| Database | SQLite (WAL mode)                                       |
| Frontend | React 19 · TypeScript 5 · Vite · Tailwind 4 · shadcn/ui |
| State    | TanStack Query · Zustand · react-hook-form + zod        |
| AI       | LangChain4j · Ollama (local)                            |

---

## API Reference

Interactive docs at **http://localhost:8080/swagger-ui.html** once the backend is running.

### Quick Examples

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"MyP@ss1!","masterPassword":"MasterP@ss1!"}'

# Login → returns JWT
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"MyP@ss1!","masterPassword":"MasterP@ss1!"}'

# Authenticated request
curl -H "Authorization: Bearer <jwt-token>" http://localhost:8080/api/v1/dashboard
```

### Core Endpoints

| Method   | Path                        | Description                                     |
| -------- | --------------------------- | ----------------------------------------------- |
| POST     | `/api/v1/auth/register`     | Register user                                   |
| POST     | `/api/v1/auth/login`        | Login, returns JWT                              |
| GET      | `/api/v1/dashboard`         | Dashboard summary                               |
| GET/POST | `/api/v1/accounts`          | Accounts                                        |
| GET/POST | `/api/v1/transactions`      | Transactions (requires `accountId` query param) |
| GET/POST | `/api/v1/assets`            | Assets                                          |
| GET/POST | `/api/v1/budgets`           | Budgets                                         |
| POST     | `/api/v1/import/upload`     | Import QIF/OFX/QFX                              |
| GET      | `/api/v1/reports/net-worth` | Net-worth report                                |
| POST     | `/api/v1/ai/chat`           | AI assistant                                    |

---

## Coding Standards

### Backend (Java)

- **Format**: run `mvn spotless:apply` (Google Java Format AOSP) before every commit
- **Static analysis**: `mvn spotbugs:check` — fix all issues before opening a PR
- **Injection**: constructor injection only (Lombok `@RequiredArgsConstructor`); no field injection
- **Monetary values**: `BigDecimal` always — never `float` or `double`
- **Missing entities**: return `Optional<T>` from repositories; use `.orElseThrow()` with a domain exception
- **Services**: define an interface + `Impl` class; `@Transactional` at class level, `readOnly = true` on reads
- **Method length**: < 50 lines; one responsibility per method
- **Logging**: `@Slf4j`; `ERROR` for unexpected failures, `WARN` recoverable, `INFO` lifecycle, `DEBUG` entry/params
- **Errors**: domain exceptions in `exception/`; mapped via `@ControllerAdvice`; never expose stack traces

Import ordering: `java.*` → third-party → `org.springframework.*` → `org.openfinance.*`.

### Frontend (TypeScript / React)

- **Strict mode**: `"strict": true` — avoid `any`; use `unknown` when the type is truly unknown
- **Server state**: TanStack Query (`useQuery` / `useMutation`) for all API data
- **Client state**: Zustand for global UI state
- **Forms**: `react-hook-form` + `zod` schema; resolver via `@hookform/resolvers/zod`
- **HTTP**: `apiClient` (axios) with `X-Encryption-Key` header from `sessionStorage.getItem('encryption_key')`
- **Imports**: always use the `@/` alias — never relative `../../` paths
- **Styling**: Tailwind utility classes; keep layout logic out of data-fetching hooks
- **Components**: functional + hooks only; `useMemo`/`useCallback` for expensive values or stable callbacks

Prettier config: `singleQuote: true`, `semi: true`, `tabWidth: 2`, `trailingComma: "es5"`, `printWidth: 100`, `arrowParens: "avoid"`.

---

## Internationalization

Open-Finance supports English (`en`) and French (`fr`).

| Artifact                | Location                                            |
| ----------------------- | --------------------------------------------------- |
| Backend message keys    | `src/main/resources/i18n/messages*.properties`      |
| Backend validation keys | `src/main/resources/ValidationMessages*.properties` |
| Frontend translations   | `openfinance-ui/public/locales/{en,fr}/`            |

**Workflow for new strings:**

1. Add the English key and value
2. Add the corresponding French key and value
3. Run `mvn -Dtest=MessageKeyCoverageTest test` to verify parity

**Key naming**: dot notation — e.g., `category.income.salary`, `error.validation.required`.

Backend services accept a `Locale` parameter; use `LocaleContextHolder.getLocale()` in controllers. DTOs use message keys (e.g., `nameKey`) instead of hardcoded strings.

---

## Database Migrations

All schema changes must go through **Flyway**:

- Add a new file to `src/main/resources/db/migration/` following the convention `V{version}__{description}.sql`
- Migrations run automatically on startup
- **SQLite caveat**: SQLite has no `ALTER TABLE DROP CONSTRAINT`. To change a `CHECK` constraint (e.g., when adding `InsightType` enum values), write a migration that recreates the table
- Never modify an already-applied migration — always add a new one

---

## Testing

### Backend

```bash
mvn test                                            # all tests
mvn -Dtest=AssetServiceTest test                    # single class
mvn -Dtest=AssetServiceTest#shouldReturnAsset test  # single method
mvn -Dtest=MessageKeyCoverageTest test              # verify i18n key coverage
mvn clean test jacoco:report                        # tests + coverage → target/site/jacoco/
```

**Patterns:**

- Unit tests: `@ExtendWith(MockitoExtension.class)`, `@Mock` / `@InjectMocks`, AssertJ assertions
- Repository tests: `@DataJpaTest` + `@AutoConfigureTestDatabase(replace=ANY)` + H2 in-memory
- Integration tests: `@SpringBootTest` + `MockMvc`; always supply required filter params (e.g., `accountId`)
- Use the builder pattern for test data; verify side-effects with `Mockito.verify()`

### Frontend

```bash
# Run from openfinance-ui/
npm test                               # all tests (Vitest)
npm test -- path/to/File.test.tsx      # single file
npm test -- -t "pattern"              # tests matching name
npm run type-check                    # tsc --noEmit
npm run lint                          # ESLint
npx playwright test                   # E2E tests
```

**Patterns:**

- Test runner is **Vitest** — use `vi.fn()`, `vi.mock()`, `vi.spyOn()` (not `jest.*`)
- Wrap all renders with `renderWithProviders()` from `@/test/test-utils`
- Call `mockAuthentication()` in `beforeEach` to seed `auth_token` and `encryption_key`
- Prefer accessible queries: `getByRole`, `getByLabelText`; avoid `getByTestId`
- Use `waitFor` for async assertions
- Mock `Element.prototype.scrollIntoView = vi.fn()` for components that auto-scroll

---

## Commit Style

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(transactions): add split transaction support
fix(import): handle OFX files with BOM character
docs(security): update JWT secret rotation steps
test(budgets): add monthly rollover edge case
chore(deps): bump spring-boot to 3.2.5
```

**Types**: `feat` · `fix` · `docs` · `refactor` · `test` · `chore`

---

## Pull Request Process

1. **Branch**: `git checkout -b feat/short-description` or `fix/short-description`
2. **Tests**: ensure `mvn test` (backend) and `npm test` (frontend) pass
3. **Format**: run `mvn spotless:apply` before pushing Java changes
4. **PR description**: summarize _what_ changed and _why_; reference related issues with `Closes #123`
5. **Review**: address all reviewer comments; PRs are merged by maintainers after ≥ 1 approval

**Never commit credentials or secrets.** Report security vulnerabilities privately via [GitHub Security Advisories](https://github.com/open-finance/open-finance/security/advisories) — see [docs/SECURITY.md](docs/SECURITY.md).
