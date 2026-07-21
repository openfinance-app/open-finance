
# Codebase Review: Hardcoded Values, Magic Numbers & Risk Patterns

Four parallel audits completed. Here's the synthesized report.

---

## ✅ Remediation Status

| Item | Status |
| --- | --- |
| **Critical Theme 1 — Default-currency chaos** | ✅ **FIXED & VERIFIED** |
| Critical Theme 2 — Float/double monetary math | ⬜ Not started |
| Critical Theme 3 — Currency-decimal hardcoding | ⬜ Not started |
| HIGH / MEDIUM / LOW severity items | ⬜ Not started |

**Verification:** backend `mvn test` → 2371 pass / 0 fail / 0 err; frontend `type-check` + Vitest → 3253 pass / 0 fail; `mvn spotless:apply` + ESLint (0 errors) clean.

---

## Critical Themes (Root Causes)

### 1. Default-currency chaos — no single source of truth ✅ FIXED
`application.yml` says `base-currency: EUR`, `User.java:92` defaults to `"USD"`, frontend `currency.ts:46` defines `DEFAULT_CURRENCY = 'EUR'`, and services fallback to a mix of USD/EUR depending on the method. A user with null `baseCurrency` gets **different currencies from different endpoints**.

- `entity/User.java:92` — `private String baseCurrency = "USD";`
- `service/NetWorthService.java:107,171,282,393,407` — convenience overloads hardcode `"USD"`
- `service/DashboardService.java` — **6 USD fallbacks** (lines 201,436,505,582,811,2172) and **6 EUR fallbacks** (lines 992,1135,1301,1463,1601,1855) in the **same class**
- `service/AuthService.java:187,243`, `controller/DashboardController.java:531`, `scheduler/NetWorthSnapshotScheduler.java:137` — all `"USD"`
- `service/ai/FinancialContextBuilder.java:187,233,273,339,379` — `"EUR"` (AI gets wrong currency)
- `service/ImportService.java:265,455,551,704,1562,2129,2319,2612,2661` — 9× `"USD"` on lookup failure
- `service/TransactionService.java:399,400,689,691` — transfers forced to USD when account.currency null
- `service/BudgetService.java:1012` — budget suggestions labeled EUR even for USD users
- Frontend: `TransactionForm.tsx:147` defaults to `'EUR'`; `OnboardingPage.tsx:51` falls back to `'USD'`; ~20 other components split EUR/USD

**PO Assessment:** User can set their preferred base currency in settings, if not set, a default application to EUR.

**Fix:** Introduce one `DefaultCurrencyProvider` reading `application.yml`, replace every `: "USD"` / `|| 'EUR'` fallback, remove the `User.baseCurrency = "USD"` entity default.

**✅ Resolution (done):**
- New `config/ExchangeRateProperties` binds `application.exchange-rates.base-currency` (default **EUR**); new `service/DefaultCurrencyProvider` (`getDefaultCurrency` / `resolve` / `resolveForUser`) is the single source of truth — added test-first with 9 unit tests (`DefaultCurrencyProviderTest`).
- Removed the `User.baseCurrency = "USD"` entity default; registration (`UserService`) and both seeders (`LiveDemoData`, `TestDataSeeder`) now set the app default explicitly.
- Replaced **every** hardcoded USD/EUR user-default fallback across the backend: `DashboardService` (12), `NetWorthService` (5 overloads), `AuthService`, `DashboardController`, `NetWorthSnapshotScheduler`, `FinancialContextBuilder` (5), `ImportService` (9), `AICategorizationService`, `BudgetService` (3), `InsightService` (2), `TransactionService` (transfers + resolve), and the 5 duplicated `resolveBaseCurrency` copies (`Asset`/`Account`/`RealEstate`/`Transaction`/`Liability` services) now delegate to the provider.
- Frontend: unified **29 files / 42 literals** onto `DEFAULT_CURRENCY` (EUR) from `@/utils/currency`.
- Deliberately left untouched (legitimate, not user-defaults): `ExchangeRateService` USD FX-pivot logic, demo/seed account-currency data, the ISO `CURRENCIES` list, and country→currency maps.

