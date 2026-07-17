package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Random;
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
import org.openfinance.entity.Payee;
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
import org.openfinance.repository.PayeeRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.RecurringTransactionRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;
import org.openfinance.security.KeyManagementService;
import org.openfinance.security.PasswordService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final AssetRepository assetRepository;
    private final LiabilityRepository liabilityRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final PayeeRepository payeeRepository;
    private final InstitutionRepository institutionRepository;

    private final PasswordService passwordService;
    private final KeyManagementService keyManagementService;
    private final EncryptionService encryptionService;
    private final CategorySeeder categorySeeder;
    private final InstitutionSeeder institutionSeeder;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final RealEstateRepository realEstateRepository;

    private static final String TEST_USERNAME = "demo";
    private static final String TEST_PASSWORD = "demo123";
    private static final String TEST_MASTER_PASSWORD = "master123";

    private SecretKey cachedEncryptionKey;
    private Long cachedUserId;

    @Value("${application.test-data-seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${seed.count.accounts:100}")
    private int seedAccountsCount;

    @Value("${seed.count.assets:100}")
    private int seedAssetsCount;

    @Value("${seed.count.liabilities:100}")
    private int seedLiabilitiesCount;

    @Value("${seed.count.realestate:100}")
    private int seedRealEstateCount;

    @Value("${seed.count.transactions:100}")
    private int seedTransactionsCount;

    @Value("${seed.count.recurring:100}")
    private int seedRecurringCount;

    @Value("${seed.count.budgets:100}")
    private int seedBudgetsCount;

    @Value("${seed.count.payees:100}")
    private int seedPayeesCount;

    @Override
    public void run(String... args) throws Exception {
        if (!seedEnabled) {
            log.info("Data seeding is disabled.");
            return;
        }
        log.info("Checking for test data seed...");
        seedTestData();
    }

    @Transactional
    public void seedTestData() {
        if (userRepository.findByUsername(TEST_USERNAME).isPresent()) {
            log.info("Test user already exists, skipping seed");
            return;
        }

        log.info("Seeding test data...");

        byte[] salt = keyManagementService.generateSalt();
        String saltBase64 = Base64.getEncoder().encodeToString(salt);
        String passwordHash = passwordService.hashPassword(TEST_PASSWORD);

        User user =
                User.builder()
                        .username(TEST_USERNAME)
                        .email("demo@openfinance.app")
                        .passwordHash(passwordHash)
                        .masterPasswordSalt(saltBase64)
                        .createdAt(LocalDateTime.now())
                        .build();
        user = userRepository.save(user);
        Long userId = user.getId();
        cachedUserId = userId;

        SecretKey encryptionKey =
                keyManagementService.deriveKey(TEST_MASTER_PASSWORD.toCharArray(), salt);
        cachedEncryptionKey = encryptionKey;

        institutionSeeder.seedDefaultInstitutions();
        categorySeeder.seedDefaultCategories(userId);

        seedAccounts(userId);
        seedAssets(userId);
        seedLiabilities(userId);
        seedRealEstate(userId);
        seedRecurringTransactions(userId);
        seedTransactions(userId);
        seedBudgets(userId);
        seedPayees(userId);

        log.info("Test data seeding completed for user: {}", TEST_USERNAME);
    }

    private void seedAccounts(Long userId) {
        Random random = new Random(42);
        List<Institution> institutions = institutionRepository.findAll();
        Institution randomInstitution =
                institutions.isEmpty()
                        ? null
                        : institutions.get(random.nextInt(institutions.size()));
        List<Account> accounts = new java.util.ArrayList<>();
        // add some realistic accounts
        accounts.add(
                createAccount(
                        userId,
                        "Main Checking",
                        AccountType.CHECKING,
                        new BigDecimal("5250.00"),
                        "EUR",
                        "1234-5678-9012-3456",
                        randomInstitution));
        accounts.add(
                createAccount(
                        userId,
                        "Savings Account",
                        AccountType.SAVINGS,
                        new BigDecimal("15750.00"),
                        "EUR",
                        "9876-5432-1098-7654",
                        randomInstitution));
        accounts.add(
                createAccount(
                        userId,
                        "Investment Account",
                        AccountType.INVESTMENT,
                        new BigDecimal("35000.00"),
                        "EUR",
                        "4567-8901-2345-6789",
                        randomInstitution));

        int base = Math.max(1, seedAccountsCount);
        for (int i = accounts.size(); i < base; i++) {
            String name = "Account " + (i + 1);
            AccountType type =
                    (i % 4 == 0)
                            ? AccountType.CHECKING
                            : (i % 4 == 1) ? AccountType.SAVINGS : AccountType.CASH;
            BigDecimal balance = BigDecimal.valueOf(100 + random.nextInt(50000)).setScale(2);
            String currency = (i % 3 == 0) ? "EUR" : (i % 3 == 1) ? "USD" : "GBP";
            String accNum =
                    String.format(
                            "%04d-%04d-%04d-%04d",
                            random.nextInt(10000),
                            random.nextInt(10000),
                            random.nextInt(10000),
                            random.nextInt(10000));
            Institution inst = randomInstitution;
            accounts.add(createAccount(userId, name, type, balance, currency, accNum, inst));
        }

        accountRepository.saveAll(accounts);
    }

    private Account createAccount(
            Long userId,
            String name,
            AccountType type,
            BigDecimal balance,
            String currency,
            String accountNumber,
            Institution institution) {
        return Account.builder()
                .userId(userId)
                .name(name)
                .type(type)
                .institution(institution)
                .balance(balance)
                .currency(currency)
                .accountNumber(accountNumber)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void seedAssets(Long userId) {
        Random rnd = new Random(123);
        List<Asset> assets = new java.util.ArrayList<>();
        int base = Math.max(1, seedAssetsCount);
        // keep a few named assets for realism (prices approximate as of early 2026)
        assets.add(
                createAsset(
                        userId,
                        "Apple Inc.",
                        AssetType.STOCK,
                        new BigDecimal("150.00"),
                        new BigDecimal("227.00"),
                        "USD",
                        "AAPL",
                        50));
        assets.add(
                createAsset(
                        userId,
                        "Microsoft",
                        AssetType.STOCK,
                        new BigDecimal("280.00"),
                        new BigDecimal("415.00"),
                        "USD",
                        "MSFT",
                        20));
        assets.add(
                createAsset(
                        userId,
                        "Bitcoin",
                        AssetType.CRYPTO,
                        new BigDecimal("35000.00"),
                        new BigDecimal("88000.00"),
                        "USD",
                        "BTC-USD",
                        0.25));
        assets.add(
                createAsset(
                        userId,
                        "Ethereum",
                        AssetType.CRYPTO,
                        new BigDecimal("2000.00"),
                        new BigDecimal("2500.00"),
                        "USD",
                        "ETH-USD",
                        2.0));
        assets.add(
                createAsset(
                        userId,
                        "NVIDIA Corp.",
                        AssetType.STOCK,
                        new BigDecimal("450.00"),
                        new BigDecimal("120.00"),
                        "USD",
                        "NVDA",
                        10));
        for (int i = assets.size(); i < base; i++) {
            String name = "Asset " + (i + 1);
            AssetType type = AssetType.OTHER;
            if (i % 3 == 0) type = AssetType.STOCK;
            if (i % 5 == 0) type = AssetType.CRYPTO;
            BigDecimal purchase = BigDecimal.valueOf(100 + rnd.nextInt(10000)).setScale(2);
            BigDecimal current = purchase.add(BigDecimal.valueOf(rnd.nextInt(10000))).setScale(2);
            String symbol = type == AssetType.STOCK || type == AssetType.CRYPTO ? ("T" + i) : null;
            assets.add(
                    createAsset(
                            userId,
                            name,
                            type,
                            purchase,
                            current,
                            "EUR",
                            symbol,
                            rnd.nextDouble() * 100));
        }
        assetRepository.saveAll(assets);
    }

    private Asset createAsset(
            Long userId,
            String name,
            AssetType type,
            BigDecimal purchasePrice,
            BigDecimal currentPrice,
            String currency,
            String symbol,
            double quantity) {
        return Asset.builder()
                .userId(userId)
                .name(name)
                .type(type)
                .symbol(symbol)
                .quantity(BigDecimal.valueOf(quantity))
                .purchasePrice(purchasePrice)
                .currentPrice(currentPrice)
                .currency(currency)
                .purchaseDate(LocalDate.now().minusYears(2))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void seedLiabilities(Long userId) {
        Random rnd = new Random(321);
        List<Liability> list = new java.util.ArrayList<>();
        int base = Math.max(1, seedLiabilitiesCount);

        // Varied remaining-balance ratios so each liability shows a different
        // "% paid off" value (fixes BUG-05: all showing exactly 40%).
        double[] remainingRatios = {0.85, 0.72, 0.95, 0.50, 0.30, 0.65, 0.78, 0.45, 0.20, 0.88};

        for (int i = 0; i < base; i++) {
            Liability l = new Liability();
            l.setUserId(userId);

            String name;
            LiabilityType type;
            BigDecimal interestRateValue;
            int loanTermYears;

            // First 5 entries are realistic named liabilities; the rest are generated.
            // BUG-09 fix: give Home Mortgage an explicit 3.75% rate (was relying on
            // random which could produce near-zero for seed 321 first call).
            // BUG-02 fix: minimum payment is now always calculated and stored.
            switch (i) {
                case 0 -> {
                    name = "Home Mortgage";
                    type = LiabilityType.MORTGAGE;
                    interestRateValue = new BigDecimal("3.75");
                    loanTermYears = 30;
                }
                case 1 -> {
                    name = "Car Loan";
                    type = LiabilityType.LOAN;
                    interestRateValue = new BigDecimal("5.99");
                    loanTermYears = 5;
                }
                case 2 -> {
                    name = "Credit Card";
                    type = LiabilityType.CREDIT_CARD;
                    interestRateValue = new BigDecimal("19.99");
                    loanTermYears = 3;
                }
                case 3 -> {
                    name = "Student Loan";
                    type = LiabilityType.STUDENT_LOAN;
                    interestRateValue = new BigDecimal("4.50");
                    loanTermYears = 10;
                }
                case 4 -> {
                    name = "Personal Loan";
                    type = LiabilityType.PERSONAL_LOAN;
                    interestRateValue = new BigDecimal("8.50");
                    loanTermYears = 3;
                }
                default -> {
                    name = "Liability " + (i + 1);
                    type =
                            switch (i % 7) {
                                case 0 -> LiabilityType.MORTGAGE;
                                case 1 -> LiabilityType.LOAN;
                                case 2 -> LiabilityType.CREDIT_CARD;
                                case 3 -> LiabilityType.PERSONAL_LOAN;
                                case 4 -> LiabilityType.STUDENT_LOAN;
                                case 5 -> LiabilityType.AUTO_LOAN;
                                default -> LiabilityType.OTHER;
                            };
                    interestRateValue =
                            BigDecimal.valueOf(1.5 + rnd.nextDouble() * 15)
                                    .setScale(2, RoundingMode.HALF_UP);
                    loanTermYears = 2 + rnd.nextInt(28);
                }
            }

            l.setName(name);
            l.setType(type);

            BigDecimal principal = BigDecimal.valueOf(1000 + rnd.nextInt(200000)).setScale(2);

            // Use the rotation of remaining ratios for variety in paid-off percentage
            double remainingRatio = remainingRatios[i % remainingRatios.length];
            BigDecimal balance =
                    principal
                            .multiply(BigDecimal.valueOf(remainingRatio))
                            .setScale(2, RoundingMode.HALF_UP);

            l.setPrincipal(principal.toPlainString());
            l.setCurrentBalance(balance.toPlainString());
            l.setInterestRate(interestRateValue.toPlainString());

            // Set start date and end date so amortization schedule can be generated
            int yearsAgo = 1 + rnd.nextInt(Math.min(loanTermYears, 10));
            LocalDate startDate = LocalDate.now().minusYears(yearsAgo);
            l.setStartDate(startDate);
            l.setEndDate(startDate.plusYears(loanTermYears));

            // Calculate realistic monthly minimum payment using standard amortization:
            // M = P * r(1+r)^n / ((1+r)^n - 1)
            BigDecimal monthlyRate =
                    interestRateValue.divide(BigDecimal.valueOf(1200), 8, RoundingMode.HALF_UP);
            int totalMonths = loanTermYears * 12;
            BigDecimal minimumPayment;
            if (monthlyRate.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    BigDecimal pow = monthlyRate.add(BigDecimal.ONE).pow(totalMonths);
                    minimumPayment =
                            principal
                                    .multiply(monthlyRate.multiply(pow))
                                    .divide(pow.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
                } catch (ArithmeticException e) {
                    minimumPayment =
                            principal.divide(
                                    BigDecimal.valueOf(totalMonths), 2, RoundingMode.HALF_UP);
                }
            } else {
                minimumPayment =
                        principal.divide(BigDecimal.valueOf(totalMonths), 2, RoundingMode.HALF_UP);
            }
            l.setMinimumPayment(minimumPayment.toPlainString());

            l.setCurrency("EUR");
            list.add(l);
        }
        liabilityRepository.saveAll(list);
    }

    private void seedTransactions(Long userId) {
        List<Account> accounts = accountRepository.findByUserId(userId);
        List<Category> categories = categoryRepository.findByUserId(userId);
        if (accounts.isEmpty() || categories.isEmpty()) return;

        Account mainChecking =
                accounts.stream()
                        .filter(
                                a ->
                                        a.getType() == AccountType.CHECKING
                                                && "EUR".equals(a.getCurrency()))
                        .findFirst()
                        .orElse(accounts.get(0));

        Random rnd = new Random(99);
        List<Transaction> txs = new java.util.ArrayList<>();
        int base = Math.max(1, seedTransactionsCount);
        int payeeCount = Math.max(1, seedPayeesCount);
        for (int i = 0; i < base; i++) {
            BigDecimal amount = BigDecimal.valueOf(5 + rnd.nextInt(2000)).setScale(2);
            TransactionType type = (i % 8 == 0) ? TransactionType.INCOME : TransactionType.EXPENSE;
            Category category = categories.get(rnd.nextInt(categories.size()));
            LocalDate date = LocalDate.now().minusDays(rnd.nextInt(365));
            String desc = (type == TransactionType.INCOME ? "Salary " : "Purchase ") + (i + 1);
            String payee = "Payee " + ((i % payeeCount) + 1);
            Transaction tx =
                    createTransaction(
                            userId,
                            mainChecking,
                            amount,
                            desc,
                            type,
                            category,
                            mainChecking.getCurrency(),
                            date);
            tx.setPayee(payee);
            txs.add(tx);
        }
        transactionRepository.saveAll(txs);
    }

    private void seedRecurringTransactions(Long userId) {
        List<Account> accounts = accountRepository.findByUserId(userId);
        List<Category> categories = categoryRepository.findByUserId(userId);
        if (accounts.isEmpty() || categories.isEmpty()) return;

        Random rnd = new Random(2026);
        List<RecurringTransaction> list = new java.util.ArrayList<>();
        int base = Math.max(1, seedRecurringCount);
        RecurringFrequency[] freqs = RecurringFrequency.values();
        for (int i = 0; i < base; i++) {
            Account a = accounts.get(rnd.nextInt(accounts.size()));
            TransactionType type = (i % 5 == 0) ? TransactionType.INCOME : TransactionType.EXPENSE;
            BigDecimal amount = BigDecimal.valueOf(10 + rnd.nextInt(2000)).setScale(2);
            RecurringTransaction r =
                    RecurringTransaction.builder()
                            .userId(userId)
                            .accountId(a.getId())
                            .type(type)
                            .amount(amount)
                            .currency(a.getCurrency())
                            .categoryId(categories.get(rnd.nextInt(categories.size())).getId())
                            .description(
                                    (type == TransactionType.INCOME
                                                    ? "Recurring Income "
                                                    : "Recurring Expense ")
                                            + (i + 1))
                            .frequency(freqs[i % freqs.length])
                            .nextOccurrence(LocalDate.now().plusDays(rnd.nextInt(30)))
                            .isActive(true)
                            .build();
            list.add(r);
        }
        recurringTransactionRepository.saveAll(list);
    }

    private void seedRealEstate(Long userId) {
        Random rnd = new Random(555);
        List<RealEstateProperty> list = new java.util.ArrayList<>();
        int base = Math.max(1, seedRealEstateCount);
        for (int i = 0; i < base; i++) {
            RealEstateProperty p =
                    RealEstateProperty.builder()
                            .userId(userId)
                            .name("Property " + (i + 1))
                            .address((100 + i) + " Demo St, City")
                            .propertyType(PropertyType.RESIDENTIAL)
                            .purchasePrice(String.valueOf(100000 + rnd.nextInt(900000)))
                            .currentValue(String.valueOf(120000 + rnd.nextInt(900000)))
                            .currency("EUR")
                            .purchaseDate(LocalDate.now().minusYears(1 + rnd.nextInt(10)))
                            .latitude(
                                    BigDecimal.valueOf(48.8 + rnd.nextDouble())
                                            .setScale(6, RoundingMode.HALF_UP))
                            .longitude(
                                    BigDecimal.valueOf(2.2 + rnd.nextDouble())
                                            .setScale(6, RoundingMode.HALF_UP))
                            .notes("Seeded property notes")
                            .isActive(true)
                            .build();
            list.add(p);
        }
        realEstateRepository.saveAll(list);
    }

    private Transaction createTransaction(
            Long userId,
            Account account,
            BigDecimal amount,
            String description,
            TransactionType type,
            Category category,
            String currency,
            LocalDate date) {
        return Transaction.builder()
                .userId(userId)
                .accountId(account.getId())
                .amount(amount)
                .description(description)
                .type(type)
                .categoryId(category != null ? category.getId() : null)
                .currency(currency)
                .date(date)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Category findCategoryByName(List<Category> categories, String name) {
        return categories.stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElse(categories.get(0));
    }

    private void seedBudgets(Long userId) {
        List<Category> categories = categoryRepository.findByUserId(userId);

        if (categories.isEmpty()) return;

        LocalDate startDate = LocalDate.now().withDayOfMonth(1);
        LocalDate endDate = startDate.plusYears(1);
        Random rnd = new Random(7);
        List<Budget> budgets = new java.util.ArrayList<>();
        int base = Math.max(1, seedBudgetsCount);
        for (int i = 0; i < base; i++) {
            Category c = categories.get(rnd.nextInt(categories.size()));
            String amount = String.valueOf(50 + rnd.nextInt(2000));
            budgets.add(
                    createBudget(
                            userId,
                            c.getId(),
                            amount,
                            BudgetPeriod.MONTHLY,
                            "EUR",
                            startDate,
                            endDate));
        }
        budgetRepository.saveAll(budgets);
    }

    private Budget createBudget(
            Long userId,
            Long categoryId,
            String amount,
            BudgetPeriod period,
            String currency,
            LocalDate startDate,
            LocalDate endDate) {
        return Budget.builder()
                .userId(userId)
                .categoryId(categoryId)
                .amount(amount)
                .period(period)
                .currency(currency)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    private void seedPayees(Long userId) {
        List<Category> categories = categoryRepository.findByUserId(userId);
        if (categories.isEmpty()) return;
        Random rnd = new Random(13);
        List<Payee> payees = new java.util.ArrayList<>();
        int base = Math.max(1, seedPayeesCount);
        for (int i = 0; i < base; i++) {
            Category c = categories.get(rnd.nextInt(categories.size()));
            payees.add(createPayee("Payee " + (i + 1), c));
        }
        payeeRepository.saveAll(payees);
    }

    private Payee createPayee(String name, Category defaultCategory) {
        return Payee.builder()
                .name(name)
                .defaultCategory(defaultCategory)
                .isSystem(false)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
