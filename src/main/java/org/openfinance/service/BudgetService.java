package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.BudgetBulkCreateResponse;
import org.openfinance.dto.BudgetHistoryEntry;
import org.openfinance.dto.BudgetHistoryResponse;
import org.openfinance.dto.BudgetProgressResponse;
import org.openfinance.dto.BudgetRequest;
import org.openfinance.dto.BudgetResponse;
import org.openfinance.dto.BudgetSuggestion;
import org.openfinance.dto.BudgetSummaryResponse;
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetPeriod;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionSplit;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.BudgetNotFoundException;
import org.openfinance.exception.CategoryNotFoundException;
import org.openfinance.mapper.BudgetMapper;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.TransactionSplitRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for managing budgets and budget tracking.
 *
 * <p>This service handles business logic for budget CRUD operations, spending calculations, and
 * progress tracking, including:
 *
 * <ul>
 *   <li>Creating new budgets with encrypted amounts
 *   <li>Updating existing budgets
 *   <li>Deleting budgets
 *   <li>Retrieving budgets with decrypted data
 *   <li>Calculating budget progress (spent, remaining, percentage)
 *   <li>Generating budget summaries
 * </ul>
 *
 * <p><strong>Security Note:</strong> Budget amounts are encrypted before storing in the database
 * and decrypted when reading. The encryption key must be provided by the caller.
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.9.1.1: Budget creation and management
 *   <li>REQ-2.9.1.2: Budget tracking and progress calculation
 *   <li>REQ-2.9.1.3: Budget reports and summaries
 *   <li>REQ-2.18: Data encryption at rest for sensitive fields
 *   <li>REQ-3.2: Authorization - Users can only access their own budgets
 * </ul>
 *
 * @see org.openfinance.entity.Budget
 * @see org.openfinance.dto.BudgetRequest
 * @see org.openfinance.dto.BudgetResponse
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-02
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final CurrencyRepository currencyRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionSplitRepository transactionSplitRepository;
    private final BudgetMapper budgetMapper;
    private final EncryptionService encryptionService;
    private final MessageSource messageSource;
    private final OperationHistoryService operationHistoryService;
    private final SearchTokenService searchTokenService;
    private final DefaultCurrencyProvider defaultCurrencyProvider;

    // Status thresholds
    private static final BigDecimal WARNING_THRESHOLD = BigDecimal.valueOf(75);
    private static final BigDecimal EXCEEDED_THRESHOLD = BigDecimal.valueOf(100);

    /**
     * Creates a new budget for the specified user.
     *
     * <p>The budget amount is encrypted before storing in the database. Validates that:
     *
     * <ul>
     *   <li>Category exists and belongs to user
     *   <li>No duplicate budget exists (same category + period)
     *   <li>Date range is valid (endDate >= startDate)
     * </ul>
     *
     * <p>Requirement REQ-2.9.1.1: Create new budget with encrypted sensitive data
     *
     * @param request the budget creation request containing budget details
     * @param userId the ID of the user creating the budget
     * @param encryptionKey the AES-256 encryption key for amount encryption
     * @return the created budget with decrypted data
     * @throws IllegalArgumentException if validation fails or parameters are null
     * @throws CategoryNotFoundException if category doesn't exist or doesn't belong to user
     */
    public BudgetResponse createBudget(BudgetRequest request, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Budget request cannot be null");
        }
        log.debug(
                "Creating budget for user {}: category={}, period={}, amount={}",
                userId,
                request.getCategoryId(),
                request.getPeriod(),
                request.getAmount());

        // Validate category ownership
        Category category = validateCategoryOwnership(request.getCategoryId(), userId);

        // Validate no duplicate budget
        validateNoDuplicateBudget(request.getCategoryId(), request.getPeriod(), userId, null);

        // Map request to entity
        Budget budget = budgetMapper.toEntity(request);
        budget.setUserId(userId);
        budget.setCurrencyId(resolveCurrencyId(budget.getCurrency()));

        // Amount handled by JPA converter

        // Save to database
        Budget savedBudget = budgetRepository.save(budget);
        indexBudgetSearchTokens(savedBudget, request.getNotes());
        log.info(
                "Budget created successfully: id={}, userId={}, category={}, period={}",
                savedBudget.getId(),
                userId,
                category.getName(),
                savedBudget.getPeriod());

        // Decrypt and return response
        BudgetResponse budgetCreateResponse = toResponseWithDecryption(savedBudget, category);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.BUDGET,
                savedBudget.getId(),
                category.getName(),
                org.openfinance.entity.OperationType.CREATE,
                (Object) null,
                null);

        return budgetCreateResponse;
    }

    /**
     * Updates an existing budget.
     *
     * <p>Only the budget owner can update the budget. Validates ownership, category existence, and
     * duplicate constraints.
     *
     * <p>Requirement REQ-2.9.1.1: Update existing budget
     *
     * <p>Requirement REQ-3.2: Authorization check - verify budget ownership
     *
     * @param budgetId the ID of the budget to update
     * @param request the budget update request
     * @param userId the ID of the user updating the budget (for authorization)
     * @param encryptionKey the AES-256 encryption key for amount encryption
     * @return the updated budget with decrypted data
     * @throws BudgetNotFoundException if budget not found or doesn't belong to user
     * @throws CategoryNotFoundException if category doesn't exist or doesn't belong to user
     * @throws IllegalArgumentException if validation fails or parameters are null
     */
    public BudgetResponse updateBudget(Long budgetId, BudgetRequest request, Long userId) {
        if (budgetId == null) {
            throw new IllegalArgumentException("Budget ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Budget request cannot be null");
        }
        log.debug("Updating budget {}: userId={}", budgetId, userId);

        // Fetch budget and verify ownership (Requirement 3.2: Authorization)
        Budget budget =
                budgetRepository
                        .findByIdAndUserId(budgetId, userId)
                        .orElseThrow(() -> BudgetNotFoundException.byIdAndUser(budgetId, userId));

        // Validate category ownership
        Category category = validateCategoryOwnership(request.getCategoryId(), userId);

        // Validate no duplicate budget (exclude current budget)
        validateNoDuplicateBudget(request.getCategoryId(), request.getPeriod(), userId, budgetId);

        // Capture snapshot before update for history
        BudgetResponse beforeSnapshot = toResponseWithDecryption(budget, category);

        // Update fields from request (only non-null fields will be copied)
        budgetMapper.updateEntityFromRequest(request, budget);
        budget.setCurrencyId(resolveCurrencyId(budget.getCurrency()));

        // Amount handled by JPA converter

        // Save changes
        Budget updatedBudget = budgetRepository.save(budget);
        indexBudgetSearchTokens(updatedBudget, request.getNotes());
        log.info("Budget updated successfully: id={}, userId={}", budgetId, userId);

        // Decrypt and return response
        BudgetResponse budgetUpdateResponse = toResponseWithDecryption(updatedBudget, category);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.BUDGET,
                budgetId,
                category.getName(), // decrypted from validateCategoryOwnership earlier
                org.openfinance.entity.OperationType.UPDATE,
                beforeSnapshot,
                null);

        return budgetUpdateResponse;
    }

    /**
     * Deletes a budget.
     *
     * <p>Hard delete - budget is permanently removed from database. Only the budget owner can
     * delete the budget.
     *
     * <p>Requirement REQ-2.9.1.1: Delete budget
     *
     * <p>Requirement REQ-3.2: Authorization check - verify budget ownership
     *
     * @param budgetId the ID of the budget to delete
     * @param userId the ID of the user deleting the budget (for authorization)
     * @throws BudgetNotFoundException if budget not found or doesn't belong to user
     * @throws IllegalArgumentException if budgetId or userId is null
     */
    public void deleteBudget(Long budgetId, Long userId) {
        if (budgetId == null) {
            throw new IllegalArgumentException("Budget ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        log.debug("Deleting budget {}: userId={}", budgetId, userId);

        // Fetch budget and verify ownership (Requirement 3.2: Authorization)
        Budget budget =
                budgetRepository
                        .findByIdAndUserId(budgetId, userId)
                        .orElseThrow(() -> BudgetNotFoundException.byIdAndUser(budgetId, userId));

        String label = null;
        BudgetResponse snapshot = null;
        if (EncryptionContext.getKey() != null) {
            // We need category for toResponseWithDecryption
            Category category = budget.getCategory();
            // Note: category name might be encrypted if not system category
            // but category.getName() in service/entity is typically the encrypted string?
            // Wait, Category name is encrypted if private.
            // BudgetResponse's categoryName will be decrypted by toResponseWithDecryption
            snapshot = toResponseWithDecryption(budget, category);
            label = snapshot.getCategoryName();
        }

        // Hard delete
        budgetRepository.delete(budget);
        searchTokenService.removeEntity("BUDGET", budgetId);

        log.info("Budget deleted successfully: id={}, userId={}", budgetId, userId);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.BUDGET,
                budgetId,
                label,
                org.openfinance.entity.OperationType.DELETE,
                snapshot,
                null);
    }

    /**
     * Retrieves a single budget by ID.
     *
     * <p>Only the budget owner can retrieve the budget. Amount is decrypted.
     *
     * <p>Requirement REQ-2.9.1.1: Retrieve budget details
     *
     * <p>Requirement REQ-3.2: Authorization check - verify budget ownership
     *
     * @param budgetId the ID of the budget to retrieve
     * @param userId the ID of the user retrieving the budget (for authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting amount
     * @return the budget with decrypted data
     * @throws BudgetNotFoundException if budget not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public BudgetResponse getBudgetById(Long budgetId, Long userId) {
        if (budgetId == null) {
            throw new IllegalArgumentException("Budget ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Retrieving budget {}: userId={}", budgetId, userId);

        // Fetch budget and verify ownership (Requirement 3.2: Authorization)
        Budget budget =
                budgetRepository
                        .findByIdAndUserId(budgetId, userId)
                        .orElseThrow(() -> BudgetNotFoundException.byIdAndUser(budgetId, userId));

        // Fetch category for denormalization
        Category category =
                categoryRepository
                        .findByIdAndUserId(budget.getCategoryId(), userId)
                        .orElseThrow(() -> new CategoryNotFoundException(budget.getCategoryId()));

        // Decrypt and return response
        return toResponseWithDecryption(budget, category);
    }

    /**
     * Retrieves all budgets for a user.
     *
     * <p>Returns all budgets regardless of period or status. Amounts are decrypted.
     *
     * <p>Requirement REQ-2.9.1.1: List all user budgets
     *
     * @param userId the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting amounts
     * @return list of budgets with decrypted data (may be empty)
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public List<BudgetResponse> getBudgetsByUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Retrieving all budgets for user {}", userId);

        List<Budget> budgets = budgetRepository.findByUserId(userId);

        log.debug("Found {} budgets for user {}", budgets.size(), userId);

        // Decrypt and map to responses
        return budgets.stream()
                .map(
                        budget -> {
                            Category category =
                                    categoryRepository
                                            .findByIdAndUserId(budget.getCategoryId(), userId)
                                            .orElseThrow(
                                                    () ->
                                                            new CategoryNotFoundException(
                                                                    budget.getCategoryId()));
                            return toResponseWithDecryption(budget, category);
                        })
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all budgets for a user filtered by period.
     *
     * <p>Returns budgets matching the specified period type (WEEKLY, MONTHLY, etc.). Amounts are
     * decrypted.
     *
     * <p>Requirement REQ-2.9.1.1: Filter budgets by period
     *
     * @param userId the ID of the user
     * @param period the budget period to filter by
     * @param encryptionKey the AES-256 encryption key for decrypting amounts
     * @return list of budgets with decrypted data (may be empty)
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public List<BudgetResponse> getBudgetsByPeriod(Long userId, BudgetPeriod period) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (period == null) {
            throw new IllegalArgumentException("Period cannot be null");
        }
        log.debug("Retrieving budgets for user {} with period {}", userId, period);

        List<Budget> budgets = budgetRepository.findByUserIdAndPeriod(userId, period);

        log.debug("Found {} budgets for user {} with period {}", budgets.size(), userId, period);

        // Decrypt and map to responses
        return budgets.stream()
                .map(
                        budget -> {
                            Category category =
                                    categoryRepository
                                            .findByIdAndUserId(budget.getCategoryId(), userId)
                                            .orElseThrow(
                                                    () ->
                                                            new CategoryNotFoundException(
                                                                    budget.getCategoryId()));
                            return toResponseWithDecryption(budget, category);
                        })
                .collect(Collectors.toList());
    }

    /**
     * Calculates budget progress for a specific budget.
     *
     * <p>Calculates:
     *
     * <ul>
     *   <li>Amount spent (from transactions in category during budget period)
     *   <li>Remaining amount (budgeted - spent)
     *   <li>Percentage spent ((spent / budgeted) × 100)
     *   <li>Days remaining in period
     *   <li>Status (ON_TRACK, WARNING, EXCEEDED)
     * </ul>
     *
     * <p>Requirement REQ-2.9.1.2: Budget tracking with spent/remaining calculations
     *
     * @param budgetId the ID of the budget to track
     * @param userId the ID of the user (for authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting amounts
     * @return budget progress details
     * @throws BudgetNotFoundException if budget not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public BudgetProgressResponse calculateBudgetProgress(Long budgetId, Long userId) {
        if (budgetId == null) {
            throw new IllegalArgumentException("Budget ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Calculating budget progress for budget {}: userId={}", budgetId, userId);

        // Fetch budget and verify ownership
        Budget budget =
                budgetRepository
                        .findByIdAndUserId(budgetId, userId)
                        .orElseThrow(() -> BudgetNotFoundException.byIdAndUser(budgetId, userId));

        // Fetch category
        Category category =
                categoryRepository
                        .findByIdAndUserId(budget.getCategoryId(), userId)
                        .orElseThrow(() -> new CategoryNotFoundException(budget.getCategoryId()));

        // Amount already decrypted by JPA converter
        String decryptedAmount = budget.getAmount();
        BigDecimal budgeted = new BigDecimal(decryptedAmount);

        // Calculate spent amount
        BigDecimal spent =
                calculateSpentAmount(category, budget.getStartDate(), budget.getEndDate(), userId);

        // Calculate remaining
        BigDecimal remaining = budgeted.subtract(spent);

        // Calculate percentage spent
        BigDecimal percentageSpent = BigDecimal.ZERO;
        if (budgeted.compareTo(BigDecimal.ZERO) > 0) {
            percentageSpent =
                    spent.divide(budgeted, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
        }

        // Calculate days remaining
        LocalDate today = LocalDate.now();
        int daysRemaining = (int) ChronoUnit.DAYS.between(today, budget.getEndDate());

        // Determine status
        String status = determineStatus(percentageSpent);

        log.debug(
                "Budget progress calculated: budgeted={}, spent={}, remaining={}, percentage={}%, status={}",
                budgeted, spent, remaining, percentageSpent, status);

        return BudgetProgressResponse.builder()
                .budgetId(budget.getId())
                .categoryName(getDecryptedCategoryName(category))
                .budgeted(budgeted)
                .spent(spent)
                .remaining(remaining)
                .percentageSpent(percentageSpent)
                .currency(budget.getCurrency())
                .period(budget.getPeriod())
                .startDate(budget.getStartDate())
                .endDate(budget.getEndDate())
                .daysRemaining(daysRemaining)
                .status(status)
                .build();
    }

    /**
     * Generates a budget summary for all budgets of a specific period.
     *
     * <p>Aggregates statistics across all budgets:
     *
     * <ul>
     *   <li>Total budgeted amount
     *   <li>Total spent amount
     *   <li>Total remaining amount
     *   <li>Average percentage spent
     *   <li>Individual budget progress details
     * </ul>
     *
     * <p>Requirement REQ-2.9.1.3: Budget reports with aggregate statistics
     *
     * @param userId the ID of the user
     * @param period the budget period to summarize
     * @param encryptionKey the AES-256 encryption key for decrypting amounts
     * @return budget summary with aggregate statistics
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public BudgetSummaryResponse getBudgetSummary(Long userId, BudgetPeriod period) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (period == null) {
            throw new IllegalArgumentException("Period cannot be null");
        }
        log.debug("Generating budget summary for user {} with period {}", userId, period);

        // Fetch budgets for period
        List<Budget> budgets = budgetRepository.findByUserIdAndPeriod(userId, period);

        if (budgets.isEmpty()) {
            log.debug("No budgets found for user {} with period {}", userId, period);
            return BudgetSummaryResponse.builder()
                    .period(period)
                    .totalBudgets(0)
                    .activeBudgets(0)
                    .totalBudgeted(BigDecimal.ZERO)
                    .totalSpent(BigDecimal.ZERO)
                    .totalRemaining(BigDecimal.ZERO)
                    .averageSpentPercentage(BigDecimal.ZERO)
                    .budgets(List.of())
                    .currency(defaultCurrencyProvider.resolveForUser(userId)) // Default
                    .build();
        }

        // Calculate progress for each budget
        List<BudgetProgressResponse> progressList =
                budgets.stream()
                        .map(
                                budget -> {
                                    try {
                                        return calculateBudgetProgress(budget.getId(), userId);
                                    } catch (Exception e) {
                                        log.error(
                                                "Error calculating progress for budget {}: {}",
                                                budget.getId(),
                                                e.getMessage());
                                        return null;
                                    }
                                })
                        .filter(progress -> progress != null)
                        .collect(Collectors.toList());

        // Aggregate statistics
        BigDecimal totalBudgeted =
                progressList.stream()
                        .map(BudgetProgressResponse::getBudgeted)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSpent =
                progressList.stream()
                        .map(BudgetProgressResponse::getSpent)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRemaining = totalBudgeted.subtract(totalSpent);

        BigDecimal averageSpentPercentage = BigDecimal.ZERO;
        if (!progressList.isEmpty()) {
            BigDecimal sumPercentages =
                    progressList.stream()
                            .map(BudgetProgressResponse::getPercentageSpent)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            averageSpentPercentage =
                    sumPercentages.divide(
                            BigDecimal.valueOf(progressList.size()), 2, RoundingMode.HALF_UP);
        }

        // Count active budgets (current date within budget period)
        LocalDate today = LocalDate.now();
        int activeBudgets =
                (int)
                        budgets.stream()
                                .filter(
                                        b ->
                                                !today.isBefore(b.getStartDate())
                                                        && !today.isAfter(b.getEndDate()))
                                .count();

        // Use currency from first budget (assumes single currency)
        String currency = budgets.get(0).getCurrency();

        log.debug(
                "Budget summary generated: totalBudgets={}, activeBudgets={}, totalBudgeted={}, totalSpent={}",
                budgets.size(),
                activeBudgets,
                totalBudgeted,
                totalSpent);

        return BudgetSummaryResponse.builder()
                .period(period)
                .totalBudgets(budgets.size())
                .activeBudgets(activeBudgets)
                .totalBudgeted(totalBudgeted)
                .totalSpent(totalSpent)
                .totalRemaining(totalRemaining)
                .averageSpentPercentage(averageSpentPercentage)
                .budgets(progressList)
                .currency(currency)
                .build();
    }

    /**
     * Generates a combined budget summary across ALL period types for a user.
     *
     * <p>Returns progress for every budget regardless of period type (WEEKLY, MONTHLY, QUARTERLY,
     * YEARLY). Used by the Budget UI when no period filter is selected so that yearly budgets (and
     * others) are not silently omitted.
     *
     * <p>Requirement REQ-2.9.1.3: Budget reports — show all periods
     *
     * @param userId the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting amounts
     * @return budget summary covering all period types
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public BudgetSummaryResponse getAllBudgetsSummary(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Generating all-period budget summary for user {}", userId);

        List<Budget> budgets = budgetRepository.findByUserId(userId);

        if (budgets.isEmpty()) {
            return BudgetSummaryResponse.builder()
                    .period(null)
                    .totalBudgets(0)
                    .activeBudgets(0)
                    .totalBudgeted(BigDecimal.ZERO)
                    .totalSpent(BigDecimal.ZERO)
                    .totalRemaining(BigDecimal.ZERO)
                    .averageSpentPercentage(BigDecimal.ZERO)
                    .budgets(List.of())
                    .currency(defaultCurrencyProvider.resolveForUser(userId))
                    .build();
        }

        List<BudgetProgressResponse> progressList =
                budgets.stream()
                        .map(
                                budget -> {
                                    try {
                                        return calculateBudgetProgress(budget.getId(), userId);
                                    } catch (Exception e) {
                                        log.error(
                                                "Error calculating progress for budget {}: {}",
                                                budget.getId(),
                                                e.getMessage());
                                        return null;
                                    }
                                })
                        .filter(progress -> progress != null)
                        .collect(Collectors.toList());

        BigDecimal totalBudgeted =
                progressList.stream()
                        .map(BudgetProgressResponse::getBudgeted)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpent =
                progressList.stream()
                        .map(BudgetProgressResponse::getSpent)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRemaining = totalBudgeted.subtract(totalSpent);
        BigDecimal averageSpentPercentage = BigDecimal.ZERO;
        if (!progressList.isEmpty()) {
            BigDecimal sumPct =
                    progressList.stream()
                            .map(BudgetProgressResponse::getPercentageSpent)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            averageSpentPercentage =
                    sumPct.divide(BigDecimal.valueOf(progressList.size()), 2, RoundingMode.HALF_UP);
        }
        LocalDate today = LocalDate.now();
        int activeBudgets =
                (int)
                        budgets.stream()
                                .filter(
                                        b ->
                                                !today.isBefore(b.getStartDate())
                                                        && !today.isAfter(b.getEndDate()))
                                .count();
        String currency = budgets.get(0).getCurrency();

        log.debug(
                "All-period budget summary generated: totalBudgets={}, totalSpent={}",
                budgets.size(),
                totalSpent);

        return BudgetSummaryResponse.builder()
                .period(null)
                .totalBudgets(budgets.size())
                .activeBudgets(activeBudgets)
                .totalBudgeted(totalBudgeted)
                .totalSpent(totalSpent)
                .totalRemaining(totalRemaining)
                .averageSpentPercentage(averageSpentPercentage)
                .budgets(progressList)
                .currency(currency)
                .build();
    }

    /**
     * Returns the spending history for a budget broken down into sub-periods.
     *
     * <p>The budget's date range (startDate → endDate) is split into sub-periods matching the
     * budget's {@link BudgetPeriod}:
     *
     * <ul>
     *   <li>WEEKLY → 7-day windows
     *   <li>MONTHLY → calendar months
     *   <li>QUARTERLY → 3-month windows
     *   <li>YEARLY → 1-year windows (edge case: a multi-year budget)
     * </ul>
     *
     * <p>For each sub-period, the actual transaction spend is calculated and compared against the
     * budget amount, producing a status indicator.
     *
     * <p>Requirement REQ-2.9.1.4: Budget history per sub-period
     *
     * @param budgetId the budget ID
     * @param userId the authenticated user's ID
     * @param encryptionKey the AES-256 encryption key for decrypting the amount
     * @return full history response with per-period breakdown
     * @throws BudgetNotFoundException if the budget doesn't belong to the user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public BudgetHistoryResponse getBudgetHistory(Long budgetId, Long userId) {
        if (budgetId == null) {
            throw new IllegalArgumentException("Budget ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Generating budget history for budget {}: userId={}", budgetId, userId);

        // Fetch and verify ownership
        Budget budget =
                budgetRepository
                        .findByIdAndUserId(budgetId, userId)
                        .orElseThrow(() -> BudgetNotFoundException.byIdAndUser(budgetId, userId));

        Category category =
                categoryRepository
                        .findByIdAndUserId(budget.getCategoryId(), userId)
                        .orElseThrow(() -> new CategoryNotFoundException(budget.getCategoryId()));

        // Amount already decrypted by JPA converter
        String decryptedAmount = budget.getAmount();
        BigDecimal budgetedPerPeriod = new BigDecimal(decryptedAmount);

        // Build sub-period windows; extend to today for budgets whose fixed end date
        // is in the past so that ongoing monthly/weekly budgets always cover the
        // current period rather than stopping at the original creation-time end date.
        LocalDate effectiveEndDate =
                budget.getEndDate().isBefore(LocalDate.now())
                        ? LocalDate.now()
                        : budget.getEndDate();
        List<LocalDate[]> windows =
                buildSubPeriodWindows(budget.getPeriod(), budget.getStartDate(), effectiveEndDate);

        BigDecimal totalSpent = BigDecimal.ZERO;
        BigDecimal totalBudgeted = BigDecimal.ZERO;
        List<BudgetHistoryEntry> entries = new ArrayList<>();

        for (LocalDate[] window : windows) {
            LocalDate periodStart = window[0];
            LocalDate periodEnd = window[1];

            BigDecimal spent = calculateSpentAmount(category, periodStart, periodEnd, userId);
            BigDecimal remaining = budgetedPerPeriod.subtract(spent);
            BigDecimal percentage = BigDecimal.ZERO;
            if (budgetedPerPeriod.compareTo(BigDecimal.ZERO) > 0) {
                percentage =
                        spent.divide(budgetedPerPeriod, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);
            }

            totalSpent = totalSpent.add(spent);
            totalBudgeted = totalBudgeted.add(budgetedPerPeriod);

            entries.add(
                    BudgetHistoryEntry.builder()
                            .label(formatPeriodLabel(budget.getPeriod(), periodStart, periodEnd))
                            .periodStart(periodStart)
                            .periodEnd(periodEnd)
                            .budgeted(budgetedPerPeriod)
                            .spent(spent)
                            .remaining(remaining)
                            .percentageSpent(percentage)
                            .status(determineStatus(percentage))
                            .build());
        }

        log.debug(
                "Budget history generated: budgetId={}, entries={}, totalSpent={}",
                budgetId,
                entries.size(),
                totalSpent);

        return BudgetHistoryResponse.builder()
                .budgetId(budget.getId())
                .categoryName(getDecryptedCategoryName(category))
                .amount(budgetedPerPeriod)
                .currency(budget.getCurrency())
                .period(budget.getPeriod())
                .startDate(budget.getStartDate())
                .endDate(budget.getEndDate())
                .history(entries)
                .totalSpent(totalSpent)
                .totalBudgeted(totalBudgeted)
                .build();
    }

    /**
     * Analyses past EXPENSE transactions to generate budget suggestions per category.
     *
     * <p>The method:
     *
     * <ol>
     *   <li>Determines the analysis window: [{@code today - lookbackMonths}, {@code today}].
     *   <li>Enumerates the EXPENSE categories to analyse — either those in {@code categoryIds}
     *       (when provided) or all categories returned by {@link
     *       CategoryRepository#findByUserIdAndType}.
     *   <li>For each category, splits the window into sub-periods matching {@code period} and sums
     *       EXPENSE transactions per sub-period (reuses {@link #calculateSpentAmount}).
     *   <li>Computes the arithmetic average across non-empty sub-periods and rounds up (ceiling) to
     *       the nearest whole unit.
     *   <li>Sets {@code hasExistingBudget = true} when a budget already exists for the category +
     *       period combination.
     *   <li>Skips categories with zero transactions in the window.
     * </ol>
     *
     * <p>Requirement REQ-2.9.1.5: Automatic budget creation from transaction history analysis
     *
     * @param userId the authenticated user's ID
     * @param period the target budget period (WEEKLY / MONTHLY / QUARTERLY / YEARLY)
     * @param lookbackMonths number of months to scan (1–24)
     * @param categoryIds restrict analysis to these category IDs; pass {@code null} to analyse ALL
     *     EXPENSE categories
     * @param encryptionKey the AES-256 encryption key (required for the {@code hasExistingBudget}
     *     check — not used directly here but enforced for consistency)
     * @return ordered list of suggestions (one per qualifying EXPENSE category)
     * @throws IllegalArgumentException if userId, period, or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public List<BudgetSuggestion> analyzeCategorySpending(
            Long userId, BudgetPeriod period, int lookbackMonths, List<Long> categoryIds) {

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (period == null) {
            throw new IllegalArgumentException("Period cannot be null");
        }
        log.debug(
                "Analyzing category spending: userId={}, period={}, lookbackMonths={}",
                userId,
                period,
                lookbackMonths);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(lookbackMonths);

        // Determine which categories to analyse
        List<Category> categories;
        if (categoryIds != null && !categoryIds.isEmpty()) {
            categories =
                    categoryIds.stream()
                            .map(
                                    id ->
                                            categoryRepository
                                                    .findByIdAndUserId(id, userId)
                                                    .orElse(null))
                            .filter(c -> c != null && c.getType() == CategoryType.EXPENSE)
                            .collect(Collectors.toList());
        } else {
            categories = categoryRepository.findByUserIdAndType(userId, CategoryType.EXPENSE);
        }

        List<BudgetSuggestion> suggestions = new ArrayList<>();

        // Default currency for suggestions is the user's base currency; the caller may still
        // override it via the request.
        String suggestionCurrency = defaultCurrencyProvider.resolveForUser(userId);

        for (Category category : categories) {
            // Split window into sub-periods matching the target budget period
            List<LocalDate[]> windows = buildSubPeriodWindows(period, startDate, endDate);

            BigDecimal totalSpent = BigDecimal.ZERO;
            int filledWindows = 0;
            int totalTxCount = 0;

            for (LocalDate[] window : windows) {
                List<Long> catIds = getCategoryAndSubcategoryIds(category);
                List<Transaction> txList =
                        transactionRepository.findByCategoryIdInAndDateRange(
                                catIds, window[0], window[1], userId);

                BigDecimal windowSpent =
                        txList.stream()
                                .filter(t -> t.getType() == TransactionType.EXPENSE)
                                .map(Transaction::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                int windowTxCount =
                        (int)
                                txList.stream()
                                        .filter(t -> t.getType() == TransactionType.EXPENSE)
                                        .count();

                // Also include split transaction amounts for these categories
                List<TransactionSplit> splits =
                        transactionSplitRepository.findByCategoryIdInAndDateRange(
                                catIds, window[0], window[1], userId);
                BigDecimal splitSpent =
                        splits.stream()
                                .filter(s -> s.getAmount() != null)
                                .map(TransactionSplit::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                windowSpent = windowSpent.add(splitSpent);

                // Also include count of split transactions
                Long splitTxCount =
                        transactionSplitRepository
                                .countUniqueTransactionsByCategoryIdInAndDateRange(
                                        catIds, window[0], window[1], userId);
                if (splitTxCount != null) {
                    windowTxCount += splitTxCount.intValue();
                }

                if (windowTxCount > 0) {
                    totalSpent = totalSpent.add(windowSpent);
                    filledWindows++;
                    totalTxCount += windowTxCount;
                }
            }

            // Skip categories with no transactions in the lookback window
            if (totalTxCount == 0) {
                continue;
            }

            // Average per non-empty sub-period, then ceiling-round to nearest whole unit
            BigDecimal averageSpent =
                    totalSpent.divide(BigDecimal.valueOf(filledWindows), 2, RoundingMode.HALF_UP);
            BigDecimal suggestedAmount = averageSpent.setScale(0, RoundingMode.CEILING);

            // Check for existing budget using COUNT-based exists query (REQ-2.9.1.5: flag
            // hasExistingBudget).
            // existsByUserIdAndCategoryIdAndPeriod uses COUNT so it is safe even when
            // duplicate rows
            // exist in the database; findByUserIdAndCategoryIdAndPeriod would throw
            // IncorrectResultSizeDataAccessException in that case.
            boolean hasExistingBudget =
                    budgetRepository.existsByUserIdAndCategoryIdAndPeriod(
                            userId, category.getId(), period);

            suggestions.add(
                    BudgetSuggestion.builder()
                            .categoryId(category.getId())
                            .categoryName(getDecryptedCategoryName(category))
                            .suggestedAmount(suggestedAmount)
                            .averageSpent(averageSpent)
                            .transactionCount(totalTxCount)
                            .period(period)
                            .currency(suggestionCurrency) // caller may override via request
                            .startDate(startDate)
                            .endDate(endDate)
                            .hasExistingBudget(hasExistingBudget)
                            .build());
        }

        log.debug(
                "Spending analysis complete: userId={}, suggestions={}",
                userId,
                suggestions.size());
        return suggestions;
    }

    /**
     * Bulk-creates multiple budgets in a single transaction-safe operation.
     *
     * <p>Each {@link BudgetRequest} in the list is processed independently:
     *
     * <ul>
     *   <li>Successful creations are collected into {@link BudgetBulkCreateResponse#getCreated()}.
     *   <li>{@link IllegalStateException} (duplicate category+period) increments {@link
     *       BudgetBulkCreateResponse#getSkippedCount()} without surfacing an error.
     *   <li>Any other exception adds a human-readable message to {@link
     *       BudgetBulkCreateResponse#getErrors()}.
     * </ul>
     *
     * <p>Requirement REQ-2.9.1.5: Bulk budget creation from user-confirmed suggestions
     *
     * @param userId the authenticated user's ID
     * @param requests the list of budget creation requests to process
     * @param encryptionKey the AES-256 encryption key for encrypting amounts
     * @return response summarising created, skipped, and error counts
     * @throws IllegalArgumentException if userId, requests, or encryptionKey is null
     */
    public BudgetBulkCreateResponse bulkCreateBudgets(Long userId, List<BudgetRequest> requests) {

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (requests == null) {
            throw new IllegalArgumentException("Requests list cannot be null");
        }
        log.debug("Bulk-creating {} budgets for userId={}", requests.size(), userId);

        List<BudgetResponse> created = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int skippedCount = 0;

        for (BudgetRequest request : requests) {
            try {
                BudgetResponse response = createBudget(request, userId);
                created.add(response);
            } catch (IllegalStateException e) {
                // Duplicate budget (same category + period) — skip silently
                log.debug(
                        "Skipping duplicate budget for category={}, period={}: {}",
                        request.getCategoryId(),
                        request.getPeriod(),
                        e.getMessage());
                skippedCount++;
            } catch (Exception e) {
                log.warn(
                        "Failed to create budget for category={}, period={}: {}",
                        request.getCategoryId(),
                        request.getPeriod(),
                        e.getMessage());
                errors.add(
                        String.format(
                                "Category %d (%s): %s",
                                request.getCategoryId(), request.getPeriod(), e.getMessage()));
            }
        }

        log.info(
                "Bulk create complete: created={}, skipped={}, errors={}",
                created.size(),
                skippedCount,
                errors.size());

        return BudgetBulkCreateResponse.builder()
                .created(created)
                .successCount(created.size())
                .skippedCount(skippedCount)
                .errors(errors)
                .build();
    }

    // ========== PRIVATE HELPER METHODS ==========

    /** Recursively collects the ID of the given category and all its subcategories. */
    private List<Long> getCategoryAndSubcategoryIds(Category category) {
        List<Long> ids = new ArrayList<>();
        ids.add(category.getId());
        if (category.getSubcategories() != null && !category.getSubcategories().isEmpty()) {
            for (Category sub : category.getSubcategories()) {
                ids.addAll(getCategoryAndSubcategoryIds(sub));
            }
        }
        return ids;
    }

    /**
     * Calculates the total amount spent in a category (and its subcategories) during a date range.
     *
     * <p>Sums all EXPENSE transactions in the specified category (and subcategories) between
     * startDate and endDate (inclusive).
     *
     * <p>Requirement REQ-2.9.1.2: Calculate spent amount from transactions
     *
     * @param category the root category mapping to transactions
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @param userId the user ID (for security/isolation)
     * @return total amount spent (always positive or zero)
     */
    private BigDecimal calculateSpentAmount(
            Category category, LocalDate startDate, LocalDate endDate, Long userId) {
        List<Long> categoryIds = getCategoryAndSubcategoryIds(category);
        List<Transaction> transactions =
                transactionRepository.findByCategoryIdInAndDateRange(
                        categoryIds, startDate, endDate, userId);

        BigDecimal mainSpent =
                transactions.stream()
                        .filter(t -> t.getType() == TransactionType.EXPENSE)
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        // REQUIREMENT: Include split transactions that match the category
        List<TransactionSplit> splits =
                transactionSplitRepository.findByCategoryIdInAndDateRange(
                        categoryIds, startDate, endDate, userId);
        BigDecimal splitSpent =
                splits.stream()
                        .filter(s -> s.getAmount() != null)
                        .map(TransactionSplit::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        return mainSpent.add(splitSpent);
    }

    /**
     * Determines budget status based on percentage spent.
     *
     * <p>Status thresholds:
     *
     * <ul>
     *   <li>ON_TRACK: Less than 75% spent (green)
     *   <li>WARNING: 75-100% spent (yellow)
     *   <li>EXCEEDED: Over 100% spent (red)
     * </ul>
     *
     * <p>Requirement REQ-2.9.1.2: Visual indicators based on spending thresholds
     *
     * @param percentageSpent the percentage of budget spent
     * @return status string ("ON_TRACK", "WARNING", or "EXCEEDED")
     */
    private String determineStatus(BigDecimal percentageSpent) {
        if (percentageSpent.compareTo(EXCEEDED_THRESHOLD) >= 0) {
            return "EXCEEDED";
        } else if (percentageSpent.compareTo(WARNING_THRESHOLD) >= 0) {
            return "WARNING";
        } else {
            return "ON_TRACK";
        }
    }

    /**
     * Validates that a category exists and belongs to the user.
     *
     * <p>Requirement REQ-3.2: Authorization - verify category ownership
     *
     * @param categoryId the category ID to validate
     * @param userId the user ID
     * @return the Category entity if valid
     * @throws CategoryNotFoundException if category doesn't exist or doesn't belong to user
     */
    private Category validateCategoryOwnership(Long categoryId, Long userId) {
        return categoryRepository
                .findByIdAndUserId(categoryId, userId)
                .orElseThrow(
                        () ->
                                new CategoryNotFoundException(
                                        String.format(
                                                "Category not found with id: %d for user: %d",
                                                categoryId, userId)));
    }

    /**
     * Validates that no duplicate budget exists.
     *
     * <p>Prevents creating multiple budgets for the same category and period. Excludes the current
     * budget when updating (if excludeBudgetId is provided).
     *
     * <p>Requirement REQ-2.9.1.1: Prevent duplicate budgets
     *
     * @param categoryId the category ID
     * @param period the budget period
     * @param userId the user ID
     * @param excludeBudgetId the budget ID to exclude (for updates), or null
     * @throws IllegalStateException if duplicate budget exists
     */
    private void validateNoDuplicateBudget(
            Long categoryId, BudgetPeriod period, Long userId, Long excludeBudgetId) {
        // Check if budget exists (repository returns Optional<Budget>)
        var existingBudget =
                budgetRepository.findByUserIdAndCategoryIdAndPeriod(userId, categoryId, period);

        // If budget exists and it's not the one we're updating, throw error
        if (existingBudget.isPresent()) {
            if (excludeBudgetId == null || !existingBudget.get().getId().equals(excludeBudgetId)) {
                throw new IllegalStateException(
                        String.format(
                                "Budget already exists for this category with period %s", period));
            }
        }
    }

    /**
     * Returns the display name for a category, decrypting if the category is user-created. System
     * categories have plain-text names; user categories have encrypted names.
     *
     * @param category the category entity
     * @param encryptionKey the AES-256 encryption key
     * @return the plain-text category name
     */
    private String getDecryptedCategoryName(Category category) {
        if (Boolean.TRUE.equals(category.getIsSystem())) {
            return (category.getNameKey() != null)
                    ? messageSource.getMessage(
                            category.getNameKey(),
                            null,
                            category.getName(),
                            LocaleContextHolder.getLocale())
                    : category.getName();
        }
        try {
            return category.getName();
        } catch (Exception e) {
            log.warn(
                    "Failed to decrypt category name for category {}: {}",
                    category.getId(),
                    e.getMessage());
            return category.getName();
        }
    }

    /**
     * Helper method to decrypt amount and map Budget to BudgetResponse DTO.
     *
     * @param budget the budget entity with encrypted amount
     * @param category the category entity for denormalization
     * @param encryptionKey the encryption key for decryption
     * @return the budget response with decrypted amount and category details
     */
    private BudgetResponse toResponseWithDecryption(Budget budget, Category category) {
        // Amount already decrypted by JPA converter
        String decryptedAmount;
        try {
            decryptedAmount = budget.getAmount();
        } catch (Exception e) {
            log.warn(
                    "Failed to decrypt budget amount for budget {}: {} - treating stored value as plaintext",
                    budget.getId(),
                    e.getMessage());
            decryptedAmount = budget.getAmount();
        }

        // Safely parse to BigDecimal
        BigDecimal amountValue;
        if (decryptedAmount == null || decryptedAmount.isBlank()) {
            amountValue = BigDecimal.ZERO;
        } else {
            try {
                amountValue = new BigDecimal(decryptedAmount);
            } catch (NumberFormatException nfe) {
                log.warn(
                        "Stored budget amount for budget {} is not a valid number: '{}' - defaulting to 0",
                        budget.getId(),
                        decryptedAmount);
                amountValue = BigDecimal.ZERO;
            }
        }

        // Create response with decrypted/parsed data
        BudgetResponse response = budgetMapper.toResponse(budget);
        response.setAmount(amountValue);
        response.setCategoryName(getDecryptedCategoryName(category));
        response.setCategoryType(category.getType());

        return response;
    }

    /**
     * Splits a budget date range into sub-period windows matching the budget's period type.
     *
     * <p>Each returned array contains exactly two elements: {@code [periodStart, periodEnd]}.
     * Periods are non-overlapping and cover the entire [startDate, endDate] range.
     *
     * @param period the budget period type
     * @param startDate the start of the overall budget range (inclusive)
     * @param endDate the end of the overall budget range (inclusive)
     * @return ordered list of sub-period date ranges
     */
    private List<LocalDate[]> buildSubPeriodWindows(
            BudgetPeriod period, LocalDate startDate, LocalDate endDate) {
        List<LocalDate[]> windows = new ArrayList<>();
        LocalDate cursor = startDate;

        while (!cursor.isAfter(endDate)) {
            LocalDate windowEnd;
            switch (period) {
                case WEEKLY:
                    // 7-day window
                    windowEnd = cursor.plusDays(6);
                    break;
                case MONTHLY:
                    // Calendar-month window: advance to end of current month
                    windowEnd = cursor.withDayOfMonth(cursor.lengthOfMonth());
                    break;
                case QUARTERLY:
                    // 3-month window aligned to the cursor month
                    windowEnd = cursor.plusMonths(3).minusDays(1);
                    break;
                case YEARLY:
                    // 1-year window
                    windowEnd = cursor.plusYears(1).minusDays(1);
                    break;
                default:
                    windowEnd = endDate;
            }

            // Clamp to overall end date
            if (windowEnd.isAfter(endDate)) {
                windowEnd = endDate;
            }

            windows.add(new LocalDate[] {cursor, windowEnd});

            // Advance cursor to next window start
            cursor = windowEnd.plusDays(1);
        }

        return windows;
    }

    /**
     * Produces a human-readable label for a sub-period.
     *
     * <p>Examples: "Jan 2024", "Feb 2024", "Week of 01 Jan", "Q1 2024"
     *
     * @param period the budget period type
     * @param periodStart the start of the sub-period
     * @param periodEnd the end of the sub-period
     * @return human-readable label
     */
    private String formatPeriodLabel(
            BudgetPeriod period, LocalDate periodStart, LocalDate periodEnd) {
        switch (period) {
            case WEEKLY:
                return "Week of "
                        + periodStart.format(DateTimeFormatter.ofPattern("dd MMM", Locale.FRENCH));
            case MONTHLY:
                return periodStart.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.FRENCH));
            case QUARTERLY:
                int quarter = (periodStart.getMonthValue() - 1) / 3 + 1;
                return "Q" + quarter + " " + periodStart.getYear();
            case YEARLY:
                return String.valueOf(periodStart.getYear());
            default:
                return periodStart + " – " + periodEnd;
        }
    }

    private Long resolveCurrencyId(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) return null;
        return currencyRepository
                .findByCode(currencyCode)
                .map(org.openfinance.entity.Currency::getId)
                .orElse(null);
    }

    private void indexBudgetSearchTokens(Budget budget, String notes) {
        try {
            javax.crypto.SecretKey key = org.openfinance.security.EncryptionContext.getKey();
            if (key == null) {
                return;
            }
            javax.crypto.SecretKey searchKey = searchTokenService.deriveSearchKey(key);
            searchTokenService.indexEntity(
                    budget.getUserId(),
                    "BUDGET",
                    budget.getId(),
                    java.util.List.<String[]>of(new String[] {"notes", notes}),
                    searchKey);
        } catch (Exception e) {
            log.warn("Failed to index budget {} search tokens: {}", budget.getId(), e.getMessage());
        }
    }
}