### 2. Float/double for monetary math (violates AGENTS.md)
- `service/InterestCalculatorService.java:75-84` — compound interest computed in `double`, `BigDecimal.valueOf(netInterest)`
- `service/FinancialFreedomService.java:433-637` — `calculateMonthsToTarget`, `generateProjections`, `generateSensitivityScenarios` all use `double` rates/balances; magic numbers `-5.0, 2.0, 15.0`
- `provider/YahooFinanceProvider.java:436` — `BigDecimal.valueOf(node.asDouble())` instead of `node.decimalValue()`
- `entity/Asset.java:364,380,412` — `BigDecimal.valueOf(0.10)` (double overload, imprecise)
- `entity/AssetCondition.java:71-79` — `getValueRetentionFactor()` returns `double`
- `service/DashboardService.java:1385,1530` — `BigDecimal.valueOf(0.40)` for debt-to-income
- `service/DashboardService.java:2177-2202` — `BigDecimal.valueOf(1.0/12)`, `1.0/365` etc. (imprecise)
- `dto/calculator/CalculationDefaults.java:25-49` — all rate defaults as `double`
- Frontend `utils/realEstateCalculations.ts` & `financialCalculations.ts` — entire calculators in `number`/`double` with `Math.round(x*100)/100`

**PO Assessment:** Apply what fit best for monetary math.

### 3. Currency-decimal hardcoding (breaks JPY=0, BTC=8)
- `hooks/useLiabilities.ts:358` — `Math.abs(amount).toFixed(2)` for all currencies
- `hooks/useCurrency.ts:142,143` & `utils/portfolio.ts:129,130` — `minimumFractionDigits: 2, maximumFractionDigits: 2`
- `utils/format.ts:29,65` & `components/real-estate-tools/performance.tsx:212,213` — `maximumFractionDigits: 2`
- Backend `setScale(2, RoundingMode.HALF_UP)` on amounts across `RealEstateService`, `LiabilityService`, `DashboardService`, `BudgetService`, `CompoundInterestService`, `InterestCalculatorService` — fine for fiat, risky for crypto accounts

**Fix:** Route through `getCurrencyDecimals(currency)` which already exists in `currency.ts`.

## HIGH Severity (other)

