package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetPeriod;
import org.openfinance.entity.Category;
import org.openfinance.entity.Institution;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityType;
import org.openfinance.entity.PropertyType;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.RecurringFrequency;
import org.openfinance.entity.RecurringTransaction;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.InstitutionRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.RecurringTransactionRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.KeyManagementService;
import org.openfinance.security.PasswordService;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a complete, realistic live-demo dataset for the Open-Finance demo
 * environment.
 *
 * <p>
 * This bean is only instantiated when
 * {@code application.live-demo.enabled=true} is set, so it
 * has no impact on production or test environments unless explicitly enabled.
 *
 * <p>
 * The demo persona is <em>Sophie Martin</em>, a 34-year-old software engineer
 * based in Paris,
 * France, with a diversified portfolio, two real-estate properties, realistic
 * recurring expenses
 * and ~14 months of transaction history.
 *
 * <p>
 * Credential defaults (override in application.yml or env vars if desired):
 *
 * <ul>
 * <li>Username: {@code demo}
 * <li>Password: {@code demo123}
 * <li>Master password: {@code master123}
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "application.live-demo.enabled", havingValue = "true", matchIfMissing = false)
public class LiveDemoData implements CommandLineRunner {

        // ── Repositories ──────────────────────────────────────────────────────────
        private final UserRepository userRepository;
        private final CategoryRepository categoryRepository;
        private final AccountRepository accountRepository;
        private final AssetRepository assetRepository;
        private final LiabilityRepository liabilityRepository;
        private final TransactionRepository transactionRepository;
        private final BudgetRepository budgetRepository;
        private final InstitutionRepository institutionRepository;
        private final RecurringTransactionRepository recurringTransactionRepository;
        private final RealEstateRepository realEstateRepository;

        // ── Services ──────────────────────────────────────────────────────────────
        private final PasswordService passwordService;
        private final KeyManagementService keyManagementService;
        private final CategorySeeder categorySeeder;
        private final JdbcTemplate jdbcTemplate;
        private final InstitutionSeeder institutionSeeder;
        private final EncryptionKeyCache encryptionKeyCache;
        private final SearchTokenService searchTokenService;

        // ── Demo credentials ──────────────────────────────────────────────────────
        private static final String DEMO_USERNAME = "demo";
        private static final String DEMO_PASSWORD = "demo123";
        private static final String DEMO_MASTER_PASSWORD = "master123";

        // ─────────────────────────────────────────────────────────────────────────

        @Override
        public void run(String... args) throws Exception {
                log.info("Live demo mode enabled — checking whether seeding is needed...");
                seedDemoData();
        }

        @Transactional
        public void seedDemoData() {
                if (userRepository.findByUsername(DEMO_USERNAME).isPresent()) {
                        log.info("Demo user '{}' already exists — skipping seed.", DEMO_USERNAME);
                        return;
                }

                log.info("Seeding live demo data for user '{}'...", DEMO_USERNAME);

                // ── User ──────────────────────────────────────────────────────────────
                byte[] salt = keyManagementService.generateSalt();
                String saltBase64 = Base64.getEncoder().encodeToString(salt);
                String passwordHash = passwordService.hashPassword(DEMO_PASSWORD);

                User user = User.builder()
                                .username(DEMO_USERNAME)
                                .email("demo@openfinance.app")
                                .passwordHash(passwordHash)
                                .masterPasswordSalt(saltBase64)
                                .createdAt(LocalDateTime.now())
                                .build();
                user = userRepository.save(user);
                Long userId = user.getId();

                // ── Derive encryption key and set context for JPA converters ──────────
                SecretKey encryptionKey = keyManagementService.deriveKey(
                                DEMO_MASTER_PASSWORD.toCharArray(), salt);
                EncryptionContext.setKey(encryptionKey);
                encryptionKeyCache.cacheKey(userId, encryptionKey);

                try {
                        // ── Shared seeders ────────────────────────────────────────────
                        institutionSeeder.seedDefaultInstitutions();
                        categorySeeder.seedDefaultCategories(userId);

                        // ── Domain data ───────────────────────────────────────────────
                        List<Account> accounts = seedAccounts(userId);
                        seedAssets(userId);
                        seedLiabilities(userId);
                        seedRealEstate(userId);
                        seedRecurringTransactions(userId, accounts);
                        seedTransactions(userId, accounts);
                        seedBudgets(userId);

                        // ── Generate search tokens for all demo data ──────────────────
                        generateSearchTokens(userId, encryptionKey, accounts);
                } finally {
                        EncryptionContext.clear();
                }

                log.info("Live demo data seeding completed for user '{}'.", DEMO_USERNAME);
        }

        // ── Convenience: identity pass-through (JPA converters handle encryption) ─

        private String enc(String value) {
                return value;
        }

        // ── Accounts ──────────────────────────────────────────────────────────────

