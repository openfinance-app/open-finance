
# Codebase Review: Hardcoded Values, Magic Numbers & Risk Patterns

Four parallel audits completed. Here's the synthesized report.

---

## ✅ Remediation Status

| Item | Status |
| --- | --- |
| **Critical Theme 1 — Default-currency chaos** | ✅ **FIXED & VERIFIED** |
| **Critical Theme 2 — Float/double monetary math** | 🟡 **IN PROGRESS** — backend money-math ✅, P0 (split precision) ✅, P1 (rate DTOs) ✅, P2 (decimal foundation) ✅, P3 (app-wide frontend precision) ✅ done; P4 (calculator tools) deferred (see remaining plan) |
| Critical Theme 3 — Currency-decimal hardcoding | ⬜ Not started |
| HIGH / MEDIUM severity items | ⬜ Not started |
| **LOW severity items** | ✅ **FIXED & VERIFIED** (8 of 11 real items fixed; 3 were stale/already-resolved — see notes) |

**Verification:** backend `mvn test` → 2395 pass / 0 fail / 0 err; frontend `type-check` + Vitest → 3284 pass / 0 fail; `mvn spotless:apply` + ESLint (0 errors, backend & frontend) clean.

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

### 2. Float/double for monetary math (violates AGENTS.md) 🟡 IN PROGRESS
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

**✅ Phase 1 done — backend money-math:**
- `InterestCalculatorService.calculateInterestEstimate` — compound interest now pure BigDecimal via `pow(int, MathContext)` (the exponent `n` is an integer number of compounding periods); no more `double`/`Math.pow`. Added 5 hand-verifiable characterization tests (`InterestCalculatorServiceTest`) — the method previously had none.
- `FinancialFreedomService.calculateMonthsToTarget` & `generateProjections` — balance iteration and yearly compounding converted to BigDecimal (`MathContext(20)`), replacing the `double` loop and the imprecise `BigDecimal.valueOf(annualRate/100.0)`; removed an unused `monthlyRate`. Added 2 characterization tests (exact annual projections, linear 0%-return months).
- `YahooFinanceProvider.getBigDecimal` — `node.decimalValue()` (reads the JSON number directly) instead of `BigDecimal.valueOf(node.asDouble())`.
- `entity/Asset` — salvage `0.10` extracted to a string-constructed `SALVAGE_RATE` constant; condition adjustment now multiplies by `AssetCondition.getValueRetentionRate()` (new exact-BigDecimal method) instead of `BigDecimal.valueOf(double)`.
- `DashboardService` — DTI `0.40` extracted to `DTI_MAX_RATIO = new BigDecimal("0.40")`; `computePeriodFraction` rewritten with exact BigDecimal division (no `1.0/12`, `1.0/365`).

> **Scope correction:** the original audit bullet above under-scoped this theme. A follow-up sweep found client-side `number` (IEEE-754 double) monetary math is **app-wide on the frontend** (~60–80 sites), not just the two calculator utils. Because this is a financial app requiring **maximum precision**, the items below are **required**, not optional.

**⏳ Remaining work (prioritized for maximum precision):**