- **`Locale.FRENCH` hardcoded** — `BudgetService.java:1374,1376` forces French month names for all users
- **`Locale.ENGLISH` hardcoded** — `CategoryService.java:296,349,400,508,723` and `FinancialContextBuilder.java:77,155` ignore user locale
- **English substring matching for category classification** — `InsightService.java:440-442,869-875` matches "subscription", "streaming", "donat", etc.; French categories silently fail to trigger insights. Should match on `Category.nameKey`, not decrypted display name
- **Frontend forces `'fr-FR'` formatting** — `real-estate-tools/performance.tsx:209,216,222` and `ResultsPanel/EvolutionChart.tsx:104,127` render French number format for all users
- **Two divergent apiClient files** — `services/apiClient.ts` (full URL) vs `lib/apiClient.ts` (empty baseURL + Vite proxy); `SecuritySettings.tsx` uses the wrong one with `/api/v1/users/me/password` paths
- **Hardcoded RSS URLs** — `service/RssService.java:30-38` (Yahoo, WSJ, Le Monde, Figaro) not in `application.yml`
- **Hardcoded OpenAPI server URL** — `config/OpenApiConfig.java:55` `http://localhost:8080` breaks Swagger "Try it out" in any non-local env
- **LoggingAspect redaction gap** — `util/LoggingAspect.java:163` only matches substring `"password"` — misses `secret`, `token`, `apiKey`, `motDePasse`, `X-Encryption-Session` header values
- **Broken/unauthenticated SSE** — `hooks/useAIChat.ts:106-113` uses `EventSource` (no JWT headers, `withCredentials: false`) hitting a backend `@PostMapping` — dead code that won't work and would bypass auth if fixed wrong
- **JSON built via string concatenation** — `service/ai/OpenAIProvider.java:71-99` only escapes `"` and `\n`; invalid JSON for prompts with `\`, tabs, control chars
- **CsvParser bypasses locale** — `service/parser/CsvParser.java:52-84` has 12 date formatters all using `Locale.ENGLISH`, ignoring the `ImportParseContext` locale. French CSV exports (with French month names) may be unparseable

**PO Assessment:** Fix all above accordingly.

## MEDIUM Severity (highlights)

- **`NetWorthService.saveNetWorthSnapshot` TOCTOU race** — `service/NetWorthService.java:428-471` read-then-insert without locking;
- **`EncryptionService` cache eviction race** — `security/EncryptionService.java:465-471` non-atomic check-then-act on `ConcurrentHashMap`; bounded-size invariant violated
- **Non-portable timezone** — `util/DateTimeUtil.java:27` `ZoneId.systemDefault()`; `OperationHistory.java:113` uses UTC while every other entity uses `LocalDateTime.now()` (system zone) — silent offset when comparing
- **JWT expiration config is dead** — `service/JwtService.java:36` `JWT_EXPIRATION_MS = 24*60*60*1000` ignores `application.yml:jwt.expiration`
- **`@Valid` missing** — `controller/ImportController.java:560` on `List<ImportedTransaction>` — only place in codebase
- **`busy_timeout` inconsistency** — `config/DatabaseConfig.java:107` `PRAGMA busy_timeout=10000` vs JDBC URL `busy_timeout=5000`
- **AI provider config defaults diverge** — `AIProviderConfig.java:36,45` default `qwen2.5:0.5b/60s` vs `application.yml:153-155` `llama3.2:3b/600s`
- **Magic numbers in business logic** — `InsightService.java` (40% spending anomaly, 75% budget warn, $20 min subscription, $100 low balance, 50% recurring ratio); `AccountSpecification.java:108` `< 1000` low-balance threshold; `DashboardService.java` debt-to-income `12,10,20,35,50`
- **200+ entity validation messages hardcoded English** — entities use raw strings while DTOs use `{key}` notation — entity validation failures return English regardless of locale
- **Most exception classes bypass `LocalizableException`** — only 4 of 30+ implement it; `DuplicatePayeeException.java:7` uses `super("A payee with this name already exists...")` despite `error.payee.duplicate` key existing
- **`BudgetPeriod` enum** — `entity/BudgetPeriod.java:25-34` hardcoded English display names (`"Weekly"`, `"Monthly"`, etc.) instead of `nameKey`
- **`GlobalExceptionHandler` substring status matching** — `exception/GlobalExceptionHandler.java:591-605` maps HTTP status by `message.contains("empty"|"Invalid"|"corrupted"|...)` — breaks if messages translate
- **Frontend storage keys duplicated as literals** — `'auth_token'`, `'encryption_session'` (`ENCRYPTION_SESSION_KEY` defined twice, in `encryption.ts` and `useAuth.ts`), `'session_start_time'`, etc. — 10+ places
- **Frontend route paths scattered** — no central `routes.ts`; literals in 25+ locations
- **Hardcoded chart colors** — `utils/portfolio.ts:173-179`, `types/alert.ts:50-54`, `types/notification.ts:23-27`, `pages/CategoriesPage.tsx:215-217` — not from design tokens
- **Hardcoded breakpoints bypassing `useBreakpoint`** — `real-estate-tools/BuyRentComparator.tsx:150,158,166,174` `window.innerWidth >= 1024`; `DashboardPage.tsx:640` uses `md: 996` while `useBreakpoint.ts:15` uses `md: 768`
- **`setTimeout`/`setInterval` magic numbers** — 50/100/150/200/300/3000/5000/8000 ms scattered through components
- **`LogoFetchService.java:111`** — `slug + ".com"` TLD assumption (wrong for `.fr`, `.co.uk`, etc.)
- **`vite.config.ts:14,17`** — dev port 3000 but `application.yml` CORS allows `http://localhost:3000,http://localhost:5173` (5173 never used)
- **Hardcoded French UI strings** — `real-estate-tools/*` (~80 strings) display French to English users
- **~231 hardcoded English JSX strings** — not wrapped in `t()` (e.g. `ErrorBoundary.tsx:47`, `AttachmentList.tsx:189-239`, `PropertyDetailView.tsx`, `RssFeedCard.tsx`, `TimelineProjection.tsx`)
- **`SecurityContext` thread-local + reactive types** — Returning reactive types (Mono/Flux) from Spring MVC controllers in a Servlet-based app causes 403 Forbidden; SecurityContext is thread-local and doesn't propagate to Reactor scheduler threads — convert to synchronous or call `.block()` in the service layer. Bbut `OpenAIProvider.java:51` still uses WebClient with `.block()` in service — works but fragile
- **`resolveBaseCurrency()` duplicated identically in 5 services** — `AssetService.java:925`, `TransactionService.java:1814`, `AccountService.java:1087`, `RealEstateService.java:1376`, `DashboardService.java:2165`; should be centralized in `UserService`
- **`populateConversionFields()` duplicated across 3 services** — near-identical currency conversion logic in `AssetService.java:852`, `AccountService.java:1016`, `TransactionService.java:1780`; extract `CurrencyConversionHelper`
- **`PERCENT = new BigDecimal("100")` bare constant 35+ times across 9 files** — `RealEstateService.java`, `InsightService.java`, `FinancialFreedomService.java`, etc.; no shared `MathConstants.PERCENT`
- **Period divisors `12` and `30` bare in 5+ files** — some files define `MONTHS_PER_YEAR`, others don't; `InsightService.java`, `DashboardService.java`
- **DTI thresholds (40%, 20, 35, 50), max-borrowing terms (10 years) hardcoded** — `DashboardService.java:1385-1546`; not configurable
- **Default interest/inflation rates duplicated between DTO and service** — `withdrawalRate=4%`, `inflationRate=2.5%` in both `FreedomCalculatorRequest.java:94,102` and `FinancialFreedomService.java:51,54`; can drift apart 
- **Crypto currency list hardcoded in backend** — `service/ExchangeRateService.java:779-782` `List.of("BTC", "ETH", "BNB", ...)`; adding a coin requires code change
- **`"USD"` DB default for user base_currency in V12 migration** — `V12__add_user_base_currency.sql:10` vs `application.yml:110` EUR; two separate defaults can diverge
- **Test data uses USD monoculture** — 70+ test files never test with JPY, crypto, or zero-decimal currencies; `AccountRepositoryTest.java`, `AssetRepositoryTest.java`, etc.
- **Country-to-currency map duplicated** — `OnboardingPage.tsx:24-33` and `buyVsRentConfig.ts:62-167` unsynchronized
- **"get all" page sizes vary** — 10000 in most places, 1000 in `LiabilitiesPage`; fragile "unlimited" heuristic