        /**
         * Returns accounts in a stable order so downstream methods can reference them
         * by index without
         * decrypting names.
         *
         * <p>
         * Index contract:
         *
         * <pre>
         *  0 – Main Checking (BNP Paribas, EUR)
         *  1 – Livret A       (LCL, EUR)
         *  2 – PEA Boursorama (Boursorama, EUR)
         *  3 – Joint Account  (Crédit Agricole, EUR)
         *  4 – Revolut Travel (Revolut, USD)
         *  5 – Cash Wallet    (none, EUR)
         * </pre>
         */
        private List<Account> seedAccounts(Long userId) {
                List<Institution> all = institutionRepository.findAll();

                Institution bnp = findInstitution(all, "BNP Paribas");
                Institution lcl = findInstitution(all, "LCL");
                Institution boursorama = findInstitution(all, "Boursorama");
                Institution ca = findInstitution(all, "Crédit Agricole");
                Institution revolut = findInstitution(all, "Revolut");

                List<Account> accounts = new ArrayList<>();
                // 0 – main day-to-day
                accounts.add(
                                buildAccount(
                                                userId,
                                                "Main Checking",
                                                AccountType.CHECKING,
                                                "3847.52",
                                                "EUR",
                                                "FR76 3000 4008 0900 0001 2345 678",
                                                bnp));
                // 1 – French regulated savings (Livret A, rate 3 %)
                accounts.add(
                                buildAccount(
                                                userId,
                                                "Livret A",
                                                AccountType.SAVINGS,
                                                "22300.00",
                                                "EUR",
                                                "FR76 3004 2005 0000 0567 8901 234",
                                                lcl));
                // 2 – EU equity savings plan
                accounts.add(
                                buildAccount(
                                                userId,
                                                "PEA Boursorama",
                                                AccountType.INVESTMENT,
                                                "48200.00",
                                                "EUR",
                                                "FR76 4061 0007 0000 0123 4567 890",
                                                boursorama));
                // 3 – shared account with partner
                accounts.add(
                                buildAccount(
                                                userId,
                                                "Joint Account",
                                                AccountType.CHECKING,
                                                "1250.75",
                                                "EUR",
                                                "FR76 1820 6004 8060 0098 7654 321",
                                                ca));
                // 4 – travel & multi-currency
                accounts.add(
                                buildAccount(
                                                userId,
                                                "Revolut Travel",
                                                AccountType.CHECKING,
                                                "2150.00",
                                                "USD",
                                                "GB29 NWBK 6016 1331 9268 19",
                                                revolut));
                // 5 – petty cash
                accounts.add(
                                buildAccount(userId, "Cash Wallet", AccountType.CASH, "350.00", "EUR", null, null));

                return accountRepository.saveAll(accounts);
        }

        private Account buildAccount(
                        Long userId,
                        String name,
                        AccountType type,
                        String balance,
                        String currency,
                        String accountNumber,
                        Institution institution) {
                return Account.builder()
                                .userId(userId)
                                .name(enc(name))
                                .type(type)
                                .institution(institution)
                                .balance(new BigDecimal(balance))
                                .currency(currency)
                                .accountNumber(accountNumber != null ? enc(accountNumber) : null)
                                .isActive(true)
                                .createdAt(LocalDateTime.now())
                                .build();
        }

        private Institution findInstitution(List<Institution> institutions, String name) {
                return institutions.stream().filter(i -> name.equals(i.getName())).findFirst().orElse(null);
        }

        // ── Assets ────────────────────────────────────────────────────────────────

        private void seedAssets(Long userId) {
                List<Asset> assets = new ArrayList<>();

                // ── US equities (held in PEA-compatible wrappers or directly via Revolut) ──
                assets.add(
                                buildAsset(
                                                userId,
                                                "Apple Inc.",
                                                AssetType.STOCK,
                                                "167.50",
                                                "227.00",
                                                "USD",
                                                "AAPL",
                                                "45",
                                                LocalDate.of(2022, 3, 15)));
                assets.add(
                                buildAsset(
                                                userId,
                                                "Microsoft Corporation",
                                                AssetType.STOCK,
                                                "280.00",
                                                "415.00",
                                                "USD",
                                                "MSFT",
                                                "25",
                                                LocalDate.of(2021, 11, 8)));
                assets.add(
                                buildAsset(
                                                userId,
                                                "NVIDIA Corporation",
                                                AssetType.STOCK,
                                                "450.00",
                                                "900.00",
                                                "USD",
                                                "NVDA",
                                                "15",
                                                LocalDate.of(2023, 2, 20)));

                // ── French / European blue-chips ──────────────────────────────────────
                assets.add(
                                buildAsset(
                                                userId,
                                                "LVMH Moët Hennessy Louis Vuitton",
                                                AssetType.STOCK,
                                                "650.00",
                                                "630.00",
                                                "EUR",
                                                "MC.PA",
                                                "8",
                                                LocalDate.of(2021, 6, 14)));
                assets.add(
                                buildAsset(
                                                userId,
                                                "TotalEnergies SE",
                                                AssetType.STOCK,
                                                "58.50",
                                                "62.30",
                                                "EUR",
                                                "TTE.PA",
                                                "100",
                                                LocalDate.of(2020, 9, 5)));
                assets.add(
                                buildAsset(
                                                userId,
                                                "BNP Paribas SA",
                                                AssetType.STOCK,
                                                "55.00",
                                                "72.00",
                                                "EUR",
                                                "BNP.PA",
                                                "50",
                                                LocalDate.of(2022, 1, 18)));
                assets.add(
                                buildAsset(
                                                userId,
                                                "Airbus SE",
                                                AssetType.STOCK,
                                                "115.00",
                                                "162.00",
                                                "EUR",
                                                "AIR.PA",
                                                "20",
                                                LocalDate.of(2023, 5, 10)));

                // ── EU-domiciled ETFs held in PEA ─────────────────────────────────────
                assets.add(
                                buildAsset(
                                                userId,
                                                "Amundi MSCI World UCITS ETF Acc",
                                                AssetType.ETF,
                                                "52.00",
                                                "71.50",
                                                "EUR",
                                                "CW8.PA",
                                                "150",
                                                LocalDate.of(2020, 4, 22)));
                assets.add(
                                buildAsset(
                                                userId,
                                                "iShares Core MSCI Europe UCITS ETF",
                                                AssetType.ETF,
                                                "65.00",
                                                "78.00",
                                                "EUR",
                                                "IMAE.AS",
                                                "80",
                                                LocalDate.of(2021, 3, 30)));
                assets.add(
                                buildAsset(
                                                userId,
                                                "Lyxor CAC 40 UCITS ETF",
                                                AssetType.ETF,
                                                "22.50",
                                                "28.40",
                                                "EUR",
                                                "CAC.PA",
                                                "200",
                                                LocalDate.of(2020, 7, 15)));

                // ── Crypto ────────────────────────────────────────────────────────────
                assets.add(
                                buildAsset(
                                                userId,
                                                "Bitcoin",
                                                AssetType.CRYPTO,
                                                "35000.00",
                                                "88000.00",
                                                "USD",
                                                "BTC-USD",
                                                "0.15",
                                                LocalDate.of(2021, 1, 10)));
                assets.add(
                                buildAsset(
                                                userId,
                                                "Ethereum",
                                                AssetType.CRYPTO,
                                                "2000.00",
                                                "2500.00",
                                                "USD",
                                                "ETH-USD",
                                                "1.5",
                                                LocalDate.of(2021, 8, 25)));

                // ── Commodities (physical gold ETC) ───────────────────────────────────
                assets.add(
                                buildAsset(
                                                userId,
                                                "iShares Physical Gold ETC",
                                                AssetType.COMMODITY,
                                                "28.50",
                                                "37.80",
                                                "USD",
                                                "IGLN.AS",
                                                "50",
                                                LocalDate.of(2022, 6, 1)));

                assetRepository.saveAll(assets);
        }