**P0 — Persisted float → system of record (data integrity, not just display): ✅ FIXED & VERIFIED**
- **Correction to the original plan below:** the backend already has a correct, precise guard — `TransactionSplitService.validateSplits()` sums split amounts in `BigDecimal` (scale 4) and compares against the parent total via `SplitValidationConstants.SPLIT_SUM_TOLERANCE` (`±0.01`). That tolerance is an **intentional, documented business rule** (`REQ-SPL-1.2`), not a float workaround — it absorbs legitimate fiat non-divisibility (e.g. a 3-way split of `100.00` → `33.33 + 33.33 + 33.33 = 99.99`). "Require exact sum-to-total" (as originally proposed) would have been **wrong** and broken that legitimate case. No backend change was needed or made.
- The real, narrower bug was purely on the **frontend**: `components/transactions/SplitTransactionForm.tsx` computed the running sum with a plain `reduce` and auto-filled the remainder via `Math.round(remaining * 100) / 100` — the classic IEEE-754 rounding-boundary bug (`Math.round(1.005 * 100) / 100 === 1`, not `1.01`, because `1.005 * 100 === 100.49999999999999`; `.toFixed(2)` has the identical bug). This auto-filled value is what gets written to the API via `TransactionForm.tsx` → `useCreateTransaction`/`useUpdateTransaction` (`POST/PUT /transactions`).
- **Fix (TDD):** added `utils/decimalMath.ts` — dependency-free, integer-minor-units arithmetic (`toMinorUnits`/`fromMinorUnits`/`roundToDecimals`/`sumToDecimals`) mirroring the backend's `BigDecimal`/`RoundingMode.HALF_UP` (round-half-away-from-zero) convention. 14 unit tests written first (RED), then implemented (GREEN). `SplitTransactionForm` now sums splits and rounds the remainder through these helpers instead of raw float math; the ±0.01 tolerance check itself now compares exact integer minor units instead of a float epsilon, so it can never diverge from the backend's BigDecimal-scale comparison. Added a regression test reproducing the `1.005 → 1.01` bug against the *old* code (confirmed RED), then confirmed it passes post-fix (GREEN) along with all 34 pre-existing tests (incl. the ±0.01-tolerance test, unchanged). *(Note: `decimalMath.ts` was later consolidated into the fuller `utils/money.ts` foundation built in P2 — see below — and deleted rather than kept as a second, divergent module.)*
- **Verified:** frontend suite 242 files / **3268 tests pass** (3253 + 15 new), `type-check` clean, ESLint 0 errors.

**P1 — Backend rate/percentage DTOs → BigDecimal (Category C, changes calculator JSON API): ✅ FIXED & VERIFIED**
- `dto/calculator/SensitivityScenario.returnRate`, `dto/calculator/ProjectionResult.progressTowardTarget`, `dto/calculator/FreedomCalculatorResponse.progressPercentage`, and all rate fields in `dto/calculator/CalculationDefaults` — converted `double` → `BigDecimal`.
- `FinancialFreedomService` — removed the remaining `double` on the rate path: `generateSensitivityScenarios`/`createScenario` now take/return `BigDecimal`; the `-5.0 / 2.0 / 15.0` sensitivity-rate bounds extracted as named `BigDecimal` constants (`SENSITIVITY_RATE_DELTA`, `PESSIMISTIC_RATE_FLOOR`, `OPTIMISTIC_RATE_CEILING`); `progressPercentage` (`calculateTimeToFreedom`) and `progressTowardTarget` (`generateProjections`) now stay `BigDecimal` end-to-end (`.min(HUNDRED)` instead of `Math.min(x, 100.0)`) instead of round-tripping through `.doubleValue()`.
- **No frontend type changes needed:** verified Jackson serializes `BigDecimal` as a plain JSON number in this app (confirmed via the pre-existing `BigDecimal` fields like `targetSavingsAmount`, already typed `number` on the frontend) — `types/calculator.ts` is unaffected, so the earlier plan to update it was unnecessary.
- TDD: extended `FinancialFreedomServiceTest` with BigDecimal-`compareTo`-based assertions first (confirmed RED — 8 compile errors against the still-`double` DTOs), then converted the DTOs + service (confirmed GREEN). Added 3 new characterization tests pinning previously-untested behavior: exact `progressPercentage`/scenario `returnRate` values, the pessimistic floor (-5%), and the optimistic ceiling (15%).
- **Verified:** full backend `mvn test` → **2381 pass, 0 fail, 0 err** (5 pre-existing skips); `spotless:apply` clean.

**P2 — Frontend decimal foundation (enabler; none exists today): ✅ FIXED & VERIFIED**
- Added `decimal.js` (`^10.6.0`) as a direct dependency and a single consolidated `utils/money.ts` — the sole shared source of truth for precise decimal math, exporting `add`/`subtract`/`multiply`/`divide`/`sum`/`percentage`/`roundToDecimals`/`sumToDecimals`/`toMinorUnits`/`fromMinorUnits`. Every function takes and returns plain `number` (converting to/from `Decimal` only inside the function body), so call sites never need to learn the `decimal.js` API. Rounding configured to half-away-from-zero to match the backend's `BigDecimal`/`RoundingMode.HALF_UP` convention.
- P0's `decimalMath.ts` (written before this library was chosen) was consolidated into `money.ts` and deleted, rather than left as a second, divergent module — matching the report's own critique elsewhere of "two divergent apiClient files."
- **Critical foundation bug found and fixed during rollout (TDD):** raw `decimal.js` **throws** a `DecimalError` on `null`/`undefined` input (`new Decimal(undefined)`), whereas native `+`/`-`/`*`//` silently propagate `NaN`. A throw inside a `.map()`/`.reduce()` callback during a React render crashes the component — strictly worse than the pre-refactor behavior. Discovered when a P3 batch agent hit this while converting `AssetsPage.tsx`. Fixed at the foundation (not per call site): `toDecimal()` now converts missing/invalid input to `Decimal(NaN)`, which propagates through every operation exactly like native `NaN` does, keeping data-quality problems visible in the UI instead of silently substituting a possibly-misleading `0` in a financial app. 9 new tests added first (confirmed RED against the un-fixed module), then fixed (GREEN). All function signatures widened to accept `number | string | Decimal | null | undefined`.
- **Verified:** 30 unit tests for `money.ts` (21 original + 9 null/undefined-safety); ESLint clean on the new files.

