package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.AccountInterest;
import org.openfinance.dto.AccountSummary;
import org.openfinance.dto.AssetAllocation;
import org.openfinance.dto.BorrowingCapacity;
import org.openfinance.dto.CashflowSankeyDto;
import org.openfinance.dto.DashboardSummary;
import org.openfinance.dto.EstimatedInterestSummary;
import org.openfinance.dto.NetWorthAllocation;
import org.openfinance.dto.NetWorthSummary;
import org.openfinance.dto.PortfolioPerformance;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.dto.YearlyBalanceResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Category;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityType;
import org.openfinance.entity.NetWorth;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.mapper.TransactionMapper;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for dashboard operations and aggregated financial data.
 *
 * <p>This service provides comprehensive dashboard functionality including:
 *
 * <ul>
 *   <li>Complete dashboard summary (net worth, accounts, recent transactions)
 *   <li>Account summaries with balances
 *   <li>Recent transactions with decryption
 *   <li>Cash flow analysis (income vs expenses)
 *   <li>Spending breakdown by category
 * </ul>
 *
 * <p><b>Requirements:</b>
 *
 * <ul>
 *   <li>REQ-2.8.1.1: Dashboard Metrics - Display gross wealth, liabilities, net worth
 *   <li>REQ-2.8.1.2: Trend Visualization - Provide monthly change data
 * </ul>
 *
 * @see DashboardSummary
 * @see NetWorthSummary
 * @see AccountSummary
 * @since 1.0.0
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final NetWorthService netWorthService;
    private final AccountRepository accountRepository;
    private final AssetRepository assetRepository;
    private final LiabilityRepository liabilityRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TransactionMapper transactionMapper;
    private final EncryptionService encryptionService;
    private final InterestCalculatorService interestCalculatorService;
    private final ExchangeRateService exchangeRateService;

    /**
     * Retrieves a complete dashboard summary for the specified user.
     *
     * <p>This method aggregates data from multiple sources to provide a comprehensive overview of
     * the user's financial position including:
     *
     * <ul>
     *   <li>Current net worth with monthly change
     *   <li>List of accounts with balances (decrypted)
     *   <li>Recent transactions (up to 10 most recent)
     *   <li>Total account count and transaction count
     * </ul>
     *
     * <p><b>Requirement REQ-2.8.1.1:</b> Dashboard displays gross wealth, liabilities, net worth
     *
     * @param userId the ID of the user requesting the dashboard
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive data
     * @return complete dashboard summary with all aggregated data
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    @Cacheable(value = "dashboardSummary", key = "#userId")
    public DashboardSummary getDashboardSummary(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Generating dashboard summary for user {}", userId);

        // Get net worth summary
        NetWorthSummary netWorthSummary = getNetWorthSummary(userId);

        // Get account summaries (decrypted)
        List<AccountSummary> accountSummaries = getAccountSummaries(userId);

        // Get recent transactions (last 10, decrypted)
        List<TransactionResponse> recentTransactions = getRecentTransactions(userId, 10);

        // Get statistics
        long totalTransactions = transactionRepository.countByUserId(userId);

        // Fetch user's base currency (REQ-6.3.14)
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User not found with ID: " + userId));
        String baseCurrency = user.getBaseCurrency();

        // Build dashboard summary
        DashboardSummary dashboard =
                DashboardSummary.builder()
                        .netWorth(netWorthSummary)
                        .accounts(accountSummaries)
                        .recentTransactions(recentTransactions)
                        .snapshotDate(LocalDate.now())
                        .totalAccounts(accountSummaries.size())
                        .totalTransactions(totalTransactions)
                        .baseCurrency(baseCurrency) // Use user's preferred base currency
                        .build();

        log.info(
                "Dashboard summary generated for user {}: {} accounts, {} transactions, net worth {}",
                userId,
                accountSummaries.size(),
                totalTransactions,
                netWorthSummary.getNetWorth());

        return dashboard;
    }

    /**
     * Retrieves the net worth summary for the specified user.
     *
     * <p>This includes current assets, liabilities, net worth, and month-over-month change. If no
     * net worth snapshot exists for today, it calculates the current net worth on-the-fly.
     *
     * <p><b>Requirement REQ-2.8.1.1:</b> Display net worth and monthly change
     *
     * <p><b>Requirement REQ-3.1:</b> Performance – result is cached in {@code netWorthSummary} for
     * up to 15 minutes to avoid repeated snapshot writes and asset-valuation queries. The cache is
     * evicted by {@link org.openfinance.service.NetWorthService} whenever new data is saved.
     *
     * @param userId the ID of the user
     * @return net worth summary with monthly change statistics
     * @throws IllegalArgumentException if userId is null
     */
    @Cacheable(value = "netWorthSummary", key = "#userId")
    public NetWorthSummary getNetWorthSummary(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Retrieving net worth summary for user {}", userId);

        // Always calculate/update today's snapshot to keep totals accurate after data
        // changes
        LocalDate today = LocalDate.now();
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User not found with ID: " + userId));
        final String baseCurrency =
                (user.getBaseCurrency() == null || user.getBaseCurrency().isBlank())
                        ? "USD"
                        : user.getBaseCurrency();

        NetWorth currentNetWorth =
                netWorthService.saveNetWorthSnapshot(userId, today, baseCurrency);

        // Calculate monthly change
        NetWorthService.NetWorthChange monthlyChange =
                netWorthService.calculateMonthlyChange(userId);

        // Build summary
        return NetWorthSummary.builder()
                .date(currentNetWorth.getSnapshotDate())
                .totalAssets(currentNetWorth.getTotalAssets())
                .totalLiabilities(currentNetWorth.getTotalLiabilities())
                .netWorth(currentNetWorth.getNetWorth())
                // BUG-001 fix: only set change values when there is prior comparison data
                .monthlyChangeAmount(monthlyChange.hasComparison() ? monthlyChange.amount() : null)
                .monthlyChangePercentage(
                        monthlyChange.hasComparison() ? monthlyChange.percentage() : null)
                .currency(currentNetWorth.getCurrency())
                .build();
    }

    /**
     * Retrieves a list of account summaries for the specified user.
     *
     * <p>Account names and descriptions are decrypted using the provided encryption key. Accounts
     * are sorted by balance descending (highest balance first). Only active accounts are included.
     *
     * @param userId the ID of the user
     * @param encryptionKey the AES-256 encryption key for decryption
     * @return list of account summaries sorted by balance descending
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    @Cacheable(value = "accountSummaries", key = "#userId")
    public List<AccountSummary> getAccountSummaries(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Retrieving account summaries for user {}", userId);

        List<Account> accounts = accountRepository.findByUserIdAndIsActive(userId, true);

        List<AccountSummary> summaries =
                accounts.stream()
                        .map(account -> mapToAccountSummary(account))
                        .sorted(
                                (a, b) ->
                                        b.getBalance().compareTo(a.getBalance())) // Sort by balance
                        // descending
                        .collect(Collectors.toList());

        log.debug("Retrieved {} account summaries for user {}", summaries.size(), userId);

        return summaries;
    }

    /**
     * Retrieves the most recent transactions for the specified user.
     *
     * <p>This method fetches the {@code limit} most recent transactions for the user and returns
     * them as a list of {@link TransactionResponse} DTOs. Sensitive fields (description, notes,
     * account/category names) are decrypted using the provided {@code encryptionKey}.
     *
     * <p><b>Performance Optimization (REQ-3.1):</b> To avoid the N+1 query problem, this method
     * batch-loads all associated {@link Account} and {@link Category} entities in two single
     * database round-trips using {@code findAllByIds}, rather than issuing a separate query for
     * each transaction.
     *
     * @param userId the ID of the user
     * @param limit the maximum number of transactions to retrieve
     * @param encryptionKey the AES-256 key for decrypting sensitive fields
     * @return list of recent transactions with decrypted fields and populated names
     * @throws IllegalArgumentException if userId is null, encryptionKey is null, or limit <= 0
     */
    public List<TransactionResponse> getRecentTransactions(Long userId, int limit) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }

        log.debug("Retrieving {} recent transactions for user {}", limit, userId);

        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(
                        0,
                        limit,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "date"));
        List<Transaction> transactions =
                transactionRepository.findByUserId(userId, pageable).getContent();

        // Requirement REQ-3.1: Performance – batch-load accounts and categories
        // in two queries instead of N+1 per-row lookups.
        Set<Long> accountIds =
                transactions.stream()
                        .flatMap(
                                t ->
                                        t.getToAccountId() != null
                                                ? java.util.stream.Stream.of(
                                                        t.getAccountId(), t.getToAccountId())
                                                : java.util.stream.Stream.of(t.getAccountId()))
                        .collect(Collectors.toSet());

        Map<Long, Account> accountMap =
                accountIds.isEmpty()
                        ? Map.of()
                        : accountRepository.findAllByIds(accountIds).stream()
                                .collect(Collectors.toMap(Account::getId, Function.identity()));

        Set<Long> categoryIds =
                transactions.stream()
                        .filter(t -> t.getCategoryId() != null)
                        .map(Transaction::getCategoryId)
                        .collect(Collectors.toSet());

        Map<Long, Category> categoryMap =
                categoryIds.isEmpty()
                        ? Map.of()
                        : categoryRepository.findAllByIds(categoryIds).stream()
                                .collect(Collectors.toMap(Category::getId, Function.identity()));

        List<TransactionResponse> responses =
                transactions.stream()
                        .map(
                                transaction -> {
                                    TransactionResponse response =
                                            transactionMapper.toResponse(transaction);

                                    // Fields already decrypted by JPA converter
                                    if (transaction.getDescription() != null
                                            && !transaction.getDescription().isBlank()) {
                                        response.setDescription(transaction.getDescription());
                                    }
                                    if (transaction.getNotes() != null
                                            && !transaction.getNotes().isBlank()) {
                                        response.setNotes(transaction.getNotes());
                                    }
                                    // Payee is stored in plaintext and should NOT be decrypted
                                    response.setPayee(transaction.getPayee());

                                    // Populate denormalized account name using pre-loaded map (no
                                    // extra query)
                                    Account account = accountMap.get(transaction.getAccountId());
                                    if (account != null) {
                                        if (account.getName() != null
                                                && !account.getName().isBlank()) {
                                            response.setAccountName(account.getName());
                                        }
                                    } else {
                                        response.setAccountName(
                                                "Account #" + transaction.getAccountId());
                                    }

                                    // Populate denormalized destination account name for transfers
                                    if (transaction.getToAccountId() != null) {
                                        Account toAccount =
                                                accountMap.get(transaction.getToAccountId());
                                        if (toAccount != null
                                                && toAccount.getName() != null
                                                && !toAccount.getName().isBlank()) {
                                            response.setToAccountName(toAccount.getName());
                                        }
                                    }

                                    // Populate denormalized category fields using pre-loaded map
                                    // (no extra query)
                                    if (transaction.getCategoryId() != null) {
                                        Category category =
                                                categoryMap.get(transaction.getCategoryId());
                                        if (category != null) {
                                            String categoryName = category.getName();
                                            response.setCategoryName(categoryName);
                                            response.setCategoryIcon(category.getIcon());
                                            response.setCategoryColor(category.getColor());
                                        }
                                    }

                                    return response;
                                })
                        .collect(Collectors.toList());

        log.debug("Retrieved {} recent transactions for user {}", responses.size(), userId);

        return responses;
    }

    /**
     * Calculates cash flow for the specified user and time period.
     *
     * <p>Cash flow shows the balance between income and expenses:
     *
     * <ul>
     *   <li><b>Income:</b> Sum of all INCOME transactions in the period
     *   <li><b>Expenses:</b> Sum of all EXPENSE transactions in the period
     *   <li><b>Net Cash Flow:</b> Income - Expenses (positive = surplus, negative = deficit)
     * </ul>
     *
     * <p><b>Requirement REQ-2.8.1.2:</b> Display monthly income vs. expenses
     *
     * @param userId the ID of the user
     * @param period the time period in days (e.g., 30 for last 30 days)
     * @return map with keys "income", "expenses", "netCashFlow" (all as BigDecimal)
     * @throws IllegalArgumentException if userId is null or period <= 0
     */
    @Cacheable(value = "cashFlow", key = "#userId + '_' + #period")
    public Map<String, BigDecimal> getCashFlow(Long userId, int period) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (period <= 0) {
            throw new IllegalArgumentException("Period must be positive");
        }

        log.debug("Calculating cash flow for user {} over {} days", userId, period);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(period);

        // Get all transactions in the period
        List<Transaction> transactions =
                transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate);

        // Look up user's base currency for conversion
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("User not found: " + userId));
        final String cashFlowBase =
                (user.getBaseCurrency() != null && !user.getBaseCurrency().isBlank())
                        ? user.getBaseCurrency()
                        : "USD";

        // Calculate income and expenses (convert each transaction to base currency).
        // Internal transfer legs (transferId != null) are excluded — moving money between
        // your own accounts is not real income or expense.
        BigDecimal income =
                transactions.stream()
                        .filter(
                                t ->
                                        t.getType() == TransactionType.INCOME
                                                && !t.getIsDeleted()
                                                && t.getTransferId() == null)
                        .map(t -> convertToBase(t.getAmount(), t.getCurrency(), cashFlowBase))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expenses =
                transactions.stream()
                        .filter(
                                t ->
                                        t.getType() == TransactionType.EXPENSE
                                                && !t.getIsDeleted()
                                                && t.getTransferId() == null)
                        .map(t -> convertToBase(t.getAmount(), t.getCurrency(), cashFlowBase))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netCashFlow = income.subtract(expenses);

        Map<String, BigDecimal> cashFlow = new HashMap<>();
        cashFlow.put("income", income);
        cashFlow.put("expenses", expenses);
        cashFlow.put("netCashFlow", netCashFlow);

        log.info(
                "Cash flow for user {} ({}d): income={}, expenses={}, net={}",
                userId,
                period,
                income,
                expenses,
                netCashFlow);

        return cashFlow;
    }

    /**
     * Calculates cash flow for the specified user between two explicit dates. Not cached – used for
     * custom date range requests.
     */
    public Map<String, BigDecimal> getCashFlow(
            Long userId, LocalDate startDate, LocalDate endDate) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates cannot be null");
        }

        log.debug("Calculating cash flow for user {} from {} to {}", userId, startDate, endDate);

        List<Transaction> transactions =
                transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate);

        User cfUser =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("User not found: " + userId));
        final String cfBase =
                (cfUser.getBaseCurrency() != null && !cfUser.getBaseCurrency().isBlank())
                        ? cfUser.getBaseCurrency()
                        : "USD";

        // Internal transfer legs (transferId != null) are excluded from cash flow.
        BigDecimal income =
                transactions.stream()
                        .filter(
                                t ->
                                        t.getType() == TransactionType.INCOME
                                                && !t.getIsDeleted()
                                                && t.getTransferId() == null)
                        .map(t -> convertToBase(t.getAmount(), t.getCurrency(), cfBase))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expenses =
                transactions.stream()
                        .filter(
                                t ->
                                        t.getType() == TransactionType.EXPENSE
                                                && !t.getIsDeleted()
                                                && t.getTransferId() == null)
                        .map(t -> convertToBase(t.getAmount(), t.getCurrency(), cfBase))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> cashFlow = new HashMap<>();
        cashFlow.put("income", income);
        cashFlow.put("expenses", expenses);
        cashFlow.put("netCashFlow", income.subtract(expenses));

        log.info(
                "Cash flow for user {} ({} to {}): income={}, expenses={}, net={}",
                userId,
                startDate,
                endDate,
                income,
                expenses,
                income.subtract(expenses));

        return cashFlow;
    }

    /**
     * Calculates daily cash flow for a specific month.
     *
     * @param userId the ID of the user
     * @param year the year
     * @param month the month (1-12)
     * @return list of daily cash flows
     */
    public List<org.openfinance.dto.DailyCashFlow> getDailyCashFlow(
            Long userId, int year, int month) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        log.debug(
                "Calculating daily cash flow for user {} for year {} month {}",
                userId,
                year,
                month);

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<Transaction> transactions =
                transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate);

        // Look up the user's base currency so each transaction is converted before aggregation
        // (consistent with getCashFlow, which also converts per-row).
        User dailyUser =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("User not found: " + userId));
        final String dailyBase =
                (dailyUser.getBaseCurrency() != null && !dailyUser.getBaseCurrency().isBlank())
                        ? dailyUser.getBaseCurrency()
                        : "USD";

        Map<LocalDate, BigDecimal> dailyIncome = new HashMap<>();
        Map<LocalDate, BigDecimal> dailyExpense = new HashMap<>();

        for (int i = 1; i <= yearMonth.lengthOfMonth(); i++) {
            LocalDate date = yearMonth.atDay(i);
            dailyIncome.put(date, BigDecimal.ZERO);
            dailyExpense.put(date, BigDecimal.ZERO);
        }

        for (Transaction t : transactions) {
            // Exclude internal transfer legs — they are not real income/expense.
            if (t.getIsDeleted() || t.getTransferId() != null) {
                continue;
            }
            LocalDate date = t.getDate();
            BigDecimal converted = convertToBase(t.getAmount(), t.getCurrency(), dailyBase);
            if (t.getType() == TransactionType.INCOME) {
                dailyIncome.put(date, dailyIncome.get(date).add(converted));
            } else if (t.getType() == TransactionType.EXPENSE) {
                dailyExpense.put(date, dailyExpense.get(date).add(converted));
            }
        }

        List<org.openfinance.dto.DailyCashFlow> result = new ArrayList<>();
        for (int i = 1; i <= yearMonth.lengthOfMonth(); i++) {
            LocalDate date = yearMonth.atDay(i);
            result.add(
                    new org.openfinance.dto.DailyCashFlow(
                            date, dailyIncome.get(date), dailyExpense.get(date)));
        }

        return result;
    }

    /**
     * Calculates spending breakdown by category for the specified user and time period.
     *
     * <p>This method groups all EXPENSE transactions by category and sums the amounts. Only
     * non-deleted expense transactions are included. Results are sorted by amount descending
     * (highest spending first).
     *
     * <p><b>Requirement REQ-2.8.1.1:</b> Dashboard displays spending by category
     *
     * @param userId the ID of the user
     * @param period the time period in days (e.g., 30 for last 30 days)
     * @return map of category name to total spending amount, sorted by amount descending
     * @throws IllegalArgumentException if userId is null or period <= 0
     */
    @Cacheable(value = "spendingByCategory", key = "#userId + '_' + #period")
    public Map<String, BigDecimal> getSpendingByCategory(Long userId, int period) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (period <= 0) {
            throw new IllegalArgumentException("Period must be positive");
        }

        log.debug("Calculating spending by category for user {} over {} days", userId, period);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(period);

        // Get all expense transactions in the period (excluding internal transfer legs,
        // which are not real spending).
        List<Transaction> expenses =
                transactionRepository
                        .findByUserIdAndDateBetween(userId, startDate, endDate)
                        .stream()
                        .filter(
                                t ->
                                        t.getType() == TransactionType.EXPENSE
                                                && !t.getIsDeleted()
                                                && t.getTransferId() == null)
                        .collect(Collectors.toList());

        // Group by category and sum amounts
        Map<String, BigDecimal> spendingByCategory =
                expenses.stream()
                        .collect(
                                Collectors.groupingBy(
                                        t ->
                                                t.getCategoryId() != null
                                                        ? "Category_" + t.getCategoryId()
                                                        : "Uncategorized",
                                        Collectors.reducing(
                                                BigDecimal.ZERO,
                                                Transaction::getAmount,
                                                BigDecimal::add)));

        // Sort by amount descending
        Map<String, BigDecimal> sortedSpending =
                spendingByCategory.entrySet().stream()
                        .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        (e1, e2) -> e1,
                                        java.util.LinkedHashMap::new));

        log.info(
                "Spending by category for user {} ({}d): {} categories, total={}",
                userId,
                period,
                sortedSpending.size(),
                sortedSpending.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));

        return sortedSpending;
    }

    /**
     * Calculates spending breakdown by category between two explicit dates. Not cached – used for
     * custom date range requests.
     */
    public Map<String, BigDecimal> getSpendingByCategory(
            Long userId, LocalDate startDate, LocalDate endDate) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates cannot be null");
        }

        log.debug(
                "Calculating spending by category for user {} from {} to {}",
                userId,
                startDate,
                endDate);

        // Exclude internal transfer legs — they are not real spending.
        List<Transaction> expenses =
                transactionRepository
                        .findByUserIdAndDateBetween(userId, startDate, endDate)
                        .stream()
                        .filter(
                                t ->
                                        t.getType() == TransactionType.EXPENSE
                                                && !t.getIsDeleted()
                                                && t.getTransferId() == null)
                        .collect(Collectors.toList());

        Map<String, BigDecimal> spendingByCategory =
                expenses.stream()
                        .collect(
                                Collectors.groupingBy(
                                        t ->
                                                t.getCategoryId() != null
                                                        ? "Category_" + t.getCategoryId()
                                                        : "Uncategorized",
                                        Collectors.reducing(
                                                BigDecimal.ZERO,
                                                Transaction::getAmount,
                                                BigDecimal::add)));

        return spendingByCategory.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (a, b) -> a,
                                java.util.LinkedHashMap::new));
    }

    /**
     * Builds Cashflow Sankey diagram data for the specified user and time period.
     *
     * <p>Groups INCOME transactions by category (or "Uncategorized") to produce income source
     * nodes, and groups EXPENSE transactions by resolved category name to produce expense category
     * nodes. Surplus (or deficit) is computed as totalIncome − totalExpenses.
     *
     * @param userId the ID of the user
     * @param period the time period in days (e.g., 30 for last 30 days)
     * @param encryptionKey the AES-256 key for decrypting user category names
     * @return CashflowSankeyDto with income sources, expense categories and surplus
     * @throws IllegalArgumentException if userId is null or period &lt;= 0
     */
    @Cacheable(value = "cashflowSankey", key = "#userId + '_' + #period")
    public CashflowSankeyDto getCashflowSankey(Long userId, int period) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (period <= 0) {
            throw new IllegalArgumentException("Period must be positive");
        }

        log.debug("Building cashflow sankey for user {} over {} days", userId, period);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(period);
        return buildCashflowSankey(userId, startDate, endDate, period);
    }

    /**
     * Builds Cashflow Sankey diagram data between two explicit dates. Not cached – used for custom
     * date range requests.
     */
    public CashflowSankeyDto getCashflowSankey(
            Long userId, LocalDate startDate, LocalDate endDate) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates cannot be null");
        }
        int period = (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        return buildCashflowSankey(userId, startDate, endDate, period);
    }

    private CashflowSankeyDto buildCashflowSankey(
            Long userId, LocalDate startDate, LocalDate endDate, int period) {
        List<Transaction> transactions =
                transactionRepository
                        .findByUserIdAndDateBetween(userId, startDate, endDate)
                        .stream()
                        .filter(t -> !t.getIsDeleted())
                        .collect(Collectors.toList());

        // Look up base currency for conversion
        User sankeyUser =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("User not found: " + userId));
        final String sankeyBase =
                (sankeyUser.getBaseCurrency() != null && !sankeyUser.getBaseCurrency().isBlank())
                        ? sankeyUser.getBaseCurrency()
                        : "USD";

        // ── Income sources grouped by category (internal transfers excluded) ─────
        Map<String, BigDecimal> incomeByCategoryKey =
                transactions.stream()
                        .filter(
                                t ->
                                        t.getType() == TransactionType.INCOME
                                                && t.getTransferId() == null)
                        .collect(
                                Collectors.groupingBy(
                                        t ->
                                                t.getCategoryId() != null
                                                        ? "Category_" + t.getCategoryId()
                                                        : "Uncategorized",
                                        Collectors.reducing(
                                                BigDecimal.ZERO,
                                                t ->
                                                        convertToBase(
                                                                t.getAmount(),
                                                                t.getCurrency(),
                                                                sankeyBase),
                                                BigDecimal::add)));

        // ── Expense categories grouped by category (internal transfers excluded) ─
        Map<String, BigDecimal> expenseByCategoryKey =
                transactions.stream()
                        .filter(
                                t ->
                                        t.getType() == TransactionType.EXPENSE
                                                && t.getTransferId() == null)
                        .collect(
                                Collectors.groupingBy(
                                        t ->
                                                t.getCategoryId() != null
                                                        ? "Category_" + t.getCategoryId()
                                                        : "Uncategorized",
                                        Collectors.reducing(
                                                BigDecimal.ZERO,
                                                t ->
                                                        convertToBase(
                                                                t.getAmount(),
                                                                t.getCurrency(),
                                                                sankeyBase),
                                                BigDecimal::add)));

        // ── Resolve category key → display name + color/icon ─────────────────
        java.util.function.Function<String, org.openfinance.entity.Category> resolveCategory =
                key -> {
                    if (key.startsWith("Category_")) {
                        try {
                            Long catId = Long.parseLong(key.substring("Category_".length()));
                            return categoryRepository.findById(catId).orElse(null);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    }
                    return null;
                };

        java.util.function.BiFunction<String, org.openfinance.entity.Category, String> resolveName =
                (key, cat) -> {
                    if (cat == null) return "Uncategorized";
                    if (Boolean.TRUE.equals(cat.getIsSystem())) return cat.getName();
                    if (cat.getName() != null && !cat.getName().isBlank()) {
                        return cat.getName();
                    }
                    return "Uncategorized";
                };

        // Build income source nodes
        List<CashflowSankeyDto.FlowNode> incomeSources =
                incomeByCategoryKey.entrySet().stream()
                        .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                        .map(
                                entry -> {
                                    org.openfinance.entity.Category cat =
                                            resolveCategory.apply(entry.getKey());
                                    String name = resolveName.apply(entry.getKey(), cat);
                                    return CashflowSankeyDto.FlowNode.builder()
                                            .name(name)
                                            .amount(entry.getValue())
                                            .color(cat != null ? cat.getColor() : null)
                                            .icon(cat != null ? cat.getIcon() : null)
                                            .build();
                                })
                        .collect(Collectors.toList());

        // Build expense category nodes
        List<CashflowSankeyDto.FlowNode> expenseCategories =
                expenseByCategoryKey.entrySet().stream()
                        .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                        .map(
                                entry -> {
                                    org.openfinance.entity.Category cat =
                                            resolveCategory.apply(entry.getKey());
                                    String name = resolveName.apply(entry.getKey(), cat);
                                    return CashflowSankeyDto.FlowNode.builder()
                                            .name(name)
                                            .amount(entry.getValue())
                                            .color(cat != null ? cat.getColor() : null)
                                            .icon(cat != null ? cat.getIcon() : null)
                                            .build();
                                })
                        .collect(Collectors.toList());

        BigDecimal totalIncome =
                incomeSources.stream()
                        .map(CashflowSankeyDto.FlowNode::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpenses =
                expenseCategories.stream()
                        .map(CashflowSankeyDto.FlowNode::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal surplus = totalIncome.subtract(totalExpenses);

        log.info(
                "Cashflow sankey for user {} ({}d): income={}, expenses={}, surplus={}",
                userId,
                period,
                totalIncome,
                totalExpenses,
                surplus);

        return CashflowSankeyDto.builder()
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .surplus(surplus)
                .incomeSources(incomeSources)
                .expenseCategories(expenseCategories)
                .period(period)
                .build();
    }

    /**
     * Gets asset allocation breakdown by type for portfolio visualization.
     *
     * <p>Returns a list of asset allocations showing the distribution of the user's portfolio
     * across different asset types (STOCK, CRYPTO, BOND, etc.). Each allocation includes:
     *
     * <ul>
     *   <li>Asset type and display name
     *   <li>Total value of assets in that type
     *   <li>Percentage of total portfolio
     *   <li>Count of individual assets
     * </ul>
     *
     * <p><b>Task 4.3.6:</b> Asset allocation chart component data
     *
     * <p><b>Requirement REQ-2.6.3:</b> Portfolio analytics and visualization
     *
     * @param userId the ID of the user
     * @return list of asset allocations sorted by value descending
     * @throws IllegalArgumentException if userId is null
     */
    @Cacheable(value = "assetAllocation", key = "#userId")
    public List<AssetAllocation> getAssetAllocation(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        log.debug("Calculating asset allocation for user {}", userId);

        // Get all assets for the user
        List<Asset> assets = assetRepository.findByUserId(userId);

        if (assets.isEmpty()) {
            log.debug("No assets found for user {}", userId);
            return List.of();
        }

        // Get user's base currency
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User not found with ID: " + userId));
        String baseCurrency = user.getBaseCurrency() != null ? user.getBaseCurrency() : "EUR";

        // Calculate total portfolio value (converted to base currency)
        BigDecimal totalPortfolioValue =
                assets.stream()
                        .map(a -> convertToBase(a.getTotalValue(), a.getCurrency(), baseCurrency))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPortfolioValue.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("Total portfolio value is zero for user {}", userId);
            return List.of();
        }

        // Group assets by type and calculate allocation
        Map<AssetType, List<Asset>> assetsByType =
                assets.stream().collect(Collectors.groupingBy(Asset::getType));

        List<AssetAllocation> allocations =
                assetsByType.entrySet().stream()
                        .map(
                                entry -> {
                                    AssetType type = entry.getKey();
                                    List<Asset> typeAssets = entry.getValue();

                                    // Calculate total value for this asset type
                                    // (converted to base currency)
                                    BigDecimal typeValue =
                                            typeAssets.stream()
                                                    .map(
                                                            a ->
                                                                    convertToBase(
                                                                            a.getTotalValue(),
                                                                            a.getCurrency(),
                                                                            baseCurrency))
                                                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                                    // Calculate percentage
                                    BigDecimal percentage =
                                            typeValue
                                                    .divide(
                                                            totalPortfolioValue,
                                                            4,
                                                            RoundingMode.HALF_UP)
                                                    .multiply(BigDecimal.valueOf(100))
                                                    .setScale(2, RoundingMode.HALF_UP);

                                    return AssetAllocation.builder()
                                            .type(type)
                                            .typeName(type.getDisplayName())
                                            .totalValue(typeValue)
                                            .percentage(percentage)
                                            .assetCount(typeAssets.size())
                                            .currency(baseCurrency)
                                            .build();
                                })
                        .sorted(
                                (a, b) ->
                                        b.getTotalValue()
                                                .compareTo(a.getTotalValue())) // Sort by value
                        // descending
                        .collect(Collectors.toList());

        log.info(
                "Asset allocation calculated for user {}: {} asset types, total value {}",
                userId,
                allocations.size(),
                totalPortfolioValue);

        return allocations;
    }

    /**
     * Gets portfolio performance metrics for dashboard cards with sparkline data.
     *
     * <p>Returns a list of performance metrics including:
     *
     * <ul>
     *   <li>Total portfolio value
     *   <li>Unrealized gains/losses
     *   <li>30-day return
     *   <li>Year-to-date return
     * </ul>
     *
     * Each metric includes historical data points for sparkline visualization.
     *
     * <p><b>Task 4.3.8:</b> Portfolio performance cards component data
     *
     * <p><b>Requirement REQ-2.6.3:</b> Portfolio performance analytics
     *
     * @param userId the ID of the user
     * @param period the number of days for sparkline history (default: 30)
     * @return list of portfolio performance metrics with sparkline data
     * @throws IllegalArgumentException if userId is null
     */
    @Cacheable(value = "portfolioPerformance", key = "#userId + '_' + #period")
    public List<PortfolioPerformance> getPortfolioPerformance(Long userId, int period) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(period);
        return buildPortfolioPerformance(userId, startDate, endDate);
    }

    /**
     * Calculates portfolio performance between two explicit dates. Not cached – used for custom
     * date range requests.
     */
    public List<PortfolioPerformance> getPortfolioPerformance(
            Long userId, LocalDate startDate, LocalDate endDate) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates cannot be null");
        }
        return buildPortfolioPerformance(userId, startDate, endDate);
    }

    private List<PortfolioPerformance> buildPortfolioPerformance(
            Long userId, LocalDate startDate, LocalDate endDate) {
        log.debug(
                "Calculating portfolio performance for user {} from {} to {}",
                userId,
                startDate,
                endDate);

        // Get all assets for the user
        List<Asset> assets = assetRepository.findByUserId(userId);

        if (assets.isEmpty()) {
            log.debug("No assets found for user {}", userId);
            return List.of();
        }

        // Get user's base currency
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User not found with ID: " + userId));
        String baseCurrency = user.getBaseCurrency() != null ? user.getBaseCurrency() : "EUR";

        // Calculate total portfolio value and cost (converted to base currency)
        BigDecimal totalValue =
                assets.stream()
                        .map(a -> convertToBase(a.getTotalValue(), a.getCurrency(), baseCurrency))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCost =
                assets.stream()
                        .map(a -> convertToBase(a.getTotalCost(), a.getCurrency(), baseCurrency))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unrealizedGain = totalValue.subtract(totalCost);
        BigDecimal unrealizedGainPercentage =
                totalCost.compareTo(BigDecimal.ZERO) > 0
                        ? unrealizedGain
                                .divide(totalCost, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        // Get net worth history for sparkline data over the requested period
        List<NetWorth> netWorthHistory =
                netWorthService.getNetWorthHistory(userId, startDate, endDate);

        // Convert to sparkline data points, filtering out zero-value stale snapshots
        List<PortfolioPerformance.HistoricalDataPoint> sparklineData =
                netWorthHistory.stream()
                        .filter(nw -> nw.getTotalAssets().compareTo(BigDecimal.ZERO) > 0)
                        .map(
                                nw ->
                                        PortfolioPerformance.HistoricalDataPoint.builder()
                                                .date(nw.getSnapshotDate())
                                                .value(nw.getTotalAssets())
                                                .build())
                        .collect(Collectors.toList());

        // Calculate period change (null when no meaningful comparison baseline)
        BigDecimal periodChange = BigDecimal.ZERO;
        BigDecimal periodChangePercentage = BigDecimal.ZERO;
        if (sparklineData.size() >= 2) {
            BigDecimal oldValue = sparklineData.get(0).getValue();
            BigDecimal newValue = sparklineData.get(sparklineData.size() - 1).getValue();
            if (oldValue.compareTo(BigDecimal.ZERO) > 0) {
                periodChange = newValue.subtract(oldValue);
                periodChangePercentage =
                        periodChange
                                .divide(oldValue, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);
            }
        }

        // Build performance metrics
        List<PortfolioPerformance> performances =
                List.of(
                        // Total Value
                        PortfolioPerformance.builder()
                                .label("Total Value")
                                .currentValue(totalValue)
                                .changeAmount(periodChange)
                                .changePercentage(periodChangePercentage)
                                .currency(baseCurrency)
                                .sparklineData(sparklineData)
                                .build(),

                        // Unrealized Gain/Loss
                        PortfolioPerformance.builder()
                                .label("Unrealized Gain")
                                .currentValue(unrealizedGain)
                                .changeAmount(unrealizedGain)
                                .changePercentage(unrealizedGainPercentage)
                                .currency(baseCurrency)
                                .sparklineData(List.of()) // No historical data for gain/loss yet
                                .build(),

                        // Total Cost Basis
                        PortfolioPerformance.builder()
                                .label("Cost Basis")
                                .currentValue(totalCost)
                                .changeAmount(BigDecimal.ZERO)
                                .changePercentage(BigDecimal.ZERO)
                                .currency(baseCurrency)
                                .sparklineData(List.of())
                                .build());

        log.info(
                "Portfolio performance calculated for user {}: total value {}, unrealized gain {}",
                userId,
                totalValue,
                unrealizedGain);

        return performances;
    }

    /**
     * Maps an Account entity to an AccountSummary DTO with decrypted fields.
     *
     * @param account the account entity
     * @param encryptionKey the encryption key for decryption
     * @return account summary with decrypted data
     */
    private AccountSummary mapToAccountSummary(Account account) {
        // Fields already decrypted by JPA converter
        String decryptedName = account.getName();
        String decryptedDescription = account.getDescription();

        return AccountSummary.builder()
                .id(account.getId())
                .name(decryptedName)
                .type(account.getType())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .isActive(account.getIsActive())
                .description(decryptedDescription)
                .build();
    }

    /**
     * Gets borrowing capacity analysis for the specified user.
     *
     * <p>Calculates borrowing capacity based on debt-to-income ratio:
     *
     * <ul>
     *   <li>Monthly income: Average INCOME transactions over analysis period
     *   <li>Monthly expenses: Average EXPENSE transactions over analysis period
     *   <li>Monthly debt payments: Sum of minimum payments on all liabilities
     *   <li>Debt-to-income ratio: (monthlyDebtPayments / monthlyIncome) * 100
     *   <li>Available capacity: Based on maintaining 40% DTI threshold
     * </ul>
     *
     * <p><b>Financial Health Status:</b>
     *
     * <ul>
     *   <li>EXCELLENT: DTI ≤ 20%
     *   <li>GOOD: DTI 21-35%
     *   <li>FAIR: DTI 36-50%
     *   <li>POOR: DTI > 50%
     * </ul>
     *
     * @param userId the ID of the user
     * @param analysisPeriod the period in days to analyze income/expenses (default: 90)
     * @param encryptionKey the encryption key for decrypting liability data
     * @return borrowing capacity with debt metrics and recommendations
     * @throws IllegalArgumentException if userId is null or analysisPeriod <= 0
     */
    @Cacheable(value = "borrowingCapacity", key = "#userId + '_' + #analysisPeriod")
    public BorrowingCapacity getBorrowingCapacity(Long userId, int analysisPeriod) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (analysisPeriod <= 0) {
            throw new IllegalArgumentException("Analysis period must be positive");
        }
        log.debug(
                "Calculating borrowing capacity for user {} over {} days", userId, analysisPeriod);

        // Get user's base currency
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User not found with ID: " + userId));
        String baseCurrency = user.getBaseCurrency() != null ? user.getBaseCurrency() : "EUR";

        // Calculate date range
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(analysisPeriod);

        // Get all transactions in the period
        List<Transaction> transactions =
                transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate);

        // Calculate total income and expenses (convert to user's base currency).
        // Internal transfer legs (transferId != null) are excluded.
        BigDecimal totalIncome =
                transactions.stream()
                        .filter(
                                t ->
                                        t.getType() == TransactionType.INCOME
                                                && !t.getIsDeleted()
                                                && t.getTransferId() == null)
                        .map(t -> convertToBase(t.getAmount(), t.getCurrency(), baseCurrency))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpenses =
                transactions.stream()
                        .filter(
                                t ->
                                        t.getType() == TransactionType.EXPENSE
                                                && !t.getIsDeleted()
                                                && t.getTransferId() == null)
                        .map(t -> convertToBase(t.getAmount(), t.getCurrency(), baseCurrency))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate average monthly income and expenses
        BigDecimal monthsInPeriod =
                BigDecimal.valueOf(analysisPeriod)
                        .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);

        BigDecimal monthlyIncome =
                monthsInPeriod.compareTo(BigDecimal.ZERO) > 0
                        ? totalIncome.divide(monthsInPeriod, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        BigDecimal monthlyExpenses =
                monthsInPeriod.compareTo(BigDecimal.ZERO) > 0
                        ? totalExpenses.divide(monthsInPeriod, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        // Get all liabilities and calculate total monthly debt payments (converted to
        // base currency)
        List<Liability> liabilities = liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId);

        BigDecimal monthlyDebtPayments =
                liabilities.stream()
                        .filter(
                                l ->
                                        l.getMinimumPayment() != null
                                                && !l.getMinimumPayment().isBlank())
                        .map(
                                l -> {
                                    try {
                                        BigDecimal rawPayment =
                                                new BigDecimal(l.getMinimumPayment());
                                        return convertToBase(
                                                rawPayment, l.getCurrency(), baseCurrency);
                                    } catch (Exception e) {
                                        log.warn(
                                                "Failed to parse minimum payment for liability id={}",
                                                l.getId(),
                                                e);
                                        return BigDecimal.ZERO;
                                    }
                                })
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate debt-to-income ratio
        BigDecimal debtToIncomeRatio =
                monthlyIncome.compareTo(BigDecimal.ZERO) > 0
                        ? monthlyDebtPayments
                                .divide(monthlyIncome, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        // Calculate recommended max borrowing (40% DTI threshold)
        BigDecimal maxDebtAt40Percent = monthlyIncome.multiply(BigDecimal.valueOf(0.40));
        BigDecimal recommendedMaxBorrowing =
                maxDebtAt40Percent
                        .subtract(monthlyDebtPayments)
                        .max(BigDecimal.ZERO); // Cannot be negative

        // Estimate available borrowing capacity (simplified: 10 years at recommended
        // monthly payment)
        // This is a rough estimate; actual borrowing depends on interest rates and loan
        // terms
        BigDecimal availableBorrowingCapacity =
                recommendedMaxBorrowing
                        .multiply(BigDecimal.valueOf(12)) // Annual
                        .multiply(BigDecimal.valueOf(10)) // 10-year term
                        .setScale(2, RoundingMode.HALF_UP);

        // Determine financial health status
        // BUG-002 fix: When no income data, return INSUFFICIENT_DATA instead of falsely
        // EXCELLENT
        String financialHealthStatus;
        if (monthlyIncome.compareTo(BigDecimal.ZERO) == 0) {
            financialHealthStatus = "INSUFFICIENT_DATA";
        } else if (debtToIncomeRatio.compareTo(BigDecimal.valueOf(20)) <= 0) {
            financialHealthStatus = "EXCELLENT";
        } else if (debtToIncomeRatio.compareTo(BigDecimal.valueOf(35)) <= 0) {
            financialHealthStatus = "GOOD";
        } else if (debtToIncomeRatio.compareTo(BigDecimal.valueOf(50)) <= 0) {
            financialHealthStatus = "FAIR";
        } else {
            financialHealthStatus = "POOR";
        }

        BorrowingCapacity capacity =
                BorrowingCapacity.builder()
                        .monthlyIncome(monthlyIncome)
                        .monthlyExpenses(monthlyExpenses)
                        .monthlyDebtPayments(monthlyDebtPayments)
                        .debtToIncomeRatio(debtToIncomeRatio)
                        .recommendedMaxBorrowing(recommendedMaxBorrowing)
                        .availableBorrowingCapacity(availableBorrowingCapacity)
                        .financialHealthStatus(financialHealthStatus)
                        .currency(baseCurrency)
                        .analysisPeriod(analysisPeriod)
                        .build();

        log.info(
                "Borrowing capacity for user {}: DTI={}%, status={}, available={}",
                userId, debtToIncomeRatio, financialHealthStatus, availableBorrowingCapacity);

        return capacity;
    }

    /**
     * Calculates borrowing capacity between two explicit dates. Not cached – used for custom date
     * range requests.
     */
    public BorrowingCapacity getBorrowingCapacity(
            Long userId, LocalDate startDate, LocalDate endDate) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates cannot be null");
        }
        int analysisPeriod = (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        log.debug(
                "Calculating borrowing capacity for user {} from {} to {}",
                userId,
                startDate,
                endDate);

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User not found with ID: " + userId));
        String baseCurrency = user.getBaseCurrency() != null ? user.getBaseCurrency() : "EUR";

        List<Transaction> transactions =
                transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate);

        // Internal transfer legs (transferId != null) are excluded.
        BigDecimal totalIncome =
                transactions.stream()
                        .filter(
                                t ->
                                        t.getType() == TransactionType.INCOME
                                                && !t.getIsDeleted()
                                                && t.getTransferId() == null)
                        .map(t -> convertToBase(t.getAmount(), t.getCurrency(), baseCurrency))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpenses =
                transactions.stream()
                        .filter(
                                t ->
                                        t.getType() == TransactionType.EXPENSE
                                                && !t.getIsDeleted()
                                                && t.getTransferId() == null)
                        .map(t -> convertToBase(t.getAmount(), t.getCurrency(), baseCurrency))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthsInPeriod =
                analysisPeriod > 0
                        ? BigDecimal.valueOf(analysisPeriod)
                                .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ONE;

        BigDecimal monthlyIncome = totalIncome.divide(monthsInPeriod, 2, RoundingMode.HALF_UP);
        BigDecimal monthlyExpenses = totalExpenses.divide(monthsInPeriod, 2, RoundingMode.HALF_UP);

        List<Liability> liabilities = liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId);
        BigDecimal monthlyDebtPayments =
                liabilities.stream()
                        .filter(
                                l ->
                                        l.getMinimumPayment() != null
                                                && !l.getMinimumPayment().isBlank())
                        .map(
                                l -> {
                                    try {
                                        BigDecimal rawPayment =
                                                new BigDecimal(l.getMinimumPayment());
                                        return convertToBase(
                                                rawPayment, l.getCurrency(), baseCurrency);
                                    } catch (Exception e) {
                                        log.warn(
                                                "Failed to parse minimum payment for liability id={}",
                                                l.getId(),
                                                e);
                                        return BigDecimal.ZERO;
                                    }
                                })
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal debtToIncomeRatio =
                monthlyIncome.compareTo(BigDecimal.ZERO) > 0
                        ? monthlyDebtPayments
                                .divide(monthlyIncome, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        BigDecimal maxDebtAt40Percent = monthlyIncome.multiply(BigDecimal.valueOf(0.40));
        BigDecimal recommendedMaxBorrowing =
                maxDebtAt40Percent.subtract(monthlyDebtPayments).max(BigDecimal.ZERO);
        BigDecimal availableBorrowingCapacity =
                recommendedMaxBorrowing
                        .multiply(BigDecimal.valueOf(12))
                        .multiply(BigDecimal.valueOf(10))
                        .setScale(2, RoundingMode.HALF_UP);

        String financialHealthStatus;
        if (monthlyIncome.compareTo(BigDecimal.ZERO) == 0) {
            financialHealthStatus = "INSUFFICIENT_DATA";
        } else if (debtToIncomeRatio.compareTo(BigDecimal.valueOf(20)) <= 0) {
            financialHealthStatus = "EXCELLENT";
        } else if (debtToIncomeRatio.compareTo(BigDecimal.valueOf(35)) <= 0) {
            financialHealthStatus = "GOOD";
        } else if (debtToIncomeRatio.compareTo(BigDecimal.valueOf(50)) <= 0) {
            financialHealthStatus = "FAIR";
        } else {
            financialHealthStatus = "POOR";
        }

        return BorrowingCapacity.builder()
                .monthlyIncome(monthlyIncome)
                .monthlyExpenses(monthlyExpenses)
                .monthlyDebtPayments(monthlyDebtPayments)
                .debtToIncomeRatio(debtToIncomeRatio)
                .recommendedMaxBorrowing(recommendedMaxBorrowing)
                .availableBorrowingCapacity(availableBorrowingCapacity)
                .financialHealthStatus(financialHealthStatus)
                .currency(baseCurrency)
                .analysisPeriod(analysisPeriod)
                .build();
    }

    /**
     * Gets net worth allocation breakdown for portfolio visualization.
     *
     * <p>Returns the distribution of the user's net worth across different categories:
     *
     * <ul>
     *   <li>Cash & Savings: CHECKING, SAVINGS, CASH accounts
     *   <li>Investments: INVESTMENT, BROKERAGE accounts + investment assets
     *   <li>Real Estate: REAL_ESTATE assets and properties
     *   <li>Other Assets: Remaining asset types
     *   <li>Mortgages: MORTGAGE liabilities
     *   <li>Loans: LOAN, AUTO_LOAN, STUDENT_LOAN liabilities
     *   <li>Credit Cards: CREDIT_CARD liabilities
     *   <li>Other Liabilities: Remaining liability types
     * </ul>
     *
     * @param userId the ID of the user
     * @param encryptionKey the encryption key for decrypting sensitive data
     * @return list of net worth allocations sorted by absolute value descending
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    @Cacheable(value = "networthAllocation", key = "#userId")
    public List<NetWorthAllocation> getNetWorthAllocation(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Calculating net worth allocation for user {}", userId);

        // Get user's base currency
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User not found with ID: " + userId));
        String baseCurrency = user.getBaseCurrency() != null ? user.getBaseCurrency() : "EUR";

        Map<String, NetWorthAllocation.NetWorthAllocationBuilder> categoryMap = new HashMap<>();

        // Process accounts
        List<Account> accounts = accountRepository.findByUserIdAndIsActive(userId, true);
        for (Account account : accounts) {
            String category = categorizeAccount(account.getType());
            // Credit card accounts with negative balance are liabilities
            boolean isLiabilityAccount =
                    account.getType() == AccountType.CREDIT_CARD
                            && account.getBalance().compareTo(BigDecimal.ZERO) < 0;
            categoryMap.computeIfAbsent(
                    category,
                    k ->
                            NetWorthAllocation.builder()
                                    .category(k)
                                    .value(BigDecimal.ZERO)
                                    .itemCount(0)
                                    .isLiability(isLiabilityAccount)
                                    .currency(baseCurrency));

            NetWorthAllocation.NetWorthAllocationBuilder builder = categoryMap.get(category);
            BigDecimal convertedBalance =
                    convertToBase(account.getBalance(), account.getCurrency(), baseCurrency);
            builder.value(categoryMap.get(category).build().getValue().add(convertedBalance));
            builder.itemCount(categoryMap.get(category).build().getItemCount() + 1);
        }

        // Process assets
        List<Asset> assets = assetRepository.findByUserId(userId);
        for (Asset asset : assets) {
            String category = categorizeAsset(asset.getType());
            categoryMap.computeIfAbsent(
                    category,
                    k ->
                            NetWorthAllocation.builder()
                                    .category(k)
                                    .value(BigDecimal.ZERO)
                                    .itemCount(0)
                                    .isLiability(false)
                                    .currency(baseCurrency));

            NetWorthAllocation.NetWorthAllocationBuilder builder = categoryMap.get(category);
            BigDecimal currentValue = categoryMap.get(category).build().getValue();
            BigDecimal convertedAssetValue =
                    convertToBase(asset.getTotalValue(), asset.getCurrency(), baseCurrency);
            builder.value(currentValue.add(convertedAssetValue));
            builder.itemCount(categoryMap.get(category).build().getItemCount() + 1);
        }

        // Process liabilities (as negative values)
        List<Liability> liabilities = liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (Liability liability : liabilities) {
            String category = categorizeLiability(liability.getType());
            categoryMap.computeIfAbsent(
                    category,
                    k ->
                            NetWorthAllocation.builder()
                                    .category(k)
                                    .value(BigDecimal.ZERO)
                                    .itemCount(0)
                                    .isLiability(true)
                                    .currency(baseCurrency));

            // Decrypt current balance
            BigDecimal balance = BigDecimal.ZERO;
            try {
                if (liability.getCurrentBalance() != null
                        && !liability.getCurrentBalance().isBlank()) {
                    String decryptedBalance =
                            encryptionService.decrypt(
                                    liability.getCurrentBalance(), EncryptionContext.getKey());
                    balance = new BigDecimal(decryptedBalance);
                }
            } catch (Exception e) {
                log.warn("Failed to decrypt liability balance for id={}", liability.getId(), e);
            }

            NetWorthAllocation.NetWorthAllocationBuilder builder = categoryMap.get(category);
            BigDecimal currentValue = categoryMap.get(category).build().getValue();
            // Liabilities are negative, so we subtract the converted balance
            BigDecimal convertedLiabilityBalance =
                    convertToBase(balance, liability.getCurrency(), baseCurrency);
            builder.value(currentValue.subtract(convertedLiabilityBalance));
            builder.itemCount(categoryMap.get(category).build().getItemCount() + 1);
        }

        // Calculate gross assets (sum of positive category values) for percentage
        // calculations
        // Using gross assets ensures asset percentages sum to ~100%, not net worth
        BigDecimal grossAssets =
                categoryMap.values().stream()
                        .map(b -> b.build().getValue())
                        .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Build final list with percentages
        List<NetWorthAllocation> allocations =
                categoryMap.entrySet().stream()
                        .map(
                                entry -> {
                                    NetWorthAllocation allocation = entry.getValue().build();

                                    // Calculate percentage relative to gross assets
                                    BigDecimal percentage =
                                            grossAssets.compareTo(BigDecimal.ZERO) > 0
                                                    ? allocation
                                                            .getValue()
                                                            .abs()
                                                            .divide(
                                                                    grossAssets,
                                                                    4,
                                                                    RoundingMode.HALF_UP)
                                                            .multiply(BigDecimal.valueOf(100))
                                                            .setScale(2, RoundingMode.HALF_UP)
                                                    : BigDecimal.ZERO;

                                    return NetWorthAllocation.builder()
                                            .category(allocation.getCategory())
                                            .value(allocation.getValue())
                                            .percentage(percentage)
                                            .itemCount(allocation.getItemCount())
                                            .isLiability(allocation.getIsLiability())
                                            .currency(baseCurrency)
                                            .build();
                                })
                        .filter(
                                a ->
                                        a.getValue().abs().compareTo(BigDecimal.ZERO)
                                                > 0) // Exclude zero
                        // balances
                        .sorted(
                                (a, b) ->
                                        b.getValue()
                                                .abs()
                                                .compareTo(a.getValue().abs())) // Sort by absolute
                        // value
                        .collect(Collectors.toList());

        log.info(
                "Net worth allocation for user {}: {} categories, gross assets {}",
                userId,
                allocations.size(),
                grossAssets);

        return allocations;
    }

    /** Categorize account type into allocation category */
    private String categorizeAccount(AccountType type) {
        switch (type) {
            case CHECKING:
            case SAVINGS:
            case CASH:
                return "Cash & Savings";
            case INVESTMENT:
                return "Investments";
            case CREDIT_CARD:
                return "Credit Cards";
            case OTHER:
            default:
                return "Other Assets";
        }
    }

    /** Categorize asset type into allocation category */
    private String categorizeAsset(AssetType type) {
        switch (type) {
            case STOCK:
            case ETF:
            case MUTUAL_FUND:
            case BOND:
            case CRYPTO:
                return "Investments";
            case REAL_ESTATE:
                return "Real Estate";
            case VEHICLE:
                return "Vehicles";
            case JEWELRY:
            case COLLECTIBLE:
            case ELECTRONICS:
            case FURNITURE:
                return "Personal Assets";
            case COMMODITY:
            case OTHER:
            default:
                return "Other Assets";
        }
    }

    /** Categorize liability type into allocation category */
    private String categorizeLiability(LiabilityType type) {
        switch (type) {
            case MORTGAGE:
                return "Mortgages";
            case LOAN:
            case PERSONAL_LOAN:
                return "Loans";
            case CREDIT_CARD:
                return "Credit Cards";
            case OTHER:
            default:
                return "Other Liabilities";
        }
    }

    /**
     * Converts an amount from one currency to the user's base currency. Falls back to the original
     * amount if conversion fails (to avoid breaking charts).
     */
    private BigDecimal convertToBase(BigDecimal amount, String fromCurrency, String baseCurrency) {
        if (fromCurrency == null
                || fromCurrency.isBlank()
                || fromCurrency.equalsIgnoreCase(baseCurrency)) {
            return amount;
        }
        try {
            return exchangeRateService.convert(amount, fromCurrency, baseCurrency);
        } catch (Exception e) {
            log.warn(
                    "Currency conversion failed ({} → {}), using original amount: {}",
                    fromCurrency,
                    baseCurrency,
                    e.getMessage());
            return amount;
        }
    }

    /**
     * Get the estimated interest summary across all interest-bearing accounts.
     *
     * <p>Fetches all active accounts with interest calculation enabled, computes the earned
     * interest and projected interest, and aggregates them.
     *
     * @param userId the ID of the user
     * @param period the period literal (e.g., "1M", "1Y", "30")
     * @param encryptionKey the user's AES-256 key for decrypting account names
     * @return EstimatedInterestSummary containing account list and totals
     */
    public EstimatedInterestSummary getEstimatedInterestSummary(Long userId, String period) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug(
                "Calculating estimated interest summary for user {} and period {}", userId, period);

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User not found with ID: " + userId));
        String baseCurrency = user.getBaseCurrency() != null ? user.getBaseCurrency() : "EUR";

        List<Account> accounts = accountRepository.findByUserIdAndIsActive(userId, true);
        List<AccountInterest> accountInterests = new java.util.ArrayList<>();

        BigDecimal totalEarned = BigDecimal.ZERO;
        BigDecimal totalProjected = BigDecimal.ZERO;

        for (Account account : accounts) {
            if (Boolean.TRUE.equals(account.getIsInterestEnabled())) {
                // Name already decrypted by JPA converter
                String accountName = account.getName();

                BigDecimal earned =
                        interestCalculatorService.calculateHistoricalAccumulated(
                                account.getId(), userId, period);
                BigDecimal projected =
                        interestCalculatorService.calculateInterestEstimate(
                                account.getId(), userId, "1Y");

                accountInterests.add(
                        new AccountInterest(account.getId(), accountName, earned, projected));
                totalEarned = totalEarned.add(earned);
                totalProjected = totalProjected.add(projected);
            }
        }

        // Also include liabilities with interest rates to show interest cost burden
        List<Liability> liabilities = liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (Liability liability : liabilities) {
            if (liability.getInterestRate() == null || liability.getInterestRate().isBlank()) {
                continue;
            }
            try {
                BigDecimal interestRate = new BigDecimal(liability.getInterestRate());
                if (interestRate.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal currentBalance = BigDecimal.ZERO;
                if (liability.getCurrentBalance() != null
                        && !liability.getCurrentBalance().isBlank()) {
                    currentBalance = new BigDecimal(liability.getCurrentBalance());
                }
                BigDecimal annualInterest =
                        currentBalance
                                .multiply(
                                        interestRate.divide(
                                                BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                                .setScale(2, RoundingMode.HALF_UP);
                // Convert to base currency if needed
                annualInterest =
                        convertToBase(annualInterest, liability.getCurrency(), baseCurrency);

                // Period fraction for "earned" (negative = cost)
                BigDecimal periodFraction = computePeriodFraction(period);
                BigDecimal periodInterest =
                        annualInterest
                                .multiply(periodFraction)
                                .setScale(2, RoundingMode.HALF_UP)
                                .negate();

                // Name already decrypted by JPA converter
                String liabilityName = liability.getName();

                accountInterests.add(
                        new AccountInterest(
                                liability.getId(),
                                liabilityName,
                                periodInterest,
                                annualInterest.negate()));
                totalEarned = totalEarned.add(periodInterest);
                totalProjected = totalProjected.add(annualInterest.negate());
            } catch (Exception e) {
                log.warn(
                        "Failed to process interest for liability id={}: {}",
                        liability.getId(),
                        e.getMessage());
            }
        }

        // Sort by highest absolute interest earned/paid
        accountInterests.sort(
                (a, b) -> b.getInterestEarned().abs().compareTo(a.getInterestEarned().abs()));

        return EstimatedInterestSummary.builder()
                .accounts(accountInterests)
                .totalEarned(totalEarned)
                .totalProjected(totalProjected)
                .currency(baseCurrency)
                .build();
    }

    /** Returns fraction of year for a given period string (1M=1/12, 1Y=1, etc.) */
    /**
     * Computes yearly balance variations for the user's accounts, institutions, and total net
     * worth. Year range is determined from the user's earliest transaction date to the latest
     * transaction date (or current year).
     *
     * @param userId the user ID
     * @param encryptionKey the AES-256 encryption key for decrypting account names
     * @return response containing yearly balance data for net worth, accounts, and institutions
     */
    public YearlyBalanceResponse getYearlyBalanceVariations(Long userId) {
        // 1. Determine year range from transactions
        List<Transaction> allTransactions =
                transactionRepository.findByUserId(userId).stream()
                        .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                        .toList();
        if (allTransactions.isEmpty()) {
            return YearlyBalanceResponse.builder()
                    .years(List.of())
                    .netWorth(List.of())
                    .accounts(List.of())
                    .institutions(List.of())
                    .currency(getUserCurrency(userId))
                    .build();
        }

        int minYear =
                allTransactions.stream()
                        .map(t -> t.getDate().getYear())
                        .min(Integer::compareTo)
                        .orElse(LocalDate.now().getYear());
        int maxYear =
                allTransactions.stream()
                        .map(t -> t.getDate().getYear())
                        .max(Integer::compareTo)
                        .orElse(LocalDate.now().getYear());

        List<Integer> years = new ArrayList<>();
        for (int y = minYear; y <= maxYear; y++) {
            years.add(y);
        }

        // 2. Net worth: pick last snapshot per year from NetWorth table
        List<NetWorth> snapshots =
                netWorthService.getNetWorthHistory(
                        userId, LocalDate.of(minYear, 1, 1), LocalDate.of(maxYear, 12, 31));

        Map<Integer, BigDecimal> netWorthByYear = new HashMap<>();
        for (NetWorth nw : snapshots) {
            int year = nw.getSnapshotDate().getYear();
            // Keep latest snapshot per year (snapshots are ordered ASC)
            netWorthByYear.put(year, nw.getNetWorth());
        }

        List<YearlyBalanceResponse.YearlyDataPoint> netWorthPoints = new ArrayList<>();
        BigDecimal previousNw = null;
        for (int year : years) {
            BigDecimal amount = netWorthByYear.getOrDefault(year, BigDecimal.ZERO);
            BigDecimal variation = null;
            if (previousNw != null && previousNw.compareTo(BigDecimal.ZERO) != 0) {
                variation =
                        amount.subtract(previousNw)
                                .multiply(BigDecimal.valueOf(100))
                                .divide(previousNw.abs(), 2, RoundingMode.HALF_UP);
            }
            netWorthPoints.add(
                    YearlyBalanceResponse.YearlyDataPoint.builder()
                            .year(year)
                            .amount(amount)
                            .variationPercentage(variation)
                            .build());
            previousNw = amount;
        }

        // 3. Per-account year-end balances
        List<Account> accounts = accountRepository.findByUserIdAndIsActive(userId, true);
        String baseCurrency = getUserCurrency(userId);

        List<YearlyBalanceResponse.YearlyBalanceEntry> accountEntries = new ArrayList<>();
        // Group transactions by accountId for efficient lookup
        Map<Long, List<Transaction>> txByAccount =
                allTransactions.stream().collect(Collectors.groupingBy(Transaction::getAccountId));

        for (Account account : accounts) {
            BigDecimal currentBalance = account.getBalance();
            if (currentBalance == null) {
                currentBalance = BigDecimal.ZERO;
            }

            // Compute year-end balance for each year by working backwards from current
            // balance_at_year_end = current_balance - net_effect_after_year_end
            List<Transaction> accountTx = txByAccount.getOrDefault(account.getId(), List.of());

            Map<Integer, BigDecimal> yearEndBalances = new HashMap<>();
            for (int year : years) {
                LocalDate yearEnd = LocalDate.of(year, 12, 31);
                // Net effect of this account's own transactions after yearEnd.
                // Transfers are stored as two rows (one per account, each in its
                // own native currency); each account's own row already reflects the
                // transfer's effect on that account — mirroring
                // AccountService.recalculateBalance. We therefore only walk the
                // account's own rows and never the other transfer side, which would
                // double-count and mix currencies (e.g. XOF into an EUR account).
                BigDecimal netEffectAfter = BigDecimal.ZERO;
                for (Transaction tx : accountTx) {
                    if (tx.getDate().isAfter(yearEnd)) {
                        if (tx.getType() == TransactionType.INCOME) {
                            netEffectAfter = netEffectAfter.add(tx.getAmount());
                        } else {
                            // EXPENSE and TRANSFER both reduced this account
                            netEffectAfter = netEffectAfter.subtract(tx.getAmount());
                        }
                    }
                }
                yearEndBalances.put(year, currentBalance.subtract(netEffectAfter));
            }

            // Compute variation percentages
            List<YearlyBalanceResponse.YearlyDataPoint> dataPoints = new ArrayList<>();
            BigDecimal prevBalance = null;
            for (int year : years) {
                BigDecimal bal = yearEndBalances.getOrDefault(year, BigDecimal.ZERO);
                BigDecimal variation = null;
                if (prevBalance != null && prevBalance.compareTo(BigDecimal.ZERO) != 0) {
                    variation =
                            bal.subtract(prevBalance)
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(prevBalance.abs(), 2, RoundingMode.HALF_UP);
                }
                dataPoints.add(
                        YearlyBalanceResponse.YearlyDataPoint.builder()
                                .year(year)
                                .amount(bal)
                                .variationPercentage(variation)
                                .build());
                prevBalance = bal;
            }

            // Name already decrypted by JPA converter
            String accountName = account.getName();

            accountEntries.add(
                    YearlyBalanceResponse.YearlyBalanceEntry.builder()
                            .id(account.getId())
                            .name(accountName)
                            .data(dataPoints)
                            .build());
        }

        // 4. Per-institution: group accounts by institution
        Map<Long, List<YearlyBalanceResponse.YearlyBalanceEntry>> byInstitution = new HashMap<>();
        Map<Long, String> institutionNames = new HashMap<>();
        Long noInstitutionKey = -1L;

        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);
            Long instId =
                    account.getInstitution() != null ? account.getInstitution().getId() : null;
            if (instId == null) {
                instId = noInstitutionKey;
                institutionNames.putIfAbsent(instId, "Other");
            } else {
                if (!institutionNames.containsKey(instId)) {
                    institutionNames.put(instId, account.getInstitution().getName());
                }
            }
            byInstitution
                    .computeIfAbsent(instId, k -> new ArrayList<>())
                    .add(accountEntries.get(i));
        }

        List<YearlyBalanceResponse.YearlyBalanceEntry> institutionEntries = new ArrayList<>();
        for (Map.Entry<Long, List<YearlyBalanceResponse.YearlyBalanceEntry>> entry :
                byInstitution.entrySet()) {
            Long instId = entry.getKey();
            List<YearlyBalanceResponse.YearlyBalanceEntry> instAccounts = entry.getValue();

            // Sum each year across all accounts in this institution
            List<YearlyBalanceResponse.YearlyDataPoint> instPoints = new ArrayList<>();
            BigDecimal prevInstBalance = null;
            for (int year : years) {
                BigDecimal total = BigDecimal.ZERO;
                for (YearlyBalanceResponse.YearlyBalanceEntry acctEntry : instAccounts) {
                    for (YearlyBalanceResponse.YearlyDataPoint dp : acctEntry.getData()) {
                        if (dp.getYear() == year) {
                            total = total.add(dp.getAmount());
                        }
                    }
                }
                BigDecimal variation = null;
                if (prevInstBalance != null && prevInstBalance.compareTo(BigDecimal.ZERO) != 0) {
                    variation =
                            total.subtract(prevInstBalance)
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(prevInstBalance.abs(), 2, RoundingMode.HALF_UP);
                }
                instPoints.add(
                        YearlyBalanceResponse.YearlyDataPoint.builder()
                                .year(year)
                                .amount(total)
                                .variationPercentage(variation)
                                .build());
                prevInstBalance = total;
            }

            institutionEntries.add(
                    YearlyBalanceResponse.YearlyBalanceEntry.builder()
                            .id(instId)
                            .name(institutionNames.getOrDefault(instId, "Unknown"))
                            .data(instPoints)
                            .build());
        }

        return YearlyBalanceResponse.builder()
                .years(years)
                .netWorth(netWorthPoints)
                .accounts(accountEntries)
                .institutions(institutionEntries)
                .currency(baseCurrency)
                .build();
    }

    private String getUserCurrency(Long userId) {
        return userRepository
                .findById(userId)
                .map(u -> u.getBaseCurrency() != null ? u.getBaseCurrency() : "USD")
                .orElse("USD");
    }

    private BigDecimal computePeriodFraction(String period) {
        if (period == null) return BigDecimal.valueOf(1.0 / 12);
        switch (period.toUpperCase()) {
            case "1D":
                return BigDecimal.valueOf(1.0 / 365);
            case "7D":
                return BigDecimal.valueOf(7.0 / 365);
            case "1M":
                return BigDecimal.valueOf(1.0 / 12);
            case "YTD":
                {
                    long daysSoFar =
                            java.time.temporal.ChronoUnit.DAYS.between(
                                    LocalDate.now().withDayOfYear(1), LocalDate.now());
                    return BigDecimal.valueOf(daysSoFar / 365.0);
                }
            case "1Y":
                return BigDecimal.ONE;
            case "ALL":
                return BigDecimal.valueOf(5.0); // Approximate 5-year
            default:
                {
                    try {
                        int days = Integer.parseInt(period);
                        return BigDecimal.valueOf(days / 365.0);
                    } catch (NumberFormatException e) {
                        return BigDecimal.valueOf(1.0 / 12);
                    }
                }
        }
    }
}
