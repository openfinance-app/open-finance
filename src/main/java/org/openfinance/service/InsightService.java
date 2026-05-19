package org.openfinance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.InsightResponse;
import org.openfinance.entity.*;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.InsightRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.RecurringTransactionRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.repository.UserSettingsRepository;
import org.openfinance.security.EncryptionService;
import org.openfinance.service.ai.AIProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for generating and managing AI-powered financial insights.
 *
 * <p>This service analyzes user's financial data to generate actionable insights including:
 *
 * <ul>
 *   <li><strong>Spending Analysis:</strong> Detect unusual spending patterns and anomalies
 *   <li><strong>Budget Monitoring:</strong> Warn about budget limits and suggest adjustments
 *   <li><strong>Savings Optimization:</strong> Identify opportunities to reduce expenses
 *   <li><strong>Investment Recommendations:</strong> Portfolio rebalancing suggestions
 *   <li><strong>Debt Management:</strong> High-interest debt alerts
 * </ul>
 *
 * <p><strong>Implementation Strategy:</strong>
 *
 * <ol>
 *   <li>Rule-based analysis for deterministic insights (e.g., budget exceeded)
 *   <li>Statistical analysis for pattern detection (e.g., spending anomalies)
 *   <li>AI-powered insights using Ollama for complex recommendations
 * </ol>
 *
 * <p><strong>Caching:</strong> Insights are cached for 15 minutes to reduce computational load.
 * Cache is invalidated when financial data changes (transactions, budgets, etc.).
 *
 * @since Sprint 11 - AI Assistant Integration (Task 11.4)
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class InsightService {

    private final InsightRepository insightRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final RealEstateRepository realEstateRepository;
    private final AssetRepository assetRepository;
    private final LiabilityRepository liabilityRepository;
    private final EncryptionService encryptionService;
    private final MessageSource messageSource;
    private final AIProvider aiProvider;
    private final ObjectMapper objectMapper;
    private final ExchangeRateService exchangeRateService;

    // Thresholds for insight generation
    private static final BigDecimal SPENDING_ANOMALY_THRESHOLD =
            new BigDecimal("0.40"); // 40% increase
    private static final BigDecimal BUDGET_WARNING_THRESHOLD =
            new BigDecimal("0.75"); // 75% of budget spent
    private static final BigDecimal BUDGET_EXCEEDED_THRESHOLD =
            new BigDecimal("1.00"); // 100% of budget spent
    private static final int LOOKBACK_DAYS = 30; // Days to look back for comparisons
    private static final int INSIGHT_EXPIRY_DAYS = 7; // Delete insights older than 7 days

    /**
     * Generate fresh insights for a user by analyzing their financial data.
     *
     * <p>This method:
     *
     * <ol>
     *   <li>Deletes existing active insights for the user
     *   <li>Analyzes spending patterns, budgets, and account balances
     *   <li>Generates new insights based on analysis
     *   <li>Saves insights to database
     * </ol>
     *
     * <p><strong>Performance:</strong> This operation is computationally intensive and should not
     * be called on every page load. Use {@link #getInsights(Long)} instead, which uses caching.
     *
     * @param userId User ID
     * @param encryptionKey User's encryption key for decrypting data
     * @return List of newly generated insights
     * @throws ResourceNotFoundException if user not found
     */
    @CacheEvict(value = "insights", key = "#userId")
    public List<InsightResponse> generateInsights(Long userId, SecretKey encryptionKey) {
        log.info("Generating insights for user {}", userId);

        // Verify user exists
        userRepository
                .findById(userId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("User not found with ID: " + userId));

        // Delete existing active insights
        insightRepository.deleteByUser_Id(userId);
        log.debug("Deleted existing insights for user {}", userId);

        // Generate insights from multiple sources
        List<Insight> newInsights = new ArrayList<>();

        try {
            // 1. Analyze spending patterns
            newInsights.addAll(generateSpendingAnomalyInsights(userId, encryptionKey));

            // 2. Check budget status
            newInsights.addAll(generateBudgetInsights(userId, encryptionKey));

            // 3. Identify savings opportunities
            newInsights.addAll(generateSavingsOpportunities(userId, encryptionKey));

            // 4. Cash flow warnings
            newInsights.addAll(generateCashFlowWarnings(userId, encryptionKey));

            // 5. Region comparison insights (income/net worth vs country averages)
            newInsights.addAll(generateRegionComparisonInsights(userId, encryptionKey));

            // 6. Tax obligation estimates
            newInsights.addAll(generateTaxObligationInsights(userId, encryptionKey));

            // 7. Recurring billing analysis
            newInsights.addAll(generateRecurringBillingInsights(userId, encryptionKey));

            log.info("Generated {} insights for user {}", newInsights.size(), userId);
        } catch (Exception e) {
            log.error("Error generating insights for user {}: {}", userId, e.getMessage(), e);
        }

        // Save all insights
        List<Insight> savedInsights = insightRepository.saveAll(newInsights);

        // Convert to DTOs
        return savedInsights.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Get all insights for a user (cached).
     *
     * <p>Returns cached insights if available, otherwise fetches from database. Call {@link
     * #generateInsights(Long, SecretKey)} to refresh insights.
     *
     * @param userId User ID
     * @return List of insights ordered by priority and date
     */
    @Cacheable(value = "insights", key = "#userId")
    @Transactional(readOnly = true)
    public List<InsightResponse> getInsights(Long userId) {
        log.debug("Fetching insights for user {}", userId);
        List<Insight> insights = insightRepository.findByUser_IdAndDismissedFalse(userId);
        return insights.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Get top N insights for dashboard display.
     *
     * @param userId User ID
     * @param limit Maximum number of insights
     * @return List of top N insights
     */
    @Transactional(readOnly = true)
    public List<InsightResponse> getTopInsights(Long userId, int limit) {
        log.debug("Fetching top {} insights for user {}", limit, userId);
        List<Insight> insights =
                insightRepository.findTopNByUser_IdAndDismissedFalse(userId, limit);
        return insights.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Dismiss an insight.
     *
     * <p>Dismissed insights are hidden from the main view but retained for history.
     *
     * @param insightId Insight ID
     * @param userId User ID (for authorization)
     * @throws ResourceNotFoundException if insight not found or not owned by user
     */
    @CacheEvict(value = "insights", key = "#userId")
    public void dismissInsight(Long insightId, Long userId) {
        log.info("Dismissing insight {} for user {}", insightId, userId);

        Insight insight =
                insightRepository
                        .findByIdAndUser_Id(insightId, userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Insight not found with ID: " + insightId));

        insight.setDismissed(true);
        insightRepository.save(insight);
    }

    /**
     * Delete old dismissed insights (cleanup task).
     *
     * <p>Called by scheduled job to remove insights older than {@value #INSIGHT_EXPIRY_DAYS} days.
     *
     * @return Number of deleted insights
     */
    public long cleanupOldInsights() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(INSIGHT_EXPIRY_DAYS);
        long deleted = insightRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Cleaned up {} old insights (before {})", deleted, cutoff);
        return deleted;
    }

    // ============================================
    // Private Helper Methods for Insight Generation
    // ============================================

    /**
     * Generate insights about unusual spending patterns.
     *
     * <p>Compares spending in each category for the last 30 days vs previous 30 days. Generates
     * SPENDING_ANOMALY insight if increase exceeds {@value SPENDING_ANOMALY_THRESHOLD}.
     */
    private List<Insight> generateSpendingAnomalyInsights(Long userId, SecretKey encryptionKey) {
        List<Insight> insights = new ArrayList<>();

        try {
            LocalDate today = LocalDate.now();
            LocalDate thirtyDaysAgo = today.minusDays(LOOKBACK_DAYS);
            LocalDate sixtyDaysAgo = today.minusDays(LOOKBACK_DAYS * 2);

            // Get all categories for user
            List<Category> categories =
                    categoryRepository.findByUserIdAndType(userId, CategoryType.EXPENSE);

            for (Category category : categories) {
                try {
                    String categoryName =
                            encryptionService.decrypt(category.getName(), encryptionKey);

                    // Calculate spending for current period
                    BigDecimal currentSpending =
                            calculateCategorySpending(
                                    userId, category.getId(), thirtyDaysAgo, today);

                    // Calculate spending for previous period
                    BigDecimal previousSpending =
                            calculateCategorySpending(
                                    userId, category.getId(), sixtyDaysAgo, thirtyDaysAgo);

                    // Check for significant increase
                    if (previousSpending.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal percentChange =
                                currentSpending
                                        .subtract(previousSpending)
                                        .divide(previousSpending, 4, RoundingMode.HALF_UP);

                        if (percentChange.compareTo(SPENDING_ANOMALY_THRESHOLD) > 0) {
                            String title =
                                    messageSource.getMessage(
                                            "insight.spending.anomaly.title",
                                            new Object[] {categoryName},
                                            LocaleContextHolder.getLocale());
                            String description =
                                    messageSource.getMessage(
                                            "insight.spending.anomaly.description",
                                            new Object[] {
                                                categoryName.toLowerCase(),
                                                percentChange
                                                        .multiply(new BigDecimal("100"))
                                                        .doubleValue(),
                                                currentSpending.doubleValue(),
                                                previousSpending.doubleValue()
                                            },
                                            LocaleContextHolder.getLocale());

                            insights.add(
                                    createInsight(
                                            userId,
                                            InsightType.SPENDING_ANOMALY,
                                            title,
                                            description,
                                            InsightPriority.HIGH));
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error analyzing category {}: {}", category.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error generating spending anomaly insights: {}", e.getMessage(), e);
        }

        return insights;
    }

    /**
     * Generate insights about budget status.
     *
     * <p>Checks all active budgets and generates warnings if spending approaches or exceeds limits.
     */
    private List<Insight> generateBudgetInsights(Long userId, SecretKey encryptionKey) {
        List<Insight> insights = new ArrayList<>();

        try {
            LocalDate today = LocalDate.now();
            List<Budget> budgets = budgetRepository.findActiveByUserIdAndDate(userId, today);

            for (Budget budget : budgets) {
                try {
                    Category category = budget.getCategory();
                    String categoryName =
                            category != null
                                    ? encryptionService.decrypt(category.getName(), encryptionKey)
                                    : "Uncategorized";

                    BigDecimal budgetAmount =
                            new BigDecimal(
                                    encryptionService.decrypt(budget.getAmount(), encryptionKey));

                    // Calculate actual spending
                    LocalDate startDate = budget.getStartDate();
                    LocalDate endDate = budget.getEndDate();
                    BigDecimal spent =
                            category != null
                                    ? calculateCategorySpending(
                                            userId, category.getId(), startDate, endDate)
                                    : BigDecimal.ZERO;

                    BigDecimal percentUsed = spent.divide(budgetAmount, 4, RoundingMode.HALF_UP);

                    // Generate appropriate insight based on percentage used
                    if (percentUsed.compareTo(BUDGET_EXCEEDED_THRESHOLD) >= 0) {
                        BigDecimal overspent = spent.subtract(budgetAmount);
                        String title =
                                messageSource.getMessage(
                                        "insight.budget.exceeded.title",
                                        new Object[] {categoryName},
                                        LocaleContextHolder.getLocale());
                        String description =
                                messageSource.getMessage(
                                        "insight.budget.exceeded.description",
                                        new Object[] {
                                            categoryName.toLowerCase(),
                                            overspent.doubleValue(),
                                            percentUsed
                                                    .subtract(BigDecimal.ONE)
                                                    .multiply(new BigDecimal("100"))
                                                    .doubleValue()
                                        },
                                        LocaleContextHolder.getLocale());
                        insights.add(
                                createInsight(
                                        userId,
                                        InsightType.BUDGET_WARNING,
                                        title,
                                        description,
                                        InsightPriority.HIGH));
                    } else if (percentUsed.compareTo(BUDGET_WARNING_THRESHOLD) >= 0) {
                        BigDecimal remaining = budgetAmount.subtract(spent);
                        String title =
                                messageSource.getMessage(
                                        "insight.budget.warning.title",
                                        new Object[] {categoryName},
                                        LocaleContextHolder.getLocale());
                        String description =
                                messageSource.getMessage(
                                        "insight.budget.warning.description",
                                        new Object[] {
                                            percentUsed
                                                    .multiply(new BigDecimal("100"))
                                                    .doubleValue(),
                                            categoryName.toLowerCase(),
                                            spent.doubleValue(),
                                            budgetAmount.doubleValue(),
                                            remaining.doubleValue()
                                        },
                                        LocaleContextHolder.getLocale());
                        insights.add(
                                createInsight(
                                        userId,
                                        InsightType.BUDGET_WARNING,
                                        title,
                                        description,
                                        InsightPriority.MEDIUM));
                    }
                } catch (Exception e) {
                    log.warn("Error analyzing budget {}: {}", budget.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error generating budget insights: {}", e.getMessage(), e);
        }

        return insights;
    }

    /**
     * Generate insights about potential savings opportunities.
     *
     * <p>Identifies recurring expenses that could be optimized (e.g., unused subscriptions).
     */
    private List<Insight> generateSavingsOpportunities(Long userId, SecretKey encryptionKey) {
        List<Insight> insights = new ArrayList<>();

        try {
            LocalDate today = LocalDate.now();
            LocalDate thirtyDaysAgo = today.minusDays(LOOKBACK_DAYS);

            // Find categories with recurring small transactions (potential subscriptions)
            List<Category> categories =
                    categoryRepository.findByUserIdAndType(userId, CategoryType.EXPENSE);

            for (Category category : categories) {
                try {
                    String categoryName =
                            encryptionService.decrypt(category.getName(), encryptionKey);

                    // Check if category name suggests subscriptions/recurring costs
                    if (categoryName.toLowerCase().contains("subscription")
                            || categoryName.toLowerCase().contains("streaming")
                            || categoryName.toLowerCase().contains("membership")) {

                        BigDecimal monthlySpending =
                                calculateCategorySpending(
                                        userId, category.getId(), thirtyDaysAgo, today);

                        if (monthlySpending.compareTo(new BigDecimal("20")) > 0) {
                            String title =
                                    messageSource.getMessage(
                                            "insight.subscription.title",
                                            null,
                                            LocaleContextHolder.getLocale());
                            String description =
                                    messageSource.getMessage(
                                            "insight.subscription.description",
                                            new Object[] {
                                                monthlySpending.doubleValue(),
                                                categoryName.toLowerCase()
                                            },
                                            LocaleContextHolder.getLocale());
                            insights.add(
                                    createInsight(
                                            userId,
                                            InsightType.SAVINGS_OPPORTUNITY,
                                            title,
                                            description,
                                            InsightPriority.MEDIUM));
                        }
                    }
                } catch (Exception e) {
                    log.warn(
                            "Error analyzing savings for category {}: {}",
                            category.getId(),
                            e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error generating savings insights: {}", e.getMessage(), e);
        }

        return insights;
    }

    /**
     * Generate insights about cash flow and account balances.
     *
     * <p>Warns about low account balances that might cause overdrafts.
     */
    private List<Insight> generateCashFlowWarnings(Long userId, SecretKey encryptionKey) {
        List<Insight> insights = new ArrayList<>();

        try {
            List<Account> accounts = accountRepository.findByUserIdAndIsActive(userId, true);

            for (Account account : accounts) {
                BigDecimal balance = account.getBalance();

                // Check for low balance in checking/savings accounts
                if ((account.getType() == AccountType.CHECKING
                                || account.getType() == AccountType.SAVINGS)
                        && balance.compareTo(new BigDecimal("100")) < 0) {

                    String accountName =
                            encryptionService.decrypt(account.getName(), encryptionKey);
                    String title =
                            messageSource.getMessage(
                                    "insight.low.balance.title",
                                    null,
                                    LocaleContextHolder.getLocale());
                    String description =
                            messageSource.getMessage(
                                    "insight.low.balance.description",
                                    new Object[] {accountName, balance.doubleValue()},
                                    LocaleContextHolder.getLocale());
                    insights.add(
                            createInsight(
                                    userId,
                                    InsightType.CASH_FLOW_WARNING,
                                    title,
                                    description,
                                    InsightPriority.HIGH));
                }
            }
        } catch (Exception e) {
            log.error("Error generating cash flow insights: {}", e.getMessage(), e);
        }

        return insights;
    }

    /** Calculate total spending in a category for a date range. */
    private BigDecimal calculateCategorySpending(
            Long userId, Long categoryId, LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions =
                transactionRepository.findByCategoryIdAndDateRange(
                        categoryId, startDate, endDate, userId);

        return transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE && !t.getIsDeleted())
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ============================================
    // Region Comparison, Tax Obligation & Recurring Billing Insight Generators
    // ============================================

    /**
     * Safely parse the JSON response from the AI which may optionally be wrapped in markdown
     * blocks.
     */
    private JsonNode safeParseAiJsonResponse(String text) {
        try {
            if (text == null || text.trim().isEmpty()) return objectMapper.createObjectNode();
            String cleaned = text.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            return objectMapper.readTree(cleaned.trim());
        } catch (Exception e) {
            log.warn("Failed to parse AI JSON response: {}", text);
            return objectMapper.createObjectNode();
        }
    }

    private static final BigDecimal RECURRING_EXPENSE_HIGH_RATIO =
            new BigDecimal("0.50"); // 50% of income

    /**
     * Generate insights comparing user's income and net worth to regional averages.
     *
     * <p>Uses the user's country setting from UserSettings and compares estimated monthly income
     * (last 30 days of INCOME transactions) and total account balances against approximate national
     * medians.
     */
    private List<Insight> generateRegionComparisonInsights(Long userId, SecretKey encryptionKey) {
        List<Insight> insights = new ArrayList<>();

        try {
            String country = getUserCountry(userId);
            Locale locale = LocaleContextHolder.getLocale();
            String countryDisplayName = Locale.of("", country).getDisplayCountry(locale);

            // Determine default currency from first active account
            String currency =
                    accountRepository.findByUserIdAndIsActive(userId, true).stream()
                            .findFirst()
                            .map(Account::getCurrency)
                            .orElse("EUR");

            // Estimate monthly income from last 30 days of INCOME transactions (converted to base
            // currency)
            LocalDate today = LocalDate.now();
            LocalDate thirtyDaysAgo = today.minusDays(LOOKBACK_DAYS);
            BigDecimal monthlyIncome =
                    calculateMonthlyIncome(userId, currency, thirtyDaysAgo, today);

            // Fetch dynamic median income and net worth from AI
            String prompt =
                    String.format(
                            "You are a financial AI. Provide the approximate median monthly household income and median household net worth for %s in the currency %s. Respond ONLY with a valid JSON object matching exactly this schema: {\"medianIncome\": 2500, \"medianNetWorth\": 150000}. Do not include markdown code block syntax.",
                            countryDisplayName, currency);
            String aiResponse = aiProvider.sendPrompt(prompt, "").block();
            JsonNode dynamicData = safeParseAiJsonResponse(aiResponse);

            BigDecimal medianIncome =
                    dynamicData.has("medianIncome")
                            ? new BigDecimal(dynamicData.get("medianIncome").asText())
                            : null;
            if (medianIncome != null && monthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal diff = monthlyIncome.subtract(medianIncome);
                BigDecimal percentDiff =
                        diff.abs()
                                .divide(medianIncome, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"));

                if (diff.compareTo(BigDecimal.ZERO) >= 0) {
                    String title =
                            messageSource.getMessage("insight.region.income.title", null, locale);
                    String description =
                            messageSource.getMessage(
                                    "insight.region.income.above.description",
                                    new Object[] {
                                        monthlyIncome.doubleValue(),
                                        currency,
                                        percentDiff.intValue(),
                                        countryDisplayName
                                    },
                                    locale);
                    insights.add(
                            createInsight(
                                    userId,
                                    InsightType.REGION_COMPARISON,
                                    title,
                                    description,
                                    InsightPriority.LOW));
                } else {
                    String title =
                            messageSource.getMessage("insight.region.income.title", null, locale);
                    String description =
                            messageSource.getMessage(
                                    "insight.region.income.below.description",
                                    new Object[] {
                                        monthlyIncome.doubleValue(),
                                        currency,
                                        percentDiff.intValue(),
                                        countryDisplayName
                                    },
                                    locale);
                    insights.add(
                            createInsight(
                                    userId,
                                    InsightType.REGION_COMPARISON,
                                    title,
                                    description,
                                    InsightPriority.MEDIUM));
                }
            }

            // Compare net worth - include accounts, real estate equity, and assets, minus
            // liabilities
            BigDecimal medianNetWorth =
                    dynamicData.has("medianNetWorth")
                            ? new BigDecimal(dynamicData.get("medianNetWorth").asText())
                            : null;
            BigDecimal accountBalance =
                    accountRepository.findByUserIdAndIsActive(userId, true).stream()
                            .map(Account::getBalance)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Add Real Estate Equity (property value minus associated mortgage balance)
            BigDecimal realEstateEquity = BigDecimal.ZERO;
            List<Long> mortgageIds = new ArrayList<>();
            List<RealEstateProperty> properties =
                    realEstateRepository.findByUserIdAndIsActive(userId, true);
            for (RealEstateProperty property : properties) {
                try {
                    String decryptedValue =
                            encryptionService.decrypt(property.getCurrentValue(), encryptionKey);
                    BigDecimal propertyValue = new BigDecimal(decryptedValue);

                    BigDecimal mortgageBalance = BigDecimal.ZERO;
                    if (property.getMortgageId() != null) {
                        Optional<Liability> mortgage =
                                liabilityRepository.findById(property.getMortgageId());
                        if (mortgage.isPresent()) {
                            String decryptedMortgage =
                                    encryptionService.decrypt(
                                            mortgage.get().getCurrentBalance(), encryptionKey);
                            mortgageBalance = new BigDecimal(decryptedMortgage);
                            mortgageIds.add(property.getMortgageId());
                        }
                    }
                    realEstateEquity =
                            realEstateEquity.add(propertyValue.subtract(mortgageBalance));
                } catch (Exception e) {
                    log.error(
                            "Error decrypting real estate value/mortgage for property {}: {}",
                            property.getId(),
                            e.getMessage());
                }
            }

            // Add Other Assets (exclude REAL_ESTATE type — already accounted for via
            // realEstateRepository)
            BigDecimal assetValue =
                    assetRepository.findByUserId(userId).stream()
                            .filter(a -> a.getType() != AssetType.REAL_ESTATE)
                            .map(Asset::getTotalValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Subtract remaining liabilities (excluding mortgages already deducted from real estate
            // equity)
            BigDecimal otherLiabilities = BigDecimal.ZERO;
            for (Liability liability :
                    liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
                if (!mortgageIds.contains(liability.getId())) {
                    try {
                        String decryptedBalance =
                                encryptionService.decrypt(
                                        liability.getCurrentBalance(), encryptionKey);
                        otherLiabilities = otherLiabilities.add(new BigDecimal(decryptedBalance));
                    } catch (Exception e) {
                        log.warn(
                                "Error decrypting liability balance for liability {}: {}",
                                liability.getId(),
                                e.getMessage());
                    }
                }
            }

            BigDecimal totalNetWorth =
                    accountBalance.add(realEstateEquity).add(assetValue).subtract(otherLiabilities);

            if (medianNetWorth != null && totalNetWorth.compareTo(BigDecimal.ZERO) != 0) {
                String comparison;
                if (totalNetWorth.compareTo(medianNetWorth) >= 0) {
                    comparison =
                            messageSource.getMessage("insight.region.networth.above", null, locale);
                } else {
                    comparison =
                            messageSource.getMessage("insight.region.networth.below", null, locale);
                }
                String title =
                        messageSource.getMessage("insight.region.networth.title", null, locale);
                String description =
                        messageSource.getMessage(
                                "insight.region.networth.description",
                                new Object[] {
                                    totalNetWorth.doubleValue(),
                                    currency,
                                    countryDisplayName,
                                    medianNetWorth.doubleValue(),
                                    comparison
                                },
                                locale);
                insights.add(
                        createInsight(
                                userId,
                                InsightType.REGION_COMPARISON,
                                title,
                                description,
                                InsightPriority.LOW));
            }

        } catch (Exception e) {
            log.error("Error generating region comparison insights: {}", e.getMessage(), e);
        }

        return insights;
    }

    /**
     * Generate insights about estimated tax obligations based on income and country.
     *
     * <p>Uses approximate effective tax rates for the user's country to estimate annual tax
     * liability. Also checks for potential deduction categories.
     */
    private List<Insight> generateTaxObligationInsights(Long userId, SecretKey encryptionKey) {
        List<Insight> insights = new ArrayList<>();

        try {
            String country = getUserCountry(userId);
            Locale locale = LocaleContextHolder.getLocale();
            String countryDisplayName = Locale.of("", country).getDisplayCountry(locale);

            // Determine default currency from first active account
            String currency =
                    accountRepository.findByUserIdAndIsActive(userId, true).stream()
                            .findFirst()
                            .map(Account::getCurrency)
                            .orElse("EUR");

            String prompt =
                    String.format(
                            "You are a tax AI. Provide the approximate current effective tax parameters for %s. Return ONLY a strict JSON object with keys 'baseRate' (percentage), 'topRate' (percentage), and 'standardDeduction' in local currency (%s). Use just numbers without symbols. Do not include markdown blocks.",
                            countryDisplayName, currency);
            String aiResponse = aiProvider.sendPrompt(prompt, "").block();
            JsonNode taxData = safeParseAiJsonResponse(aiResponse);

            if (!taxData.has("baseRate") || !taxData.has("standardDeduction")) {
                log.debug("No tax data available from AI for country: {}", country);
                return insights;
            }

            // Estimate annual income from last 30 days extrapolated (converted to base currency)
            LocalDate today = LocalDate.now();
            LocalDate thirtyDaysAgo = today.minusDays(LOOKBACK_DAYS);
            BigDecimal monthlyIncome =
                    calculateMonthlyIncome(userId, currency, thirtyDaysAgo, today);

            if (monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
                return insights;
            }

            BigDecimal annualIncome = monthlyIncome.multiply(new BigDecimal("12"));

            // Calculate estimated tax using the base rate
            BigDecimal baseRate =
                    new BigDecimal(taxData.get("baseRate").asText()); // e.g. 22 for 22%
            BigDecimal standardDeduction =
                    new BigDecimal(taxData.get("standardDeduction").asText());
            BigDecimal taxableIncome =
                    annualIncome.subtract(standardDeduction).max(BigDecimal.ZERO);
            BigDecimal estimatedTax =
                    taxableIncome
                            .multiply(baseRate)
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal effectiveRate =
                    annualIncome.compareTo(BigDecimal.ZERO) > 0
                            ? estimatedTax
                                    .divide(annualIncome, 4, RoundingMode.HALF_UP)
                                    .multiply(new BigDecimal("100"))
                            : BigDecimal.ZERO;

            // Generate tax estimate insight
            String title = messageSource.getMessage("insight.tax.estimate.title", null, locale);
            String description =
                    messageSource.getMessage(
                            "insight.tax.estimate.description",
                            new Object[] {
                                annualIncome.doubleValue(),
                                currency,
                                countryDisplayName,
                                estimatedTax.doubleValue(),
                                effectiveRate.doubleValue()
                            },
                            locale);
            insights.add(
                    createInsight(
                            userId,
                            InsightType.TAX_OBLIGATION,
                            title,
                            description,
                            InsightPriority.MEDIUM));

            // Check for potential deduction categories (donations, professional expenses)
            List<Category> expenseCategories =
                    categoryRepository.findByUserIdAndType(userId, CategoryType.EXPENSE);
            BigDecimal potentialDeductions = BigDecimal.ZERO;

            for (Category category : expenseCategories) {
                try {
                    String catName =
                            encryptionService
                                    .decrypt(category.getName(), encryptionKey)
                                    .toLowerCase();
                    if (catName.contains("donat")
                            || catName.contains("charit")
                            || catName.contains("don")
                            || catName.contains("profession")
                            || catName.contains("education")
                            || catName.contains("retirement")
                            || catName.contains("retraite")) {
                        BigDecimal catSpending =
                                calculateCategorySpending(
                                        userId, category.getId(), thirtyDaysAgo, today);
                        potentialDeductions =
                                potentialDeductions.add(catSpending.multiply(new BigDecimal("12")));
                    }
                } catch (Exception e) {
                    log.warn(
                            "Error analyzing deduction category {}: {}",
                            category.getId(),
                            e.getMessage());
                }
            }

            if (potentialDeductions.compareTo(new BigDecimal("100")) > 0) {
                String deductionTitle =
                        messageSource.getMessage("insight.tax.deduction.title", null, locale);
                String deductionDescription =
                        messageSource.getMessage(
                                "insight.tax.deduction.description",
                                new Object[] {
                                    potentialDeductions.doubleValue(), currency, countryDisplayName
                                },
                                locale);
                insights.add(
                        createInsight(
                                userId,
                                InsightType.TAX_OBLIGATION,
                                deductionTitle,
                                deductionDescription,
                                InsightPriority.LOW));
            }

        } catch (Exception e) {
            log.error("Error generating tax obligation insights: {}", e.getMessage(), e);
        }

        return insights;
    }

    /**
     * Generate insights analyzing recurring subscriptions and bills.
     *
     * <p>Examines active recurring transactions to summarize total recurring costs, identify high
     * ratios relative to income, and flag potential savings.
     */
    private List<Insight> generateRecurringBillingInsights(Long userId, SecretKey encryptionKey) {
        List<Insight> insights = new ArrayList<>();

        try {
            Locale locale = LocaleContextHolder.getLocale();

            // Get active recurring expenses
            List<RecurringTransaction> activeRecurring =
                    recurringTransactionRepository.findByUserIdAndIsActive(userId);

            List<RecurringTransaction> recurringExpenses =
                    activeRecurring.stream()
                            .filter(rt -> rt.getType() == TransactionType.EXPENSE)
                            .collect(Collectors.toList());

            if (recurringExpenses.isEmpty()) {
                return insights;
            }

            // Calculate monthly equivalent for each recurring expense
            BigDecimal totalMonthly = BigDecimal.ZERO;
            for (RecurringTransaction rt : recurringExpenses) {
                BigDecimal monthlyEquiv = toMonthlyAmount(rt.getAmount(), rt.getFrequency());
                totalMonthly = totalMonthly.add(monthlyEquiv);
            }

            BigDecimal totalAnnual = totalMonthly.multiply(new BigDecimal("12"));

            String currency = recurringExpenses.get(0).getCurrency();

            // Estimate monthly income (converted to same currency as recurring expenses)
            LocalDate today = LocalDate.now();
            LocalDate thirtyDaysAgo = today.minusDays(LOOKBACK_DAYS);
            BigDecimal monthlyIncome =
                    calculateMonthlyIncome(userId, currency, thirtyDaysAgo, today);

            BigDecimal incomeRatioPercent = BigDecimal.ZERO;
            if (monthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
                incomeRatioPercent =
                        totalMonthly
                                .divide(monthlyIncome, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"));
            }

            // Summary insight
            String summaryTitle =
                    messageSource.getMessage("insight.recurring.summary.title", null, locale);
            String summaryDesc =
                    messageSource.getMessage(
                            "insight.recurring.summary.description",
                            new Object[] {
                                recurringExpenses.size(),
                                totalMonthly.doubleValue(),
                                currency,
                                totalAnnual.doubleValue(),
                                incomeRatioPercent.intValue()
                            },
                            locale);
            insights.add(
                    createInsight(
                            userId,
                            InsightType.RECURRING_BILLING,
                            summaryTitle,
                            summaryDesc,
                            InsightPriority.LOW));

            // High ratio warning
            if (monthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = totalMonthly.divide(monthlyIncome, 4, RoundingMode.HALF_UP);
                if (ratio.compareTo(RECURRING_EXPENSE_HIGH_RATIO) >= 0) {
                    String highTitle =
                            messageSource.getMessage(
                                    "insight.recurring.high.ratio.title", null, locale);
                    String highDesc =
                            messageSource.getMessage(
                                    "insight.recurring.high.ratio.description",
                                    new Object[] {
                                        totalMonthly.doubleValue(),
                                        currency,
                                        incomeRatioPercent.intValue()
                                    },
                                    locale);
                    insights.add(
                            createInsight(
                                    userId,
                                    InsightType.RECURRING_BILLING,
                                    highTitle,
                                    highDesc,
                                    InsightPriority.HIGH));
                }
            }

            // AI Competitor Search for better deals
            List<String> expenseNames = new ArrayList<>();
            for (RecurringTransaction rt : recurringExpenses) {
                try {
                    expenseNames.add(encryptionService.decrypt(rt.getDescription(), encryptionKey));
                } catch (Exception e) {
                }
            }
            if (!expenseNames.isEmpty()) {
                String prompt =
                        "Review these subscriptions/services: "
                                + String.join(", ", expenseNames)
                                + ". For each, provide a cheaper or better competitor alternative. Return ONLY a strict JSON array of objects with keys 'originalService', 'competitorName', 'competitorPrice' (numeric string), 'potentialSavings' (numeric string without currency). Use "
                                + currency
                                + " for prices. Do not output markdown code blocks.";
                try {
                    String aiResponse = aiProvider.sendPrompt(prompt, "").block();
                    JsonNode competitorDataArray = safeParseAiJsonResponse(aiResponse);
                    if (competitorDataArray.isArray() && competitorDataArray.size() > 0) {
                        for (JsonNode competitorData : competitorDataArray) {
                            String originalService =
                                    competitorData.has("originalService")
                                            ? competitorData.get("originalService").asText()
                                            : "Service";
                            String competitorName =
                                    competitorData.has("competitorName")
                                            ? competitorData.get("competitorName").asText()
                                            : "Competitor";
                            String potentialSavingsStr =
                                    competitorData.has("potentialSavings")
                                            ? competitorData.get("potentialSavings").asText()
                                            : "0";

                            // Try parsing potential savings safely
                            try {
                                BigDecimal potentialSavings = new BigDecimal(potentialSavingsStr);
                                if (potentialSavings.compareTo(BigDecimal.ZERO) > 0) {
                                    String compTitle =
                                            messageSource.getMessage(
                                                    "insight.recurring.competitor.title",
                                                    new Object[] {originalService},
                                                    "Better deal found for " + originalService,
                                                    locale);
                                    String compDesc =
                                            messageSource.getMessage(
                                                    "insight.recurring.competitor.description",
                                                    new Object[] {
                                                        originalService,
                                                        competitorName,
                                                        potentialSavings.doubleValue(),
                                                        currency
                                                    },
                                                    "Consider switching from "
                                                            + originalService
                                                            + " to "
                                                            + competitorName
                                                            + " to save "
                                                            + potentialSavings.doubleValue()
                                                            + " "
                                                            + currency,
                                                    locale);
                                    insights.add(
                                            createInsight(
                                                    userId,
                                                    InsightType.RECURRING_BILLING,
                                                    compTitle,
                                                    compDesc,
                                                    InsightPriority.MEDIUM));
                                }
                            } catch (NumberFormatException nfe) {
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to fetch competitor pricing from AI: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Error generating recurring billing insights: {}", e.getMessage(), e);
        }

        return insights;
    }

    /** Convert an amount at a given recurring frequency to its monthly equivalent. */
    private BigDecimal toMonthlyAmount(BigDecimal amount, RecurringFrequency frequency) {
        if (amount == null || frequency == null) {
            return BigDecimal.ZERO;
        }
        switch (frequency) {
            case DAILY:
                return amount.multiply(new BigDecimal("30"));
            case WEEKLY:
                return amount.multiply(new BigDecimal("4.33"));
            case BIWEEKLY:
                return amount.multiply(new BigDecimal("2.17"));
            case MONTHLY:
                return amount;
            case QUARTERLY:
                return amount.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
            case YEARLY:
                return amount.divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
            default:
                return amount;
        }
    }

    /**
     * Calculate the total income for a user over a date range, converting all transactions to the
     * specified base currency before summing.
     */
    private BigDecimal calculateMonthlyIncome(
            Long userId, String baseCurrency, LocalDate from, LocalDate to) {
        List<Transaction> allIncomeTransactions =
                transactionRepository.findByUserIdAndType(userId, TransactionType.INCOME);

        // Sum income in the requested window
        BigDecimal windowTotal = sumIncomeInRange(allIncomeTransactions, baseCurrency, from, to);

        if (windowTotal.compareTo(BigDecimal.ZERO) > 0) {
            return windowTotal;
        }

        // Fallback: no income in the requested window — use the last 12 months and return a monthly
        // average
        LocalDate today = LocalDate.now();
        LocalDate twelveMonthsAgo = today.minusMonths(12);
        BigDecimal yearTotal =
                sumIncomeInRange(allIncomeTransactions, baseCurrency, twelveMonthsAgo, today);
        if (yearTotal.compareTo(BigDecimal.ZERO) > 0) {
            log.debug(
                    "No income found in last 30 days for user {}; falling back to 12-month average",
                    userId);
            return yearTotal.divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    /** Sum all non-deleted income transactions in [from, to] converted to baseCurrency. */
    private BigDecimal sumIncomeInRange(
            List<Transaction> incomeTransactions,
            String baseCurrency,
            LocalDate from,
            LocalDate to) {
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction t : incomeTransactions) {
            if (t.getIsDeleted()
                    || t.getDate() == null
                    || t.getDate().isBefore(from)
                    || t.getDate().isAfter(to)) {
                continue;
            }
            BigDecimal amount = t.getAmount();
            String txCurrency = t.getCurrency();
            if (txCurrency != null && !txCurrency.equalsIgnoreCase(baseCurrency)) {
                try {
                    amount = exchangeRateService.convert(amount, txCurrency, baseCurrency);
                } catch (Exception e) {
                    log.warn(
                            "Currency conversion failed for transaction {}: {} -> {}: {}",
                            t.getId(),
                            txCurrency,
                            baseCurrency,
                            e.getMessage());
                }
            }
            total = total.add(amount);
        }
        return total;
    }

    /** Get the user's country code from UserSettings, defaulting to "FR". */
    private String getUserCountry(Long userId) {
        Optional<UserSettings> settings = userSettingsRepository.findByUserId(userId);
        return settings.map(UserSettings::getCountry).orElse("FR");
    }

    /** Create an insight entity. */
    private Insight createInsight(
            Long userId,
            InsightType type,
            String title,
            String description,
            InsightPriority priority) {
        User user = userRepository.findById(userId).orElseThrow();
        return Insight.builder()
                .user(user)
                .type(type)
                .title(title)
                .description(description)
                .priority(priority)
                .dismissed(false)
                .build();
    }

    /** Convert entity to DTO. */
    private InsightResponse toDto(Insight insight) {
        return InsightResponse.builder()
                .id(insight.getId())
                .type(insight.getType())
                .title(insight.getTitle())
                .description(insight.getDescription())
                .priority(insight.getPriority())
                .dismissed(insight.getDismissed())
                .createdAt(insight.getCreatedAt())
                .build();
    }
}