**P3 — Frontend main-app aggregations & FX products: ✅ FIXED & VERIFIED**
- Converted ~65 client-side arithmetic sites across 26 files to `money.ts` (replacing raw `.reduce`/`+`/`-`/`*`/`/` on monetary values): `utils/portfolio.ts`, `pages/AssetsPage.tsx`, `pages/AccountsPage.tsx`, `pages/RealEstatePage.tsx`, `pages/DashboardPage.tsx`, `pages/CategoriesPage.tsx` (no change needed — its arithmetic lives in `useSecondaryConversion`, already covered), `components/assets/{PhysicalAssetCard,AssetList,AssetPerformanceTable,AssetDetailModal}.tsx`, `components/accounts/{AccountForm,AccountCard}.tsx`, `components/dashboard/{CurrencyBreakdown,InstitutionBreakdown,CashflowSankeyCard,BalanceVariationCard,NetWorthTrendChart,DailyCashFlowCalendar}.tsx`, `components/liabilities/{LiabilitySummaryCards,LiabilityBreakdownPanel,LiabilityList}.tsx`, `hooks/useLiabilities.ts` (incl. `calculateTotalInterest`), `components/real-estate/PropertyDetailView.tsx`, `components/budgets/BudgetSummaryCard.tsx`, `hooks/useUserFinancialData.ts`, `hooks/{useCurrency,useSecondaryConversion}.ts`, `components/ui/ExchangeRateDisplay.tsx`, `components/transactions/{RecurringTransactionCard,RecurringTransactionDetailModal,RecurringTransactionForm}.tsx`.
- Executed as 4 parallel agents by independent feature domain (assets/portfolio; accounts/dashboard; liabilities/real-estate; budgets/scattered FX), each with an exact, pre-classified site list (derived from a prior read-only exploration distinguishing genuine client-side derivation from display-only formatting of already-backend-computed values, which was correctly left untouched, e.g. `asset.gainPercentage`, `roi.totalROI`).
- Left untouched by design: backend-provided percentage/ROI fields merely `.toFixed()`-formatted for display; SVG/pixel layout math (bar-heights, fan-slice geometry) that only looks like a "sum" but isn't monetary; the calculator tools (P4, deferred).
- **Verified:** full frontend suite **3284 tests pass, 0 fail** (242 files); `type-check` clean; ESLint 0 errors (both `money.ts` and `money.test.ts` fully clean, no new warnings introduced). No test assertions needed correcting — all pre-existing fixtures used clean values, so the precision fix was purely internal.

> **Follow-up sites — ✅ FIXED & VERIFIED:** 6 sites of the same class of issue, surfaced incidentally while executing P3 but outside its originally-enumerated scope (flagged transparently by the executing agents rather than silently left half-done), have since been converted: `AssetPerformanceTable.tsx` (`purchasePrice`/`currentPrice` × exchangeRate, 2 sites), `AssetDetailModal.tsx` (`currentPrice`/`purchasePrice` × exchangeRate ×4, plus 1 chart-data site — 5 sites), `LiabilityBreakdownPanel.tsx`'s `BreakdownRow` inline FX conversion (1 site, separate from the already-fixed `toBase` helper), `LiabilityDetailDialog.tsx` (2 sites — both file-local `toBase` helpers), `PropertyCard.tsx` (3 sites: purchasePrice, appreciation, equity). The 6th flagged item — "a second `.reduce` in `AssetsPage.tsx`" — turned out to be a stale observation from a parallel batch race (that file's `computeAssetSummary` had already been fully converted by a sibling batch); verified via full-file read, no action needed. A repo-wide sweep (`* exchangeRate` / `exchangeRate *`) after these fixes confirms **zero remaining raw FX-product sites** outside the deferred P4 calculator tools. Verified: `type-check` clean, full frontend suite **3284/3284 pass** (unchanged count — no assertions needed correcting), ESLint 0 errors (no new warnings).

