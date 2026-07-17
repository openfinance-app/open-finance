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
import org.openfinance.entity.*;
import org.openfinance.repository.*;
import org.openfinance.security.EncryptionService;
import org.openfinance.service.NetWorthService;
import org.springframework.context.MessageSource;
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
    private final EncryptionService encryptionService;
    private final MessageSource messageSource;

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
        return buildContext(userId, Locale.ENGLISH);
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
        appendAssetSummary(context, userId, accounts);
        context.append("\n");

        // 6. Liability Summary
        String liabilitiesHeader =
                messageSource.getMessage("ai.context.liabilities", null, "LIABILITIES", locale);
        context.append("=== ").append(liabilitiesHeader).append(" ===\n");
        appendLiabilitySummary(context, userId, accounts);

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
        return buildMinimalContext(userId, Locale.ENGLISH);
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
        context.append("\n");

        String accountsHeader =
                messageSource.getMessage("ai.context.accounts", null, "ACCOUNTS", locale);
        context.append("=== ").append(accountsHeader).append(" ===\n");
        appendAccountSummary(context, accounts);

        return context.toString();
    }

    private void appendNetWorthSummary(StringBuilder sb, Long userId, List<Account> accounts) {
        // Determine base currency from user's first active account (default EUR)
        String baseCurrency = accounts.stream().findFirst().map(Account::getCurrency).orElse("EUR");

        try {
            BigDecimal totalAssets = netWorthService.calculateTotalAssets(userId, baseCurrency);
            BigDecimal totalLiabilities =
                    netWorthService.calculateTotalLiabilities(userId, baseCurrency);
            BigDecimal netWorth = totalAssets.subtract(totalLiabilities);

            sb.append(String.format("Net Worth: %,.2f %s\n", netWorth, baseCurrency));
            sb.append(String.format("Total Assets: %,.2f %s\n", totalAssets, baseCurrency));
            sb.append(
                    String.format("Total Liabilities: %,.2f %s\n", totalLiabilities, baseCurrency));
        } catch (Exception e) {
            log.warn(
                    "NetWorthService unavailable, falling back to raw account balances: {}",
                    e.getMessage());
            // Fallback: sum raw account balances directly
            try {
                BigDecimal rawBalance =
                        accounts.stream()
                                .map(Account::getBalance)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                sb.append(
                        String.format(
                                "Total Account Balances: %,.2f %s (note: approximate, multi-currency not converted)\n",
                                rawBalance, baseCurrency));
            } catch (Exception ex) {
                sb.append("Net worth data unavailable\n");
            }
        }
    }

    private void appendAccountSummary(StringBuilder sb, List<Account> accounts) {
        try {

            if (accounts.isEmpty()) {
                sb.append("No accounts found\n");
                return;
            }

            sb.append(String.format("Total: %d accounts\n", accounts.size()));

            for (Account account : accounts) {
                String name = account.getName();
                BigDecimal balance = account.getBalance();
                String typeLabel = account.getType().getDisplayName();
                String currency = account.getCurrency() != null ? account.getCurrency() : "EUR";

                sb.append(
                        String.format("• %s (%s): %,.2f %s\n", name, typeLabel, balance, currency));
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
                String currency = tx.getCurrency() != null ? tx.getCurrency() : "EUR";
                sb.append(
                        String.format(
                                "%s | %s: %s | %s%,.2f %s\n",
                                date, type, description, sign, amount.abs(), currency));
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

            sb.append(String.format("Active budgets: %d\n", activeBudgets.size()));

            for (Budget budget : activeBudgets) {
                String amountStr = budget.getAmount();
                BigDecimal amount = new BigDecimal(amountStr);

                sb.append(
                        String.format(
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

    private void appendAssetSummary(StringBuilder sb, Long userId, List<Account> accounts) {
        try {
            List<Asset> assets = assetRepository.findByUserId(userId);

            if (assets.isEmpty()) {
                sb.append("No investment assets\n");
                return;
            }

            BigDecimal totalValue =
                    assets.stream()
                            .map(Asset::getTotalValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Determine base currency from first account
            String baseCurrency =
                    accounts.stream().findFirst().map(Account::getCurrency).orElse("EUR");

            sb.append(
                    String.format(
                            "Total: %d assets worth %,.2f %s\n",
                            assets.size(), totalValue, baseCurrency));

            // Group by asset type
            var byType = assets.stream().collect(Collectors.groupingBy(Asset::getType));

            for (var entry : byType.entrySet()) {
                int count = entry.getValue().size();
                BigDecimal typeValue =
                        entry.getValue().stream()
                                .map(Asset::getTotalValue)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                sb.append(
                        String.format(
                                "• %s: %d assets (%,.2f %s)\n",
                                entry.getKey().getDisplayName(), count, typeValue, baseCurrency));
            }
        } catch (Exception e) {
            log.warn("Failed to build asset summary: {}", e.getMessage());
            sb.append("Asset data unavailable\n");
        }
    }

    private void appendLiabilitySummary(StringBuilder sb, Long userId, List<Account> accounts) {
        try {
            List<Liability> liabilities =
                    liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId);

            if (liabilities.isEmpty()) {
                sb.append("No liabilities\n");
                return;
            }

            // Determine base currency from first account
            String baseCurrency =
                    accounts.stream().findFirst().map(Account::getCurrency).orElse("EUR");

            BigDecimal totalBalance = BigDecimal.ZERO;

            for (Liability liability : liabilities) {
                String balanceStr = liability.getCurrentBalance();
                BigDecimal balance = new BigDecimal(balanceStr);
                totalBalance = totalBalance.add(balance);
            }

            sb.append(
                    String.format(
                            "Total: %d liabilities (%,.2f %s)\n",
                            liabilities.size(), totalBalance, baseCurrency));

            for (Liability liability : liabilities) {
                String name = liability.getName();
                String balanceStr = liability.getCurrentBalance();
                BigDecimal balance = new BigDecimal(balanceStr);
                String typeLabel = formatLiabilityType(liability.getType());

                sb.append(
                        String.format(
                                "• %s (%s): %,.2f %s\n", name, typeLabel, balance, baseCurrency));
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
