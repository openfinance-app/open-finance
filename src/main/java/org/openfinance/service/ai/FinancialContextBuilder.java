package org.openfinance.service.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.Account;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.Budget;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.security.EncryptionService;
import org.openfinance.service.ExchangeRateService;
import org.openfinance.service.NetWorthService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

/**
 * Service for building financial context summaries for AI prompts.
 *
 * <p>Gathers and formats user's financial data into a structured context string that provides the
 * AI assistant with relevant information to answer questions.
 *
 * <p><strong>Context includes:</strong>
 *
 * <ul>
 *   <li>Net worth summary (assets, liabilities, net worth)
 *   <li>Account balances by type
 *   <li>Recent transactions (last 10)
 *   <li>Budget status (if available)
 *   <li>Asset summary (if available)
 * </ul>
 *
 * @since Sprint 11 - AI Assistant Integration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialContextBuilder {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AssetRepository assetRepository;
    private final LiabilityRepository liabilityRepository;
    private final BudgetRepository budgetRepository;
    private final NetWorthService netWorthService;
    private final ExchangeRateService exchangeRateService;
    private final EncryptionService encryptionService;
    private final MessageSource messageSource;
    private final org.openfinance.service.DefaultCurrencyProvider defaultCurrencyProvider;

    /**
     * Builds a comprehensive financial context summary for the AI assistant.
     *
     * <p><strong>Example Output:</strong>
     *
     * <pre>{@code
     * === FINANCIAL SUMMARY ===
     * Net Worth: $45,250.00
     * Total Assets: $50,000.00
     * Total Liabilities: $4,750.00
     *
     * === ACCOUNTS (3) ===
     * • Checking Account (CHECKING): $2,500.00
     * • Savings Account (SAVINGS): $15,000.00
     * • Credit Card (CREDIT_CARD): -$750.00
     *
     * === RECENT TRANSACTIONS (Last 10) ===
     * Jan 25, 2024 | Expense: Grocery Store | -$125.50
     * ...
     * }</pre>
     *
     * @param userId User ID to build context for
     * @param encryptionKey Encryption key to decrypt sensitive data
     * @return Formatted financial context string
     */
    public String buildContext(Long userId) {
        return buildContext(userId, LocaleContextHolder.getLocale());
    }

    /**
     * Builds a comprehensive financial context summary for the AI assistant with localized section
     * headers.
     *
     * @param userId User ID to build context for
     * @param encryptionKey Encryption key to decrypt sensitive data
     * @param locale Locale for section headers
     * @return Formatted financial context string
     */
    public String buildContext(Long userId, Locale locale) {
        log.debug("Building financial context for user ID: {} with locale: {}", userId, locale);

        List<Account> accounts = accountRepository.findByUserIdAndIsActive(userId, true);
        StringBuilder context = new StringBuilder();

        // 1. Net Worth Summary
        String financialSummaryHeader =
                messageSource.getMessage(
                        "ai.context.financial.summary", null, "FINANCIAL SUMMARY", locale);
        context.append("=== ").append(financialSummaryHeader).append(" ===\n");
        appendNetWorthSummary(context, userId, accounts);
        appendCashFlowSummary(context, userId);
        context.append("\n");

        // 2. Account Balances
        String accountsHeader =
                messageSource.getMessage("ai.context.accounts", null, "ACCOUNTS", locale);
        context.append("=== ").append(accountsHeader).append(" ===\n");
        appendAccountSummary(context, accounts);
        context.append("\n");

        // 3. Recent Transactions
        String transactionsHeader =
                messageSource.getMessage(
                        "ai.context.recent.transactions",
                        null,
                        "RECENT TRANSACTIONS (Last 10)",
                        locale);
        context.append("=== ").append(transactionsHeader).append(" ===\n");
        appendRecentTransactions(context, userId, locale);
        context.append("\n");

        // 4. Budget Status
        String budgetHeader =
                messageSource.getMessage("ai.context.budget.status", null, "BUDGET STATUS", locale);
        context.append("=== ").append(budgetHeader).append(" ===\n");
        appendBudgetStatus(context, userId);
        context.append("\n");

        // 5. Asset Summary
        String assetsHeader = messageSource.getMessage("ai.context.assets", null, "ASSETS", locale);
        context.append("=== ").append(assetsHeader).append(" ===\n");
        appendAssetSummary(context, userId);
        context.append("\n");

        // 6. Liability Summary
        String liabilitiesHeader =
                messageSource.getMessage("ai.context.liabilities", null, "LIABILITIES", locale);
        context.append("=== ").append(liabilitiesHeader).append(" ===\n");
        appendLiabilitySummary(context, userId);

        String result = context.toString();
        log.debug("Built financial context: {} characters", result.length());
        return result;
    }

    /**
     * Builds a minimal context with only net worth and account summary.
     *
     * <p>Used when quick responses are needed or to reduce token usage.
     *
     * @param userId User ID
     * @param encryptionKey Encryption key
     * @return Minimal financial context string
     */
    public String buildMinimalContext(Long userId) {
        return buildMinimalContext(userId, LocaleContextHolder.getLocale());
    }

    /**
     * Builds a minimal context with only net worth and account summary with localized headers.
     *
     * @param userId User ID
     * @param encryptionKey Encryption key
     * @param locale Locale for section headers
     * @return Minimal financial context string
     */
    public String buildMinimalContext(Long userId, Locale locale) {
        List<Account> accounts = accountRepository.findByUserIdAndIsActive(userId, true);
        StringBuilder context = new StringBuilder();

        String financialSummaryHeader =
                messageSource.getMessage(
                        "ai.context.financial.summary", null, "FINANCIAL SUMMARY", locale);
        context.append("=== ").append(financialSummaryHeader).append(" ===\n");
        appendNetWorthSummary(context, userId, accounts);
        appendCashFlowSummary(context, userId);
        context.append("\n");

        String accountsHeader =
                messageSource.getMessage("ai.context.accounts", null, "ACCOUNTS", locale);
        context.append("=== ").append(accountsHeader).append(" ===\n");
        appendAccountSummary(context, accounts);

        return context.toString();
    }

    private void appendNetWorthSummary(StringBuilder sb, Long userId, List<Account> accounts) {
        String baseCurrency = defaultCurrencyProvider.resolveForUser(userId);
        sb.append("[VERIFIED_FINANCIAL_DATA]\n");
        try {
            BigDecimal accountBalance =
                    accounts.stream()
                            .map(
                                    account ->
                                            toBase(
                                                    account.getBalance(),
                                                    account.getCurrency(),
                                                    baseCurrency))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            sb.append(
                    String.format(
                            Locale.ROOT,
                            "Total Account Balances: %,.2f %s\n",
                            accountBalance,
                            baseCurrency));
        } catch (Exception e) {
            sb.append("Total Account Balances: unavailable (currency conversion failed)\n");
        }

        try {
            BigDecimal totalAssets = netWorthService.calculateTotalAssets(userId, baseCurrency);
            BigDecimal totalLiabilities =
                    netWorthService.calculateTotalLiabilities(userId, baseCurrency);
            BigDecimal netWorth = totalAssets.subtract(totalLiabilities);

            sb.append(String.format(Locale.ROOT, "Net Worth: %,.2f %s\n", netWorth, baseCurrency));
            sb.append(
                    String.format(
                            Locale.ROOT, "Total Assets: %,.2f %s\n", totalAssets, baseCurrency));
            sb.append(
                    String.format(
                            Locale.ROOT,
                            "Total Liabilities: %,.2f %s\n",
                            totalLiabilities,
                            baseCurrency));
        } catch (Exception e) {
            log.warn("Net worth unavailable in AI context: {}", e.getMessage());
            sb.append("Net worth data unavailable; do not infer it from partial data\n");
        }
    }

    private BigDecimal toBase(BigDecimal amount, String currency, String baseCurrency) {
        String source = defaultCurrencyProvider.resolve(currency);
        return source.equals(baseCurrency)
                ? amount
                : exchangeRateService.convert(amount, source, baseCurrency);
    }

    private void appendCashFlowSummary(StringBuilder sb, Long userId) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.withDayOfMonth(1);
        String baseCurrency = defaultCurrencyProvider.resolveForUser(userId);
        sb.append("Cash flow period (current calendar month to date): ")
                .append(start)
                .append(" through ")
                .append(end)
                .append("\n");
        try {
            List<Transaction> transactions =
                    transactionRepository.findByUserIdAndDateBetween(userId, start, end);
            BigDecimal income =
                    transactionTotal(transactions, TransactionType.INCOME, baseCurrency);
            BigDecimal expenses =
                    transactionTotal(transactions, TransactionType.EXPENSE, baseCurrency);
            sb.append(
                    String.format(
                            Locale.ROOT, "Month-to-date income: %,.2f %s\n", income, baseCurrency));
            sb.append(
                    String.format(
                            Locale.ROOT,
                            "Month-to-date expenses: %,.2f %s\n",
                            expenses,
                            baseCurrency));
            BigDecimal surplus = income.subtract(expenses);
            sb.append(
                    String.format(
                            Locale.ROOT,
                            "Month-to-date cash flow: %,.2f %s (%s)\n",
                            surplus,
                            baseCurrency,
                            surplus.signum() < 0 ? "deficit" : "surplus"));
            sb.append(
                    "Internal transfers are excluded. These are observed totals, not a forecast or a monthly average.\n");
        } catch (Exception e) {
            sb.append(
                    "Month-to-date cash flow unavailable; do not infer it from recent transactions\n");
        }
    }

    private BigDecimal transactionTotal(
            List<Transaction> transactions, TransactionType type, String baseCurrency) {
        return transactions.stream()
                .filter(transaction -> !Boolean.TRUE.equals(transaction.getIsDeleted()))
                .filter(
                        transaction ->
                                transaction.getTransferId() == null
                                        && transaction.getType() == type)
                .map(
                        transaction ->
                                toBase(
                                        transaction.getAmount(),
                                        transaction.getCurrency(),
                                        baseCurrency))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void appendAccountSummary(StringBuilder sb, List<Account> accounts) {
        try {

            if (accounts.isEmpty()) {
                sb.append("No accounts found\n");
                return;
            }

            sb.append(String.format(Locale.ROOT, "Total: %d accounts\n", accounts.size()));

            for (Account account : accounts) {
                String name = account.getName();
                BigDecimal balance = account.getBalance();
                String typeLabel = account.getType().getDisplayName();
                String currency = defaultCurrencyProvider.resolve(account.getCurrency());

                sb.append(
                        String.format(
                                Locale.ROOT,
                                "• %s (%s): %,.2f %s\n",
                                name,
                                typeLabel,
                                balance,
                                currency));
            }
        } catch (Exception e) {
            log.warn("Failed to build account summary: {}", e.getMessage());
            sb.append("Account data unavailable\n");
        }
    }

    private void appendRecentTransactions(StringBuilder sb, Long userId, Locale locale) {
        try {
            // Fetch all user transactions and sort/limit manually
            List<Transaction> allTransactions =
                    transactionRepository.findByUserIdAndDateBetween(
                            userId, LocalDate.now().minusMonths(3), LocalDate.now());

            List<Transaction> transactions =
                    allTransactions.stream()
                            .filter(t -> !t.getIsDeleted())
                            .sorted((t1, t2) -> t2.getDate().compareTo(t1.getDate()))
                            .limit(10)
                            .collect(Collectors.toList());

            if (transactions.isEmpty()) {
                sb.append("No recent transactions\n");
                return;
            }

            DateTimeFormatter dateFormatter =
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);

            for (Transaction tx : transactions) {
                String date = tx.getDate().format(dateFormatter);
                String description = tx.getDescription();
                BigDecimal amount = tx.getAmount();
                TransactionType type = tx.getType();

                String sign = type == TransactionType.EXPENSE ? "-" : "+";
                String currency = defaultCurrencyProvider.resolve(tx.getCurrency());
                sb.append(
                        String.format(
                                Locale.ROOT,
                                "%s | %s: %s | %s%,.2f %s\n",
                                date,
                                type,
                                description,
                                sign,
                                amount.abs(),
                                currency));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch recent transactions: {}", e.getMessage());
            sb.append("Transaction data unavailable\n");
        }
    }

    private void appendBudgetStatus(StringBuilder sb, Long userId) {
        try {
            // Fetch all budgets and filter for active ones manually
            List<Budget> allBudgets = budgetRepository.findByUserId(userId);
            LocalDate now = LocalDate.now();

            List<Budget> activeBudgets =
                    allBudgets.stream()
                            .filter(
                                    b ->
                                            !b.getStartDate().isAfter(now)
                                                    && !b.getEndDate().isBefore(now))
                            .collect(Collectors.toList());

            if (activeBudgets.isEmpty()) {
                sb.append("No active budgets\n");
                return;
            }

            sb.append(String.format(Locale.ROOT, "Active budgets: %d\n", activeBudgets.size()));

            for (Budget budget : activeBudgets) {
                String amountStr = budget.getAmount();
                BigDecimal amount = new BigDecimal(amountStr);

                sb.append(
                        String.format(
                                Locale.ROOT,
                                "• %s: %,.2f/%s\n",
                                budget.getCategory().getName(),
                                amount,
                                budget.getPeriod().toString().toLowerCase()));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch budget status: {}", e.getMessage());
            sb.append("Budget data unavailable\n");
        }
    }

    private void appendAssetSummary(StringBuilder sb, Long userId) {
        try {
            List<Asset> assets = assetRepository.findByUserId(userId);

            if (assets.isEmpty()) {
                sb.append("No investment assets\n");
                return;
            }

            sb.append(
                    String.format(
                            Locale.ROOT,
                            "Total: %d assets (values in each asset's currency)\n",
                            assets.size()));
            for (Asset asset : assets) {
                if (asset.getAcquisitionType() == AcquisitionType.PLANNED) continue;
                sb.append(
                        String.format(
                                Locale.ROOT,
                                "• %s: %,.2f %s\n",
                                asset.getName(),
                                asset.getTotalValue(),
                                defaultCurrencyProvider.resolve(asset.getCurrency())));
            }

        } catch (Exception e) {
            log.warn("Failed to build asset summary: {}", e.getMessage());
            sb.append("Asset data unavailable\n");
        }
    }

    private void appendLiabilitySummary(StringBuilder sb, Long userId) {
        try {
            List<Liability> liabilities =
                    liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId);

            if (liabilities.isEmpty()) {
                sb.append("No liabilities\n");
                return;
            }

            sb.append(
                    String.format(
                            Locale.ROOT,
                            "Total: %d liabilities (balances in each liability's currency)\n",
                            liabilities.size()));
            for (Liability liability : liabilities) {
                BigDecimal balance = new BigDecimal(liability.getCurrentBalance());
                sb.append(
                        String.format(
                                Locale.ROOT,
                                "• %s (%s): %,.2f %s\n",
                                liability.getName(),
                                formatLiabilityType(liability.getType()),
                                balance,
                                defaultCurrencyProvider.resolve(liability.getCurrency())));
            }

        } catch (Exception e) {
            log.warn("Failed to build liability summary: {}", e.getMessage());
            sb.append("Liability data unavailable\n");
        }
    }

    private String formatLiabilityType(LiabilityType type) {
        if (type == null) return "Loan";
        return switch (type) {
            case MORTGAGE -> "Mortgage";
            case LOAN -> "Loan";
            case CREDIT_CARD -> "Credit Card";
            case PERSONAL_LOAN -> "Personal Loan";
            case STUDENT_LOAN -> "Student Loan";
            case AUTO_LOAN -> "Auto Loan";
            case OTHER -> "Other";
        };
    }
}