**P4 — Frontend calculator tools (deferred — display-only what-if; ~2,300 LOC + ~9 exact-assertion test suites to re-derive):**
- `utils/financialCalculations.ts`, `utils/realEstateCalculations.ts`, `utils/taxRegimeCalculations.ts`, `services/realEstateCalculationService.ts`, `hooks/useLoanCalculator.ts`, `hooks/useEarlyPayoffCalculator.ts`, `hooks/useFinancialFreedom.ts` (local path), `components/accounts/InterestRateVariationsSection.tsx` (`calcPeriodInterest`) and `components/accounts/AccountForm.tsx`'s compound-interest preview formula (intentionally left alone in P3). Convert to `money.ts`; kill the `Math.round(x*100)/100` sites (6 in `realEstateCalculations.ts`) which mis-round `.xx5` boundaries. The financial-freedom UI uses the local float path — align it with (or route it through) the now-BigDecimal backend endpoint to eliminate frontend↔backend divergence.

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


## LOW Severity (highlights) ✅ FIXED & VERIFIED (8 of 11 real; 3 stale/already-resolved)

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

**✅ Resolution:** Re-verified each item against the current codebase (much has shifted since this audit was written) before fixing. **3 items were stale** — already resolved by earlier, unrelated changes, or inaccurate at time of writing — and are called out explicitly rather than silently skipped:

1. **`FinancialFreedomService` €/double format — FIXED.** No currency context exists at this service layer (the request carries only raw amounts), so hardcoding "€" was actively wrong for non-EUR users, not just imprecise. Removed the symbol entirely (caller/frontend pairs the figure with the user's real currency) and replaced `String.format("€%,.2f", amount.doubleValue())` with `NumberFormat.getNumberInstance(Locale.US)` operating on the `BigDecimal` directly (no `.doubleValue()` round-trip). Added a characterization test for the "not achievable" message path (previously untested) — this took a long debugging detour due to repeatedly misreading *which* `assertFalse(...)` was failing in a multi-assertion test (both `achievable` and the `€`-check assertions produce an identical `expected: <false> but was: <true>` signature); resolved by reading exact stack-trace line numbers from the raw Surefire report rather than piped/grepped console output, which was unreliable in this shell for this specific investigation.
2. **`DateTimeUtil` locale-aware formatters — FIXED.** Had zero production callers and zero tests (confirmed via search), so this was a pure hardening exercise, not a live bug. Pinned the existing `DISPLAY_DATE_FORMATTER`/`DISPLAY_DATE_TIME_FORMATTER` to `Locale.ENGLISH` explicitly (removing the implicit JVM-default-locale dependency) and added `formatDateForDisplay(date, locale)` / `formatDateTimeForDisplay(dateTime, locale)` overloads for callers that need the user's actual locale (e.g. French month abbreviations). 6 new tests.
3. **`RssService` — FIXED.** Was constructing a `new RestTemplate()` on every single call to `getFinanceFeeds`. Added a dedicated `rssRestTemplate` bean (`config/RssConfig.java`, mirroring the existing `LogoFetchConfig` pattern) injected via constructor, so one instance is reused for the service's lifetime. Extracted the hardcoded Chrome User-Agent string into a named `RssService.USER_AGENT` constant. 2 new tests (RestTemplate reuse, User-Agent header sent).
4. **`Backup`/`Attachment` duplicate file-size formatters — FIXED.** Extracted a single shared `util/FileSizeFormatter` (2-decimal precision at every tier, "bytes" suffix) used by both entities, eliminating the inconsistency (`Backup` was already internally consistent at 2 decimals; `Attachment` mixed 1 decimal for KB/MB with 2 for GB). Updated `AttachmentTest`'s 4 affected assertions from 1-decimal to 2-decimal patterns (necessary and expected — that's the actual fix). 5 new tests for the extracted formatter; `BackupRepositoryTest`'s existing 2-decimal assertion needed no change.
5. **`OllamaClient`/`OpenAIProvider` magic number — FIXED.** Extracted the duplicated `10 * 1024 * 1024` WebClient buffer size into a shared `AIWebClientDefaults.MAX_IN_MEMORY_SIZE_BYTES` constant in `service.ai`, so the two providers' buffer sizes can never silently drift apart.
6. **`application-railway.yml` hardcoded URL — STALE, nothing to fix.** This file no longer exists in the repository (confirmed via filesystem search and `git log`); there is no `railway.toml` either. Railway deployment now appears to rely on environment variables / the `pom.xml` `railway` Maven profile alone, with no separate hardcoded-URL config file.
7. **`RepairRunner`/`TestDataSeeder` magic numbers — partially stale.** `RepairRunner.java`'s actual repair logic (`repairForUser` and callees) is unreachable dead code — `run()` returns immediately after logging a deprecation warning — and contains **no** `0.10`/`365`/`12` magic numbers at all; the report's claim no longer matches the file's content. `TestDataSeeder.java` did have real (if low-stakes, seed-data-only) `12`/`365` magic numbers — extracted as `MONTHS_PER_YEAR`/`SEED_TRANSACTION_LOOKBACK_DAYS` constants.
8. **`InsightService` 24 `.doubleValue()` calls — FIXED.** Verified first (by reading the actual `.properties` files) that every affected message pattern uses explicit `{n,number,#.##}`-style `MessageFormat` tokens, which render any `Number` subtype (including `BigDecimal`) identically — so removing `.doubleValue()` and passing the `BigDecimal` directly is a pure precision improvement with **zero visible message-text change**. Fixed all 24 sites (one string-concatenation fallback-message site used `.toPlainString()` instead, to guarantee no scientific-notation risk). Deliberately left untouched: `UnusualTransactionDetectionService.java`'s `.doubleValue()` calls (legitimate statistical mean/stddev/z-score math, not monetary formatting, and not part of this report item) and `FinancialFreedomService.java:410`'s zero-comparison (not a formatting concern).
9. **Frontend `CompoundInterestCalculator.tsx` — FIXED.** Converted the 3 genuine `Number(x.toFixed(2))` re-conversion sites (chart data: principal/contributions/interest) to `roundToDecimals()` from the established `utils/money` foundation, and also fixed the raw subtraction feeding into them (`row.cumulativePrincipal - initPrincipal` → `subtract(...)`) for full derivation-chain precision. Line 268 (`result.effectiveAnnualRate.toFixed(2)`) — named in the original audit bullet — was **not** touched: it's pure terminal display formatting (renders directly to a string, never re-enters numeric computation), not the "re-introduces float error" pattern the item describes.
10. **Scheduler timezone divergence — STALE, nothing to fix.** Searched for `Europe/Paris` as a scheduler default anywhere in the codebase — the only matches are Javadoc examples and frontend test fixtures (a French user's `timezone` *setting*, unrelated to schedulers). `application.yml`'s `scheduled.market-hours.zone` and `MarketDataScheduler.DEFAULT_ZONE` both already read `America/New_York`, consistently.
11. **QIF parser "hardcoded dates" — FIXED (at the root cause).** The tests themselves are safe (`LocalDate.of(year, month, day)` literals — inherently locale-independent). The real, if largely theoretical, risk was in **production code**: `QifParser.java`'s 13 numeric `DateTimeFormatter.ofPattern(...)` array entries used the JVM-default-locale implicitly. Pinned all 13 to `Locale.ROOT` explicitly, which is the standard hardening recommendation for `DateTimeFormatter` construction (never rely on implicit default locale), removing the "may behave differently on non-US locale JVMs" risk at its source rather than in the test file.

**Verified:** full backend `mvn test` → **2395 pass, 0 fail, 0 err** (5 pre-existing skips, +14 new tests this pass); `spotless:apply` clean. Full frontend suite → **3284/3284 pass** (one `useInstitutions` flake in the full parallel run reproduced as a pre-existing, unrelated CPU-contention flake — confirmed 12/12 passing in isolation, and confirmed zero working-tree changes to that file); `type-check` clean; ESLint 0 errors, no new warnings.

---