        private Asset buildAsset(
                        Long userId,
                        String name,
                        AssetType type,
                        String purchasePrice,
                        String currentPrice,
                        String currency,
                        String symbol,
                        String quantity,
                        LocalDate purchaseDate) {
                return Asset.builder()
                                .userId(userId)
                                .name(enc(name))
                                .type(type)
                                .symbol(symbol)
                                .quantity(new BigDecimal(quantity))
                                .purchasePrice(new BigDecimal(purchasePrice))
                                .currentPrice(new BigDecimal(currentPrice))
                                .currency(currency)
                                .purchaseDate(purchaseDate)
                                .createdAt(LocalDateTime.now())
                                .build();
        }

        // ── Liabilities ───────────────────────────────────────────────────────────

        private void seedLiabilities(Long userId) {
                List<Liability> liabilities = new ArrayList<>();

                // Primary residence mortgage — 20-year loan at a 2020 fixed rate
                Liability mortgage = new Liability();
                mortgage.setUserId(userId);
                mortgage.setName(enc("Home Mortgage — BNP Paribas"));
                mortgage.setType(LiabilityType.MORTGAGE);
                mortgage.setPrincipal(enc("280000.00"));
                mortgage.setCurrentBalance(enc("241500.00"));
                mortgage.setInterestRate(enc("1.15"));
                mortgage.setMinimumPayment(null);
                mortgage.setCurrency("EUR");
                mortgage.setStartDate(LocalDate.of(2020, 3, 10));
                liabilities.add(mortgage);

                // 4-year car loan — Renault Mégane E-Tech
                Liability carLoan = new Liability();
                carLoan.setUserId(userId);
                carLoan.setName(enc("Car Loan — Renault Mégane E-Tech"));
                carLoan.setType(LiabilityType.LOAN);
                carLoan.setPrincipal(enc("18500.00"));
                carLoan.setCurrentBalance(enc("8750.00"));
                carLoan.setInterestRate(enc("3.9"));
                carLoan.setMinimumPayment(null);
                carLoan.setCurrency("EUR");
                carLoan.setStartDate(LocalDate.of(2022, 6, 1));
                liabilities.add(carLoan);

                // Revolving credit card — used for travel and online purchases
                Liability creditCard = new Liability();
                creditCard.setUserId(userId);
                creditCard.setName(enc("BNP Paribas Visa Premier"));
                creditCard.setType(LiabilityType.CREDIT_CARD);
                creditCard.setPrincipal(enc("5000.00"));
                creditCard.setCurrentBalance(enc("1240.50"));
                creditCard.setInterestRate(enc("18.5"));
                creditCard.setMinimumPayment(null);
                creditCard.setCurrency("EUR");
                creditCard.setStartDate(LocalDate.of(2019, 5, 1));
                liabilities.add(creditCard);

                liabilityRepository.saveAll(liabilities);
        }

        // ── Real estate ───────────────────────────────────────────────────────────

        private void seedRealEstate(Long userId) {
                List<RealEstateProperty> properties = new ArrayList<>();

                // Primary residence — 2-bed apartment in Paris 11th arrondissement
                properties.add(
                                RealEstateProperty.builder()
                                                .userId(userId)
                                                .name(enc("Apartment — Paris 75011 (Primary Residence)"))
                                                .address(enc("24 Rue de la Roquette, 75011 Paris"))
                                                .propertyType(PropertyType.RESIDENTIAL)
                                                .purchasePrice(enc("320000"))
                                                .currentValue(enc("385000"))
                                                .currency("EUR")
                                                .purchaseDate(LocalDate.of(2020, 3, 15))
                                                .latitude(BigDecimal.valueOf(48.854200).setScale(6,
                                                                RoundingMode.HALF_UP))
                                                .longitude(BigDecimal.valueOf(2.374200).setScale(6,
                                                                RoundingMode.HALF_UP))
                                                .notes(
                                                                enc(
                                                                                "2-bedroom, 58 m², Haussmann-era building. Mortgage via BNP Paribas at 1.15 % fixed for 20 years."))
                                                .isActive(true)
                                                .build());

                // Buy-to-let studio in Lyon — generates €650/month rental income
                properties.add(
                                RealEstateProperty.builder()
                                                .userId(userId)
                                                .name(enc("Studio — Lyon 69003 (Rental Property)"))
                                                .address(enc("15 Rue Garibaldi, 69003 Lyon"))
                                                .propertyType(PropertyType.RESIDENTIAL)
                                                .purchasePrice(enc("145000"))
                                                .currentValue(enc("168000"))
                                                .currency("EUR")
                                                .purchaseDate(LocalDate.of(2018, 6, 10))
                                                .latitude(BigDecimal.valueOf(45.752000).setScale(6,
                                                                RoundingMode.HALF_UP))
                                                .longitude(BigDecimal.valueOf(4.840000).setScale(6,
                                                                RoundingMode.HALF_UP))
                                                .notes(
                                                                enc(
                                                                                "32 m² studio, fully furnished. Currently rented at €650/month. Managed via Foncia agency."))
                                                .isActive(true)
                                                .build());

                realEstateRepository.saveAll(properties);
        }

        // ── Recurring transactions ────────────────────────────────────────────────

