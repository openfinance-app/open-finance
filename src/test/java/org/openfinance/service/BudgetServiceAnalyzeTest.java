package org.openfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.BudgetBulkCreateResponse;
import org.openfinance.dto.BudgetRequest;
import org.openfinance.dto.BudgetResponse;
import org.openfinance.dto.BudgetSuggestion;
import org.openfinance.entity.*;
import org.openfinance.mapper.BudgetMapper;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.TransactionSplitRepository;
import org.openfinance.security.EncryptionService;

/**
 * Unit tests for BudgetService new methods: analyzeCategorySpending and bulkCreateBudgets.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>analyzeCategorySpending() - spending analysis and suggestion generation
 *   <li>bulkCreateBudgets() - bulk budget creation with error handling
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceAnalyzeTest {

    @Mock private BudgetRepository budgetRepository;

    @Mock private CategoryRepository categoryRepository;

    @Mock private TransactionRepository transactionRepository;

    @Mock private TransactionSplitRepository transactionSplitRepository;

    @Mock private EncryptionService encryptionService;

    @Mock private BudgetMapper budgetMapper;

    @Mock private OperationHistoryService operationHistoryService;

    @InjectMocks private BudgetService budgetService;

    private User testUser;
    private Category testCategory1;
    private Category testCategory2;
    private Transaction testTransaction;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = User.builder().id(1L).username("testuser").email("test@example.com").build();

        // Create test categories
        testCategory1 =
                Category.builder()
                        .id(1L)
                        .userId(1L)
                        .name("Groceries")
                        .type(CategoryType.EXPENSE)
                        .isSystem(true)
                        .build();

        testCategory2 =
                Category.builder()
                        .id(2L)
                        .userId(1L)
                        .name("Entertainment")
                        .type(CategoryType.EXPENSE)
                        .isSystem(true)
                        .build();

        // Create test transaction
        testTransaction =
                Transaction.builder()
                        .id(1L)
                        .userId(1L)
                        .categoryId(1L)
                        .amount(new BigDecimal("250.00"))
                        .type(TransactionType.EXPENSE)
                        .date(LocalDate.now().minusMonths(1))
                        .build();
    }

    // ========== ANALYZE CATEGORY SPENDING TESTS ==========

    @Test
    void shouldAnalyzeCategorySpendingWithAllCategories() {
        // Given
        List<Category> categories = Arrays.asList(testCategory1, testCategory2);
        List<Transaction> transactions = Arrays.asList(testTransaction);

        when(categoryRepository.findByUserIdAndType(1L, CategoryType.EXPENSE))
                .thenReturn(categories);
        when(transactionRepository.findByCategoryIdInAndDateRange(anyList(), any(), any(), eq(1L)))
                .thenReturn(transactions); // Return transactions for any category
        when(budgetRepository.existsByUserIdAndCategoryIdAndPeriod(
                        anyLong(), anyLong(), eq(BudgetPeriod.MONTHLY)))
                .thenReturn(false);

        // When
        List<BudgetSuggestion> suggestions =
                budgetService.analyzeCategorySpending(1L, BudgetPeriod.MONTHLY, 6, null);

        // Then
        assertThat(suggestions).hasSize(2); // Both categories have transactions
        BudgetSuggestion suggestion = suggestions.get(0);
        assertThat(suggestion.getCategoryId()).isEqualTo(1L);
        assertThat(suggestion.getCategoryName()).isEqualTo("Groceries");
        assertThat(suggestion.getSuggestedAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(suggestion.getAverageSpent()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(suggestion.getTransactionCount()).isEqualTo(7);
        assertThat(suggestion.getPeriod()).isEqualTo(BudgetPeriod.MONTHLY);
        assertThat(suggestion.getCurrency()).isEqualTo("EUR");
        assertThat(suggestion.isHasExistingBudget()).isFalse();

        verify(categoryRepository).findByUserIdAndType(1L, CategoryType.EXPENSE);
        verify(transactionRepository, times(14))
                .findByCategoryIdInAndDateRange(
                        anyList(), any(), any(), eq(1L)); // 7 months * 2 categories
    }

    @Test
    void shouldAnalyzeCategorySpendingWithSpecificCategoryIdsFilter() {
        // Given
        List<Long> categoryIds = Arrays.asList(1L);
        List<Transaction> transactions = Arrays.asList(testTransaction);

        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory1));
        when(transactionRepository.findByCategoryIdInAndDateRange(anyList(), any(), any(), eq(1L)))
                .thenReturn(transactions);
        when(budgetRepository.existsByUserIdAndCategoryIdAndPeriod(1L, 1L, BudgetPeriod.MONTHLY))
                .thenReturn(false);

        // When
        List<BudgetSuggestion> suggestions =
                budgetService.analyzeCategorySpending(1L, BudgetPeriod.MONTHLY, 6, categoryIds);

        // Then
        assertThat(suggestions).hasSize(1);
        BudgetSuggestion suggestion = suggestions.get(0);
        assertThat(suggestion.getCategoryId()).isEqualTo(1L);
        assertThat(suggestion.getCategoryName()).isEqualTo("Groceries");

        verify(categoryRepository).findByIdAndUserId(1L, 1L);
        verify(transactionRepository, times(7))
                .findByCategoryIdInAndDateRange(anyList(), any(), any(), eq(1L));
    }

    @Test
    void shouldSkipCategoriesWithZeroTransactions() {
        // Given
        List<Category> categories = Arrays.asList(testCategory1, testCategory2);

        when(categoryRepository.findByUserIdAndType(1L, CategoryType.EXPENSE))
                .thenReturn(categories);
        when(transactionRepository.findByCategoryIdInAndDateRange(anyList(), any(), any(), eq(1L)))
                .thenReturn(Collections.emptyList());

        // When
        List<BudgetSuggestion> suggestions =
                budgetService.analyzeCategorySpending(1L, BudgetPeriod.MONTHLY, 6, null);

        // Then
        assertThat(suggestions).isEmpty();

        verify(categoryRepository).findByUserIdAndType(1L, CategoryType.EXPENSE);
        verify(transactionRepository, times(14))
                .findByCategoryIdInAndDateRange(anyList(), any(), any(), eq(1L));
    }

    @Test
    void shouldSetHasExistingBudgetFlagCorrectly() {
        // Given
        List<Category> categories = Arrays.asList(testCategory1);
        List<Transaction> transactions = Arrays.asList(testTransaction);

        when(categoryRepository.findByUserIdAndType(1L, CategoryType.EXPENSE))
                .thenReturn(categories);
        when(transactionRepository.findByCategoryIdInAndDateRange(anyList(), any(), any(), eq(1L)))
                .thenReturn(transactions);
        // existsByUserIdAndCategoryIdAndPeriod returns true → hasExistingBudget should
        // be true
        when(budgetRepository.existsByUserIdAndCategoryIdAndPeriod(1L, 1L, BudgetPeriod.MONTHLY))
                .thenReturn(true);

        // When
        List<BudgetSuggestion> suggestions =
                budgetService.analyzeCategorySpending(1L, BudgetPeriod.MONTHLY, 6, null);

        // Then
        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).isHasExistingBudget()).isTrue();

        verify(budgetRepository).existsByUserIdAndCategoryIdAndPeriod(1L, 1L, BudgetPeriod.MONTHLY);
    }

    @Test
    void shouldDefaultCurrencyToEUR() {
        // Given
        List<Category> categories = Arrays.asList(testCategory1);
        List<Transaction> transactions = Arrays.asList(testTransaction);

        when(categoryRepository.findByUserIdAndType(1L, CategoryType.EXPENSE))
                .thenReturn(categories);
        when(transactionRepository.findByCategoryIdInAndDateRange(anyList(), any(), any(), eq(1L)))
                .thenReturn(transactions);
        when(budgetRepository.existsByUserIdAndCategoryIdAndPeriod(1L, 1L, BudgetPeriod.MONTHLY))
                .thenReturn(false);

        // When
        List<BudgetSuggestion> suggestions =
                budgetService.analyzeCategorySpending(1L, BudgetPeriod.MONTHLY, 6, null);

        // Then
        assertThat(suggestions.get(0).getCurrency()).isEqualTo("EUR");
    }

    @Test
    void shouldComputeLookbackWindowFromTodayMinusLookbackMonths() {
        // Given
        List<Category> categories = Arrays.asList(testCategory1);
        List<Transaction> transactions = Arrays.asList(testTransaction);

        when(categoryRepository.findByUserIdAndType(1L, CategoryType.EXPENSE))
                .thenReturn(categories);
        when(transactionRepository.findByCategoryIdInAndDateRange(anyList(), any(), any(), eq(1L)))
                .thenReturn(transactions);
        when(budgetRepository.existsByUserIdAndCategoryIdAndPeriod(1L, 1L, BudgetPeriod.MONTHLY))
                .thenReturn(false);

        LocalDate today = LocalDate.now();
        LocalDate expectedStartDate = today.minusMonths(3);

        // When
        List<BudgetSuggestion> suggestions =
                budgetService.analyzeCategorySpending(1L, BudgetPeriod.MONTHLY, 3, null);

        // Then
        assertThat(suggestions).hasSize(1);
        BudgetSuggestion suggestion = suggestions.get(0);
        assertThat(suggestion.getStartDate()).isEqualTo(expectedStartDate);
        assertThat(suggestion.getEndDate()).isEqualTo(today);

        verify(transactionRepository, times(4))
                .findByCategoryIdInAndDateRange(anyList(), any(), any(), eq(1L));
    }

    // ========== BULK CREATE BUDGETS TESTS ==========

    @Test
    void shouldBulkCreateBudgetsAllSucceed() {
        // Given
        BudgetRequest request1 =
                BudgetRequest.builder()
                        .categoryId(1L)
                        .amount(new BigDecimal("500.00"))
                        .currency("EUR")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 1, 1))
                        .endDate(LocalDate.of(2026, 12, 31))
                        .rollover(false)
                        .build();

        BudgetRequest request2 =
                BudgetRequest.builder()
                        .categoryId(2L)
                        .amount(new BigDecimal("300.00"))
                        .currency("EUR")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 1, 1))
                        .endDate(LocalDate.of(2026, 12, 31))
                        .rollover(false)
                        .build();

        List<BudgetRequest> requests = Arrays.asList(request1, request2);

        Budget budget1 = Budget.builder().id(1L).build();
        Budget budget2 = Budget.builder().id(2L).build();

        BudgetResponse response1 = BudgetResponse.builder().id(1L).build();
        BudgetResponse response2 = BudgetResponse.builder().id(2L).build();

        // Mock the repository calls that createBudget uses
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory1));
        when(categoryRepository.findByIdAndUserId(2L, 1L)).thenReturn(Optional.of(testCategory2));
        when(budgetRepository.findByUserIdAndCategoryIdAndPeriod(1L, 1L, BudgetPeriod.MONTHLY))
                .thenReturn(Optional.empty());
        when(budgetRepository.findByUserIdAndCategoryIdAndPeriod(1L, 2L, BudgetPeriod.MONTHLY))
                .thenReturn(Optional.empty());
        when(budgetRepository.save(any(Budget.class))).thenReturn(budget1, budget2);
        when(budgetMapper.toEntity(any(BudgetRequest.class))).thenReturn(budget1, budget2);
        when(budgetMapper.toResponse(any(Budget.class))).thenReturn(response1, response2);

        // When
        BudgetBulkCreateResponse response = budgetService.bulkCreateBudgets(1L, requests);

        // Then
        assertThat(response.getCreated()).hasSize(2);
        assertThat(response.getSuccessCount()).isEqualTo(2);
        assertThat(response.getSkippedCount()).isEqualTo(0);
        assertThat(response.getErrors()).isEmpty();

        verify(budgetRepository, times(2)).save(any(Budget.class));
    }

    @Test
    void shouldBulkCreateBudgetsHandleDuplicates() {
        // Given
        BudgetRequest request =
                BudgetRequest.builder()
                        .categoryId(1L)
                        .amount(new BigDecimal("500.00"))
                        .currency("EUR")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 1, 1))
                        .endDate(LocalDate.of(2026, 12, 31))
                        .rollover(false)
                        .build();

        List<BudgetRequest> requests = Arrays.asList(request);

        Budget existingBudget = Budget.builder().id(999L).build();

        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory1));
        when(budgetRepository.findByUserIdAndCategoryIdAndPeriod(1L, 1L, BudgetPeriod.MONTHLY))
                .thenReturn(Optional.of(existingBudget)); // Duplicate exists

        // When
        BudgetBulkCreateResponse response = budgetService.bulkCreateBudgets(1L, requests);

        // Then
        assertThat(response.getCreated()).isEmpty();
        assertThat(response.getSuccessCount()).isEqualTo(0);
        assertThat(response.getSkippedCount()).isEqualTo(1);
        assertThat(response.getErrors()).isEmpty();

        verify(budgetRepository, never()).save(any(Budget.class));
    }

    @Test
    void shouldBulkCreateBudgetsHandleOtherExceptions() {
        // Given
        BudgetRequest request =
                BudgetRequest.builder()
                        .categoryId(1L)
                        .amount(new BigDecimal("500.00"))
                        .currency("EUR")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 1, 1))
                        .endDate(LocalDate.of(2026, 12, 31))
                        .rollover(false)
                        .build();

        List<BudgetRequest> requests = Arrays.asList(request);

        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory1));
        when(budgetRepository.findByUserIdAndCategoryIdAndPeriod(1L, 1L, BudgetPeriod.MONTHLY))
                .thenReturn(Optional.empty());
        when(budgetMapper.toEntity(any(BudgetRequest.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When
        BudgetBulkCreateResponse response = budgetService.bulkCreateBudgets(1L, requests);

        // Then
        assertThat(response.getCreated()).isEmpty();
        assertThat(response.getSuccessCount()).isEqualTo(0);
        assertThat(response.getSkippedCount()).isEqualTo(0);
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().get(0)).contains("Database error");

        verify(budgetRepository, never()).save(any(Budget.class));
    }

    @Test
    void shouldBulkCreateBudgetsHandleEmptyRequestList() {
        // Given
        List<BudgetRequest> requests = Collections.emptyList();

        // When
        BudgetBulkCreateResponse response = budgetService.bulkCreateBudgets(1L, requests);

        // Then
        assertThat(response.getCreated()).isEmpty();
        assertThat(response.getSuccessCount()).isEqualTo(0);
        assertThat(response.getSkippedCount()).isEqualTo(0);
        assertThat(response.getErrors()).isEmpty();

        verifyNoInteractions(
                categoryRepository,
                budgetRepository,
                transactionRepository,
                budgetMapper,
                encryptionService);
    }

    @Test
    void shouldThrowExceptionForNullUserIdInAnalyzeCategorySpending() {
        // When/Then
        assertThatThrownBy(
                        () ->
                                budgetService.analyzeCategorySpending(
                                        null, BudgetPeriod.MONTHLY, 6, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null");
    }

    @Test
    void shouldThrowExceptionForNullPeriodInAnalyzeCategorySpending() {
        // When/Then
        assertThatThrownBy(() -> budgetService.analyzeCategorySpending(1L, null, 6, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Period cannot be null");
    }

    @Test
    void shouldThrowExceptionForNullUserIdInBulkCreateBudgets() {
        // Given
        List<BudgetRequest> requests = Collections.emptyList();

        // When/Then
        assertThatThrownBy(() -> budgetService.bulkCreateBudgets(null, requests))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null");
    }

    @Test
    void shouldThrowExceptionForNullRequestsInBulkCreateBudgets() {
        // When/Then
        assertThatThrownBy(() -> budgetService.bulkCreateBudgets(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Requests list cannot be null");
    }
}