**PO Assessment:** Fix all above accordingly. 
NOTES:
1-Magic numbers in business logic: InsightService.java (40% spending anomaly, 75% budget warn, $20 min subscription, $100 low balance, 50% recurring ratio); AccountSpecification.java:108 < 1000 low-balance threshold; DashboardService.java debt-to-income 12,10,20,35,50 
should be configurable.
2-Crypto currency list should not be hardcoded. Use similar fiat currenciues ISO 4217 list or external free provider.


## LOW Severity (highlights)

- `service/FinancialFreedomService.java:674` — `String.format("€%,.2f", amount.doubleValue())` hardcoded currency symbol + double
- `util/DateTimeUtil.java:22-24` — display formatters not locale-aware
- `service/RssService.java:43,54` — `new RestTemplate()` per call; hardcoded Chrome 120 User-Agent
- `entity/Backup.java:134-143` and `entity/Attachment.java:189-196` — duplicate file-size formatters with inconsistent precision (`%.2f` vs `%.1f`)
- `service/ai/OllamaClient.java:77` and `OpenAIProvider.java:62` — `10 * 1024 * 1024` max in-memory size magic number
- `application-railway.yml:124` — hardcoded deployment URL `https://open-finance.up.railway.app`
- `service/RepairRunner.java`, `TestDataSeeder.java` — `0.10`, `365`, `12` magic numbers in calculations
- `InsightService.java` ~24 `.doubleValue()` calls for message formatting (loses precision >2^53)
- Frontend `Number(x.toFixed(2))` chains in `CompoundInterestCalculator.tsx:268,331-333` re-introduce float error
- **Scheduler timezone defaults diverge** — Java default `America/New_York` vs `application.yml` `Europe/Paris`; duplicate defaults
- **QIF parser tests use hardcoded MM/DD/YYYY dates** — `QifParserTest.java`; may pass/fail differently on non-US locale JVMs

**PO Assessment:** Fix all above accordingly.

---