        private void seedRecurringTransactions(Long userId, List<Account> accounts) {
                Account main = accounts.get(0); // Main Checking
                Account joint = accounts.get(3); // Joint Account

                List<Category> cats = categoryRepository.findByUserId(userId);
                LocalDate today = LocalDate.now();

                List<RecurringTransaction> list = new ArrayList<>();

                // ── Income ────────────────────────────────────────────────────────────
                list.add(
                                buildRecurring(
                                                userId,
                                                main.getId(),
                                                TransactionType.INCOME,
                                                "4200.00",
                                                "EUR",
                                                cat(cats, "Salary"),
                                                "Tech Corp Paris — Net Monthly Salary",
                                                RecurringFrequency.MONTHLY,
                                                today.withDayOfMonth(1)));

                list.add(
                                buildRecurring(
                                                userId,
                                                main.getId(),
                                                TransactionType.INCOME,
                                                "650.00",
                                                "EUR",
                                                cat(cats, "Residential Rent"),
                                                "Lyon Studio — Rental Income",
                                                RecurringFrequency.MONTHLY,
                                                today.withDayOfMonth(5)));

                // ── Housing ───────────────────────────────────────────────────────────
                list.add(
                                buildRecurring(
                                                userId,
                                                main.getId(),
                                                TransactionType.EXPENSE,
                                                "1050.00",
                                                "EUR",
                                                cat(cats, "Mortgage"),
                                                "BNP Paribas — Mortgage Payment",
                                                RecurringFrequency.MONTHLY,
                                                today.withDayOfMonth(5)));

                // ── Telecom & Internet ───────────────────────────────────────────────
                list.add(
                                buildRecurring(
                                                userId,
                                                main.getId(),
                                                TransactionType.EXPENSE,
                                                "9.99",
                                                "EUR",
                                                cat(cats, "Phone"),
                                                "Free Mobile — Monthly Plan",
                                                RecurringFrequency.MONTHLY,
                                                today.withDayOfMonth(3)));

                list.add(
                                buildRecurring(
                                                userId,
                                                main.getId(),
                                                TransactionType.EXPENSE,
                                                "29.99",
                                                "EUR",
                                                cat(cats, "Internet"),
                                                "Free Fibre — Home Internet",
                                                RecurringFrequency.MONTHLY,
                                                today.withDayOfMonth(3)));

                // ── Streaming & subscriptions ─────────────────────────────────────────
                list.add(
                                buildRecurring(
                                                userId,
                                                main.getId(),
                                                TransactionType.EXPENSE,
                                                "19.99",
                                                "EUR",
                                                cat(cats, "Streaming Services"),
                                                "Netflix",
                                                RecurringFrequency.MONTHLY,
                                                today.withDayOfMonth(10)));

                list.add(
                                buildRecurring(
                                                userId,
                                                main.getId(),
                                                TransactionType.EXPENSE,
                                                "9.99",
                                                "EUR",
                                                cat(cats, "Streaming Services"),
                                                "Spotify Premium",
                                                RecurringFrequency.MONTHLY,
                                                today.withDayOfMonth(12)));

                list.add(
                                buildRecurring(
                                                userId,
                                                main.getId(),
                                                TransactionType.EXPENSE,
                                                "6.99",
                                                "EUR",
                                                cat(cats, "Streaming Services"),
                                                "Amazon Prime",
                                                RecurringFrequency.MONTHLY,
                                                today.withDayOfMonth(18)));

                list.add(
                                buildRecurring(
                                                userId,
                                                main.getId(),
                                                TransactionType.EXPENSE,
                                                "8.99",
                                                "EUR",
                                                cat(cats, "Streaming Services"),
                                                "Disney+",
                                                RecurringFrequency.MONTHLY,
                                                today.withDayOfMonth(22)));

                list.add(
                                buildRecurring(
                                                userId,
                                                main.getId(),
                                                TransactionType.EXPENSE,
                                                "25.99",
                                                "EUR",
                                                cat(cats, "Cable TV"),
                                                "Canal+",
                                                RecurringFrequency.MONTHLY,
                                                today.withDayOfMonth(8)));

                // ── Utilities ─────────────────────────────────────────────────────────
                list.add(
                                buildRecurring(
                                                userId,
                                                main.getId(),
                                                TransactionType.EXPENSE,
                                                "95.20",
                                                "EUR",
                                                cat(cats, "Electricity"),
                                                "EDF — Electricity Direct Debit",
                                                RecurringFrequency.MONTHLY,
                                                today.withDayOfMonth(15)));

                // ── Insurance ─────────────────────────────────────────────────────────
                list.add(
                                buildRecurring(
                                                userId,
                                                joint.getId(),
                                                TransactionType.EXPENSE,
                                                "42.50",
                                                "EUR",
                                                cat(cats, "Home Insurance"),
                                                "MAIF — Home Insurance",
                                                RecurringFrequency.MONTHLY,
                                                today.withDayOfMonth(7)));

                list.add(
                                buildRecurring(
                                                userId,
                                                main.getId(),
                                                TransactionType.EXPENSE,
                                                "85.30",
                                                "EUR",
                                                cat(cats, "Auto Insurance"),
                                                "MAIF — Car Insurance",
                                                RecurringFrequency.MONTHLY,
                                                today.withDayOfMonth(7)));

                // ── Health & fitness ──────────────────────────────────────────────────
                list.add(
                                buildRecurring(
                                                userId,
                                                main.getId(),
                                                TransactionType.EXPENSE,
                                                "45.00",
                                                "EUR",
                                                cat(cats, "Gym/Fitness"),
                                                "SportCity Paris — Gym Membership",
                                                RecurringFrequency.MONTHLY,
                                                today.withDayOfMonth(2)));

                // ── Savings transfer ──────────────────────────────────────────────────
                list.add(
                                buildRecurring(
                                                userId,
                                                main.getId(),
                                                TransactionType.EXPENSE,
                                                "500.00",
                                                "EUR",
                                                cat(cats, "Savings"),
                                                "PEA — Monthly Investment Transfer",
                                                RecurringFrequency.MONTHLY,
                                                today.withDayOfMonth(2)));

                recurringTransactionRepository.saveAll(list);
        }

        private RecurringTransaction buildRecurring(
                        Long userId,
                        Long accountId,
                        TransactionType type,
                        String amount,
                        String currency,
                        Category category,
                        String description,
                        RecurringFrequency frequency,
                        LocalDate nextOccurrence) {
                return RecurringTransaction.builder()
                                .userId(userId)
                                .accountId(accountId)
                                .type(type)
                                .amount(new BigDecimal(amount))
                                .currency(currency)
                                .categoryId(category != null ? category.getId() : null)
                                .description(enc(description))
                                .frequency(frequency)
                                .nextOccurrence(nextOccurrence)
                                .isActive(true)
                                .build();
        }

        // ── Historical transactions ───────────────────────────────────────────────

        private void seedTransactions(Long userId, List<Account> accounts) {
                Account main = accounts.get(0); // Main Checking – EUR
                Account savings = accounts.get(1); // Livret A – EUR
                Account joint = accounts.get(3); // Joint Account – EUR

                List<Category> cats = categoryRepository.findByUserId(userId);
                if (cats.isEmpty())
                        return;

                LocalDate today = LocalDate.now();
                List<Transaction> txs = new ArrayList<>();

                // ── Monthly salary — last 14 months ───────────────────────────────────
                for (int m = 14; m >= 1; m--) {
                        LocalDate d = today.minusMonths(m).withDayOfMonth(1);
                        txs.add(
                                        tx(
                                                        userId,
                                                        main,
                                                        "4200.00",
                                                        "Tech Corp Paris — Net Salary",
                                                        TransactionType.INCOME,
                                                        cat(cats, "Salary"),
                                                        d));
                }

                // ── Annual performance bonus ───────────────────────────────────────────
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "2500.00",
                                                "Tech Corp Paris — Annual Performance Bonus",
                                                TransactionType.INCOME,
                                                cat(cats, "Bonus"),
                                                today.minusMonths(2).withDayOfMonth(28)));

                // ── Freelance consulting income ────────────────────────────────────────
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "800.00",
                                                "ProConseil SAS — UX Consulting Invoice #2025-047",
                                                TransactionType.INCOME,
                                                cat(cats, "Freelance Work"),
                                                today.minusMonths(10).withDayOfMonth(15)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "1200.00",
                                                "StartupLab — Mobile App Development Invoice #2025-112",
                                                TransactionType.INCOME,
                                                cat(cats, "Freelance Work"),
                                                today.minusMonths(5).withDayOfMonth(22)));

                // ── Rental income — last 14 months ────────────────────────────────────
                for (int m = 14; m >= 1; m--) {
                        LocalDate d = today.minusMonths(m).withDayOfMonth(5);
                        txs.add(
                                        tx(
                                                        userId,
                                                        main,
                                                        "650.00",
                                                        "Foncia Lyon — Rental Income Studio 69003",
                                                        TransactionType.INCOME,
                                                        cat(cats, "Residential Rent"),
                                                        d));
                }

                // ── Annual tax refund ─────────────────────────────────────────────────
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "347.80",
                                                "DGFiP — Income Tax Refund 2024",
                                                TransactionType.INCOME,
                                                cat(cats, "Government Benefits"),
                                                today.minusMonths(9).withDayOfMonth(18)));

                // ── Stock dividends (quarterly) ────────────────────────────────────────
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "180.00",
                                                "TotalEnergies SE — Dividend Payment Q2 2025",
                                                TransactionType.INCOME,
                                                cat(cats, "Dividends"),
                                                today.minusMonths(6).withDayOfMonth(10)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "95.50",
                                                "BNP Paribas SA — Dividend Payment Q2 2025",
                                                TransactionType.INCOME,
                                                cat(cats, "Dividends"),
                                                today.minusMonths(6).withDayOfMonth(12)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "52.30",
                                                "LVMH — Dividend Payment Q2 2025",
                                                TransactionType.INCOME,
                                                cat(cats, "Dividends"),
                                                today.minusMonths(6).withDayOfMonth(14)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "90.00",
                                                "TotalEnergies SE — Dividend Payment Q4 2025",
                                                TransactionType.INCOME,
                                                cat(cats, "Dividends"),
                                                today.minusMonths(2).withDayOfMonth(11)));

                // ── Savings interest ──────────────────────────────────────────────────
                txs.add(
                                tx(
                                                userId,
                                                savings,
                                                "669.00",
                                                "LCL Livret A — Annual Interest (3.0%)",
                                                TransactionType.INCOME,
                                                cat(cats, "Interest Income"),
                                                today.minusMonths(3).withDayOfMonth(1)));

                // ════════════════════════════════════════════════════════════════════
                // EXPENSES
                // ════════════════════════════════════════════════════════════════════

                // ── Mortgage — last 14 months ─────────────────────────────────────────
                for (int m = 14; m >= 1; m--) {
                        LocalDate d = today.minusMonths(m).withDayOfMonth(5);
                        txs.add(
                                        tx(
                                                        userId,
                                                        main,
                                                        "1050.00",
                                                        "BNP Paribas — Mortgage Direct Debit",
                                                        TransactionType.EXPENSE,
                                                        cat(cats, "Mortgage"),
                                                        d));
                }

                // ── Groceries ─────────────────────────────────────────────────────────
                // ~3–4 runs per month over 14 months
                Object[][] groceryRuns = {
                                { "87.50", "Carrefour Market Bastille", 14, 8 },
                                { "124.30", "E.Leclerc Vincennes", 14, 22 },
                                { "56.80", "Monoprix République", 13, 11 },
                                { "98.45", "Carrefour City Nation", 13, 26 },
                                { "78.90", "Intermarché Charonne", 12, 5 },
                                { "112.60", "E.Leclerc Vincennes", 12, 18 },
                                { "65.40", "Carrefour Market Bastille", 11, 9 },
                                { "34.50", "Biocoop Nation", 11, 16 },
                                { "89.30", "Auchan Bercy", 11, 28 },
                                { "102.80", "Carrefour Market Bastille", 10, 6 },
                                { "67.40", "Monoprix Oberkampf", 10, 19 },
                                { "78.50", "E.Leclerc Vincennes", 9, 4 },
                                { "55.90", "Super U — Alfortville", 9, 14 },
                                { "95.20", "Carrefour Market Bastille", 8, 3 },
                                { "43.80", "Biocoop Nation", 8, 21 },
                                { "87.60", "Intermarché Charonne", 7, 7 },
                                { "112.40", "E.Leclerc Vincennes", 7, 20 },
                                { "67.80", "Monoprix République", 6, 10 },
                                { "98.90", "Carrefour City Nation", 6, 24 },
                                { "45.60", "Biocoop Nation", 5, 2 },
                                { "82.40", "Auchan Bercy", 5, 17 },
                                { "115.30", "E.Leclerc Vincennes", 4, 6 },
                                { "96.40", "Carrefour Market Bastille", 4, 22 },
                                { "78.20", "Monoprix Oberkampf", 3, 8 },
                                { "91.50", "Carrefour City Nation", 3, 21 },
                                { "62.30", "Biocoop Nation", 2, 5 },
                                { "104.70", "E.Leclerc Vincennes", 2, 19 },
                                { "88.60", "Carrefour Market Bastille", 1, 7 },
                                { "73.40", "Monoprix République", 1, 18 },
                                { "119.80", "E.Leclerc Vincennes", 1, 27 },
                };
                for (Object[] g : groceryRuns) {
                        txs.add(
                                        tx(
                                                        userId,
                                                        main,
                                                        (String) g[0],
                                                        (String) g[1],
                                                        TransactionType.EXPENSE,
                                                        cat(cats, "Groceries"),
                                                        today.minusMonths((int) g[2]).withDayOfMonth((int) g[3])));
                }

                // ── Restaurants & fast food ───────────────────────────────────────────
                Object[][] dining = {
                                { "12.50", "McDonald's — Nation", "Fast Food", 13, 8 },
                                { "5.50", "Starbucks — Place de la République", "Coffee Shops", 13, 14 },
                                { "28.50", "Uber Eats — Sushi Hikari", "Dining Out", 12, 20 },
                                { "45.80", "Restaurant Le Bistrot Oberkampf", "Dining Out", 11, 14 },
                                { "6.80", "Starbucks — Gare de Lyon", "Coffee Shops", 11, 3 },
                                { "18.90", "McDonald's — Gare du Nord", "Fast Food", 10, 22 },
                                { "32.40", "Deliveroo — Thai Basilic", "Dining Out", 9, 7 },
                                { "38.90", "Sushi Hikari — Paris 11", "Dining Out", 8, 19 },
                                { "4.90", "Starbucks — Gare de Lyon", "Coffee Shops", 8, 5 },
                                { "24.80", "Uber Eats — Bella Italia", "Dining Out", 7, 28 },
                                { "62.50", "Restaurant Chez Paul — Bastille", "Dining Out", 6, 11 },
                                { "14.20", "Burger King — Forum des Halles", "Fast Food", 6, 24 },
                                { "5.80", "Starbucks — Place de la République", "Coffee Shops", 5, 9 },
                                { "41.20", "L'Avenue de l'Opéra Brasserie", "Dining Out", 5, 16 },
                                { "22.50", "Deliveroo — Crispy Burger", "Dining Out", 4, 6 },
                                { "7.20", "Starbucks — Gare de Lyon", "Coffee Shops", 4, 14 },
                                { "78.00", "Restaurant Le Châtelet", "Dining Out", 3, 20 },
                                { "13.60", "McDonald's — Nation", "Fast Food", 3, 8 },
                                { "29.80", "Uber Eats — New Delhi Palace", "Dining Out", 2, 22 },
                                { "54.30", "Brasserie Lipp", "Dining Out", 2, 5 },
                                { "8.40", "Starbucks — Place de la République", "Coffee Shops", 1, 10 },
                                { "36.90", "Delivery Sushi Hikari", "Dining Out", 1, 23 },
                };
                for (Object[] d : dining) {
                        txs.add(
                                        tx(
                                                        userId,
                                                        main,
                                                        (String) d[0],
                                                        (String) d[1],
                                                        TransactionType.EXPENSE,
                                                        cat(cats, (String) d[2]),
                                                        today.minusMonths((int) d[3]).withDayOfMonth((int) d[4])));
                }

                // ── Transport ─────────────────────────────────────────────────────────
                // Navigo Mois pass — most months
                int[] navigoMonths = { 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1 };
                for (int m : navigoMonths) {
                        txs.add(
                                        tx(
                                                        userId,
                                                        main,
                                                        "86.40",
                                                        "RATP — Navigo Mois Pass",
                                                        TransactionType.EXPENSE,
                                                        cat(cats, "Bus/Metro"),
                                                        today.minusMonths(m).withDayOfMonth(1)));
                }
                // Train journeys
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "59.50",
                                                "SNCF — Paris-Lyon TGV (2nd class)",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Train"),
                                                today.minusMonths(9).withDayOfMonth(8)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "45.00",
                                                "SNCF — Paris-Bordeaux Intercités",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Train"),
                                                today.minusMonths(4).withDayOfMonth(12)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "72.00",
                                                "SNCF — Paris-Strasbourg TGV",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Train"),
                                                today.minusMonths(1).withDayOfMonth(7)));
                // Fuel
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "72.40",
                                                "TotalEnergies — Station Autoroute A6",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Gas/Fuel"),
                                                today.minusMonths(8).withDayOfMonth(17)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "68.90",
                                                "TotalEnergies — Station Nation",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Gas/Fuel"),
                                                today.minusMonths(5).withDayOfMonth(4)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "75.30",
                                                "TotalEnergies — Station Bercy",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Gas/Fuel"),
                                                today.minusMonths(2).withDayOfMonth(14)));
                // Ride-share
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "18.50",
                                                "Uber — CDG Airport Transfer",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Taxi/Rideshare"),
                                                today.minusMonths(7).withDayOfMonth(22)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "24.60",
                                                "Uber — Late Night Bastille",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Taxi/Rideshare"),
                                                today.minusMonths(4).withDayOfMonth(19)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "12.80",
                                                "Uber — Paris Centre",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Taxi/Rideshare"),
                                                today.minusMonths(2).withDayOfMonth(27)));

                // ── Shopping ──────────────────────────────────────────────────────────
                Object[][] shopping = {
                                { "34.50", "Amazon — Books & Office Supplies", "Shopping", 13, 15 },
                                { "120.00", "Zalando — Running Shoes", "Clothing", 12, 24 },
                                { "89.90", "Amazon — USB-C Hub & Accessories", "Electronics", 11, 6 },
                                { "245.00", "IKEA — Living Room Bookshelf", "Home Goods", 9, 3 },
                                { "78.50", "Zalando — Winter Jacket", "Clothing", 8, 14 },
                                { "156.80", "Amazon — Philips Hue Smart Lights", "Electronics", 7, 25 },
                                { "56.40", "H&M — T-Shirts Summer Collection", "Clothing", 6, 8 },
                                { "68.90", "Fnac — Kindle Paperwhite", "Electronics", 5, 20 },
                                { "45.20", "Amazon — Kitchen Storage Set", "Home Goods", 4, 13 },
                                { "89.50", "IKEA — Bedroom Accessories", "Home Goods", 3, 28 },
                                { "89.30", "H&M — Spring Collection", "Clothing", 2, 5 },
                                { "52.00", "Amazon — Technical Books", "Shopping", 1, 18 },
                                { "165.00", "Décathlon — Cycling Gear", "Shopping", 7, 4 },
                                { "38.90", "Maison du Monde — Decorative Items", "Home Goods", 10, 12 },
                };
                for (Object[] s : shopping) {
                        txs.add(
                                        tx(
                                                        userId,
                                                        main,
                                                        (String) s[0],
                                                        (String) s[1],
                                                        TransactionType.EXPENSE,
                                                        cat(cats, (String) s[2]),
                                                        today.minusMonths((int) s[3]).withDayOfMonth((int) s[4])));
                }

                // ── Healthcare ────────────────────────────────────────────────────────
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "18.50",
                                                "Pharmacie du Marais — Prescriptions",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Prescriptions"),
                                                today.minusMonths(13).withDayOfMonth(20)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "25.00",
                                                "Dr. Martin — General Practitioner",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Doctor Visits"),
                                                today.minusMonths(11).withDayOfMonth(11)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "32.80",
                                                "Pharmacie Centrale République",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Prescriptions"),
                                                today.minusMonths(9).withDayOfMonth(16)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "180.00",
                                                "Optique Vision — Progressive Lenses",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Vision"),
                                                today.minusMonths(8).withDayOfMonth(22)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "25.00",
                                                "Dr. Dubois — Dermatologist",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Doctor Visits"),
                                                today.minusMonths(6).withDayOfMonth(7)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "25.40",
                                                "Pharmacie République",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Prescriptions"),
                                                today.minusMonths(4).withDayOfMonth(11)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "450.00",
                                                "Dentiste Dr. Chen — Crown & Filling",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Dental"),
                                                today.minusMonths(3).withDayOfMonth(15)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "22.90",
                                                "Pharmacie du Marais",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Prescriptions"),
                                                today.minusMonths(1).withDayOfMonth(18)));

                // ── Travel ────────────────────────────────────────────────────────────
                // Summer holiday: Barcelona
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "245.00",
                                                "Air France — Paris CDG → Barcelona BCN",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Airlines"),
                                                today.minusMonths(5).withDayOfMonth(18)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "320.00",
                                                "Airbnb — Eixample Apartment Barcelona (4 nights)",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Hotels"),
                                                today.minusMonths(5).withDayOfMonth(18)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "85.00",
                                                "Restaurant La Barceloneta",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Dining Out"),
                                                today.minusMonths(5).withDayOfMonth(20)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "42.50",
                                                "Ryanair — Paris BVA → Rome CIA",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Airlines"),
                                                today.minusMonths(13).withDayOfMonth(5)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "280.00",
                                                "Booking.com — Hotel Trastevere Rome (3 nights)",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Hotels"),
                                                today.minusMonths(13).withDayOfMonth(5)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "65.00",
                                                "Trattoria da Luigi — Rome",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Dining Out"),
                                                today.minusMonths(13).withDayOfMonth(7)));
                // Weekend in Alsace (joint account)
                txs.add(
                                tx(
                                                userId,
                                                joint,
                                                "340.00",
                                                "Hôtel Le Maréchal Colmar (2 nights)",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Hotels"),
                                                today.minusMonths(2).withDayOfMonth(4)));

                // ── Personal care ─────────────────────────────────────────────────────
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "42.00",
                                                "Salon Saint-Antoine — Haircut",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Haircut/Salon"),
                                                today.minusMonths(12).withDayOfMonth(9)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "38.50",
                                                "Salon Saint-Antoine — Haircut",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Haircut/Salon"),
                                                today.minusMonths(9).withDayOfMonth(21)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "24.90",
                                                "Sephora — Skincare Routine",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Haircut/Salon"),
                                                today.minusMonths(8).withDayOfMonth(4)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "42.00",
                                                "Salon Saint-Antoine — Haircut",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Haircut/Salon"),
                                                today.minusMonths(6).withDayOfMonth(16)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "65.80",
                                                "Sephora — Perfume & Cosmetics",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Haircut/Salon"),
                                                today.minusMonths(3).withDayOfMonth(10)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "42.00",
                                                "Salon Saint-Antoine — Haircut",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Haircut/Salon"),
                                                today.minusMonths(2).withDayOfMonth(20)));

                // ── Education ─────────────────────────────────────────────────────────
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "29.99",
                                                "Udemy — React & TypeScript Complete Guide",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Online Courses"),
                                                today.minusMonths(12).withDayOfMonth(3)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "19.99",
                                                "Coursera — Machine Learning Specialization",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Online Courses"),
                                                today.minusMonths(9).withDayOfMonth(15)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "18.90",
                                                "Fnac — Clean Code by Robert C. Martin",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Books/Supplies"),
                                                today.minusMonths(6).withDayOfMonth(23)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "39.99",
                                                "LinkedIn Learning — Annual Subscription",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Online Courses"),
                                                today.minusMonths(3).withDayOfMonth(1)));

                // ── Joint account expenses ─────────────────────────────────────────────
                txs.add(
                                tx(
                                                userId,
                                                joint,
                                                "650.00",
                                                "IKEA — Shared Living Room Renovation",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Home Goods"),
                                                today.minusMonths(7).withDayOfMonth(2)));
                txs.add(
                                tx(
                                                userId,
                                                joint,
                                                "120.00",
                                                "Restaurant Anniversaire — Le Grand Véfour",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Dining Out"),
                                                today.minusMonths(4).withDayOfMonth(15)));

                // ── Charity ───────────────────────────────────────────────────────────
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "50.00",
                                                "Médecins Sans Frontières — Monthly Donation",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Charity"),
                                                today.minusMonths(11).withDayOfMonth(1)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "50.00",
                                                "Médecins Sans Frontières — Monthly Donation",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Charity"),
                                                today.minusMonths(7).withDayOfMonth(1)));
                txs.add(
                                tx(
                                                userId,
                                                main,
                                                "50.00",
                                                "Médecins Sans Frontières — Monthly Donation",
                                                TransactionType.EXPENSE,
                                                cat(cats, "Charity"),
                                                today.minusMonths(3).withDayOfMonth(1)));

                List<Transaction> saved = transactionRepository.saveAll(txs);

                // Search token generation replaces the old FTS population.
                // Tokens are generated in generateSearchTokens() after all entities are saved.
        }

        /**
         * Generates blind-index search tokens for all demo entities so they are
         * discoverable via the encrypted search system.
         */
        private void generateSearchTokens(Long userId, SecretKey encryptionKey, List<Account> accounts) {
                try {
                        SecretKey searchKey = searchTokenService.deriveSearchKey(encryptionKey);

                        // Index accounts
                        for (Account account : accounts) {
                                searchTokenService.indexEntity(userId, "ACCOUNT", account.getId(),
                                                Collections.singletonList(new String[] { "name", account.getName() }),
                                                searchKey);
                        }

                        // Index transactions
                        List<Transaction> transactions = transactionRepository.findByUserId(userId);
                        for (Transaction tx : transactions) {
                                searchTokenService.indexEntity(userId, "TRANSACTION", tx.getId(),
                                                List.of(
                                                                new String[] { "description", tx.getDescription() },
                                                                new String[] { "payee", tx.getPayee() },
                                                                new String[] { "tags", tx.getTags() }),
                                                searchKey);
                        }

                        // Index assets
                        List<Asset> assets = assetRepository.findByUserId(userId);
                        for (Asset asset : assets) {
                                searchTokenService.indexEntity(userId, "ASSET", asset.getId(),
                                                List.of(
                                                                new String[] { "name", asset.getName() },
                                                                new String[] { "symbol", asset.getSymbol() }),
                                                searchKey);
                        }

                        // Index liabilities
                        List<Liability> liabilities = liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId);
                        for (Liability liability : liabilities) {
                                searchTokenService.indexEntity(userId, "LIABILITY", liability.getId(),
                                                Collections.singletonList(new String[] { "name", liability.getName() }),
                                                searchKey);
                        }

                        // Index real estate
                        List<RealEstateProperty> properties = realEstateRepository.findByUserId(userId);
                        for (RealEstateProperty property : properties) {
                                searchTokenService.indexEntity(userId, "REAL_ESTATE", property.getId(),
                                                List.of(
                                                                new String[] { "name", property.getName() },
                                                                new String[] { "address", property.getAddress() }),
                                                searchKey);
                        }

                        log.info("Generated search tokens for {} transactions, {} accounts, {} assets, {} liabilities, {} properties",
                                        transactions.size(), accounts.size(), assets.size(),
                                        liabilities.size(), properties.size());
                } catch (Exception e) {
                        log.warn("Failed to generate demo search tokens: {}", e.getMessage());
                }
        }

        private Transaction tx(
                        Long userId,
                        Account account,
                        String amount,
                        String description,
                        TransactionType type,
                        Category category,
                        LocalDate date) {
                return Transaction.builder()
                                .userId(userId)
                                .accountId(account.getId())
                                .amount(new BigDecimal(amount))
                                .description(enc(description))
                                .payee(description) // store plain-text for FTS indexing
                                .type(type)
                                .categoryId(category != null ? category.getId() : null)
                                .currency(account.getCurrency())
                                .date(date)
                                .createdAt(LocalDateTime.now())
                                .build();
        }

        // ── Budgets ───────────────────────────────────────────────────────────────

        private void seedBudgets(Long userId) {
                List<Category> cats = categoryRepository.findByUserId(userId);
                if (cats.isEmpty())
                        return;

                LocalDate firstOfMonth = LocalDate.now().withDayOfMonth(1);
                LocalDate yearEnd = firstOfMonth.plusYears(1);

                Object[][] budgetDefs = {
                                { "Groceries", "500.00" },
                                { "Dining Out", "280.00" },
                                { "Public Transit", "120.00" },
                                { "Auto Expenses", "200.00" },
                                { "Entertainment", "150.00" },
                                { "Shopping", "400.00" },
                                { "Healthcare", "150.00" },
                                { "Subscriptions", "100.00" },
                                { "Personal Care", "100.00" },
                                { "Air Travel", "900.00" },
                                { "Education", "80.00" },
                                { "Utilities", "140.00" },
                                { "Charity", "60.00" },
                };

                List<Budget> budgets = new ArrayList<>();
                for (Object[] def : budgetDefs) {
                        Category c = cat(cats, (String) def[0]);
                        budgets.add(
                                        Budget.builder()
                                                        .userId(userId)
                                                        .categoryId(c != null ? c.getId() : cats.get(0).getId())
                                                        .amount(enc((String) def[1]))
                                                        .period(BudgetPeriod.MONTHLY)
                                                        .currency("EUR")
                                                        .startDate(firstOfMonth)
                                                        .endDate(yearEnd)
                                                        .build());
                }
                budgetRepository.saveAll(budgets);
        }

        // ── Helpers ───────────────────────────────────────────────────────────────

        /**
         * Case-insensitive category lookup; falls back to first category if not found.
         */
        private Category cat(List<Category> categories, String name) {
                return categories.stream()
                                .filter(c -> name.equalsIgnoreCase(c.getName()))
                                .findFirst()
                                .orElse(categories.get(0));
        }
}
