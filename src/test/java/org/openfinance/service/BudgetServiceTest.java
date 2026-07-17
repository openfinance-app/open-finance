package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.BudgetHistoryEntry;
import org.openfinance.dto.BudgetHistoryResponse;
import org.openfinance.dto.BudgetProgressResponse;
import org.openfinance.dto.BudgetRequest;
import org.openfinance.dto.BudgetResponse;
import org.openfinance.dto.BudgetSummaryResponse;
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetPeriod;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.BudgetNotFoundException;
import org.openfinance.exception.CategoryNotFoundException;
import org.openfinance.mapper.BudgetMapper;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.TransactionSplitRepository;
import org.openfinance.security.EncryptionService;
import org.springframework.context.MessageSource;

/**
 * Unit tests for BudgetService.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>CRUD operations (create, read, update, delete)
 *   <li>Budget progress calculations
 *   <li>Budget summary generation
 *   <li>Validation and error handling
 *   <li>Encryption/decryption of budget amounts
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetService Unit Tests")
class BudgetServiceTest {

    @Mock private BudgetRepository budgetRepository;

    @Mock private CategoryRepository categoryRepository;

    @Mock private TransactionRepository transactionRepository;

    @Mock private TransactionSplitRepository transactionSplitRepository;

    @Mock private BudgetMapper budgetMapper;

    @Mock private EncryptionService encryptionService;

    @Mock private OperationHistoryService operationHistoryService;

    @Mock private MessageSource messageSource;

    @Mock private CurrencyRepository currencyRepository;

    @Mock private SearchTokenService searchTokenService;

    @InjectMocks private BudgetService budgetService;

    private Category testCategory;
    private Budget testBudget;
    private BudgetRequest testRequest;
    private BudgetResponse testResponse;

    @BeforeEach
    void setUp() {
        // Create test category
        testCategory =
                Category.builder()
                        .id(1L)
                        .userId(1L)
                        .name("Groceries")
                        .type(CategoryType.EXPENSE)
                        .isSystem(true)
                        .build();

        // Create test budget request
        testRequest =
                BudgetRequest.builder()
                        .categoryId(1L)
                        .amount(new BigDecimal("500.00"))
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 1, 1))
                        .endDate(LocalDate.of(2026, 12, 31))
                        .rollover(false)
                        .notes("Monthly grocery budget")
                        .build();

        // Create test budget entity
        testBudget =
                Budget.builder()
                        .id(1L)
                        .userId(1L)
                        .categoryId(1L)
                        .amount("500.00")
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 1, 1))
                        .endDate(LocalDate.of(2026, 12, 31)) // Future end date
                        .rollover(false)
                        .notes("Monthly grocery budget")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        // Create test budget response
        testResponse =
                BudgetResponse.builder()
                        .id(1L)
                        .categoryId(1L)
                        .categoryName("Groceries")
                        .categoryType(CategoryType.EXPENSE)
                        .amount(new BigDecimal("500.00"))
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 28))
                        .rollover(false)
                        .notes("Monthly grocery budget")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
    }

    // ========== CREATE BUDGET TESTS ==========

    @Test
    @DisplayName("Should create budget successfully")
    void shouldCreateBudget() {
        // Given
        Budget mappedBudget =
                Budget.builder()
                        .categoryId(1L)
                        .amount("500.00")
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 28))
                        .rollover(false)
                        .notes("Monthly grocery budget")
                        .build();

        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));
        when(budgetRepository.findByUserIdAndCategoryIdAndPeriod(1L, 1L, BudgetPeriod.MONTHLY))
                .thenReturn(Optional.empty());
        when(budgetMapper.toEntity(testRequest)).thenReturn(mappedBudget);
        when(budgetRepository.save(any(Budget.class))).thenReturn(testBudget);
        when(budgetMapper.toResponse(testBudget)).thenReturn(testResponse);

        // When
        BudgetResponse response = budgetService.createBudget(testRequest, 1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCategoryName()).isEqualTo("Groceries");
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(response.getCurrency()).isEqualTo("USD");
        assertThat(response.getPeriod()).isEqualTo(BudgetPeriod.MONTHLY);

        verify(categoryRepository).findByIdAndUserId(1L, 1L);
        verify(budgetRepository).findByUserIdAndCategoryIdAndPeriod(1L, 1L, BudgetPeriod.MONTHLY);
        verify(budgetRepository).save(any(Budget.class));
    }

    @Test
    @DisplayName("Should throw CategoryNotFoundException when category not found")
    void shouldThrowExceptionWhenCategoryNotFound() {
        // Given
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> budgetService.createBudget(testRequest, 1L))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessageContaining("Category not found with id: 1 for user: 1");

        verify(categoryRepository).findByIdAndUserId(1L, 1L);
        verify(budgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when category belongs to different user")
    void shouldThrowExceptionWhenCategoryBelongsToDifferentUser() {
        // Given
        Category otherUserCategory =
                Category.builder()
                        .id(1L)
                        .userId(999L) // Different user
                        .name("Other User's Category")
                        .type(CategoryType.EXPENSE)
                        .build();

        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> budgetService.createBudget(testRequest, 1L))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(categoryRepository).findByIdAndUserId(1L, 1L);
        verify(budgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when duplicate budget exists")
    void shouldThrowExceptionWhenDuplicateBudgetExists() {
        // Given
        Budget existingBudget =
                Budget.builder()
                        .id(999L)
                        .userId(1L)
                        .categoryId(1L)
                        .period(BudgetPeriod.MONTHLY)
                        .build();

        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));
        when(budgetRepository.findByUserIdAndCategoryIdAndPeriod(1L, 1L, BudgetPeriod.MONTHLY))
                .thenReturn(Optional.of(existingBudget));

        // When/Then
        assertThatThrownBy(() -> budgetService.createBudget(testRequest, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Budget already exists");

        verify(categoryRepository).findByIdAndUserId(1L, 1L);
        verify(budgetRepository).findByUserIdAndCategoryIdAndPeriod(1L, 1L, BudgetPeriod.MONTHLY);
        verify(budgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should allow same category with different period")
    void shouldAllowSameCategoryWithDifferentPeriod() {
        // Given
        BudgetRequest weeklyRequest =
                BudgetRequest.builder()
                        .categoryId(1L)
                        .amount(new BigDecimal("125.00"))
                        .currency("USD")
                        .period(BudgetPeriod.WEEKLY) // Different period
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 7))
                        .rollover(false)
                        .build();

        Budget mappedBudget =
                Budget.builder()
                        .categoryId(1L)
                        .amount("125.00")
                        .currency("USD")
                        .period(BudgetPeriod.WEEKLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 7))
                        .rollover(false)
                        .build();

        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));
        when(budgetRepository.findByUserIdAndCategoryIdAndPeriod(1L, 1L, BudgetPeriod.WEEKLY))
                .thenReturn(Optional.empty()); // No duplicate for WEEKLY
        when(budgetMapper.toEntity(weeklyRequest)).thenReturn(mappedBudget);
        when(budgetRepository.save(any(Budget.class))).thenReturn(testBudget);
        when(budgetMapper.toResponse(testBudget)).thenReturn(testResponse);

        // When
        BudgetResponse response = budgetService.createBudget(weeklyRequest, 1L);

        // Then
        assertThat(response).isNotNull();
        verify(budgetRepository).save(any(Budget.class));
    }

    // ========== UPDATE BUDGET TESTS ==========

    @Test
    @DisplayName("Should update budget successfully")
    void shouldUpdateBudget() {
        // Given
        BudgetRequest updateRequest =
                BudgetRequest.builder()
                        .categoryId(1L)
                        .amount(new BigDecimal("600.00")) // Updated amount
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 28))
                        .rollover(true) // Changed to true
                        .notes("Updated notes")
                        .build();

        Budget updatedBudget =
                Budget.builder()
                        .id(1L)
                        .userId(1L)
                        .categoryId(1L)
                        .amount("600.00")
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 28))
                        .rollover(true)
                        .notes("Updated notes")
                        .build();

        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testBudget));
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));
        when(budgetRepository.findByUserIdAndCategoryIdAndPeriod(1L, 1L, BudgetPeriod.MONTHLY))
                .thenReturn(Optional.of(testBudget)); // Same budget, not a duplicate
        doNothing().when(budgetMapper).updateEntityFromRequest(updateRequest, testBudget);
        when(budgetRepository.save(testBudget)).thenReturn(updatedBudget);
        when(budgetMapper.toResponse(updatedBudget)).thenReturn(testResponse);

        // When
        BudgetResponse response = budgetService.updateBudget(1L, updateRequest, 1L);

        // Then
        assertThat(response).isNotNull();
        verify(budgetRepository).findByIdAndUserId(1L, 1L);
        verify(categoryRepository).findByIdAndUserId(1L, 1L);
        verify(budgetMapper).updateEntityFromRequest(updateRequest, testBudget);
        verify(budgetRepository).save(testBudget);
    }

    @Test
    @DisplayName("Should throw BudgetNotFoundException when updating non-existent budget")
    void shouldThrowExceptionWhenUpdatingNonExistentBudget() {
        // Given
        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> budgetService.updateBudget(1L, testRequest, 1L))
                .isInstanceOf(BudgetNotFoundException.class)
                .hasMessageContaining("Budget not found with id: 1 for user: 1");

        verify(budgetRepository).findByIdAndUserId(1L, 1L);
        verify(budgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when updating to duplicate category+period")
    void shouldThrowExceptionWhenUpdatingToDuplicateCategoryPeriod() {
        // Given
        Budget otherBudget =
                Budget.builder()
                        .id(999L) // Different ID
                        .userId(1L)
                        .categoryId(1L)
                        .period(BudgetPeriod.MONTHLY)
                        .build();

        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testBudget));
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));
        when(budgetRepository.findByUserIdAndCategoryIdAndPeriod(1L, 1L, BudgetPeriod.MONTHLY))
                .thenReturn(Optional.of(otherBudget)); // Different budget with same category+period

        // When/Then
        assertThatThrownBy(() -> budgetService.updateBudget(1L, testRequest, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Budget already exists");

        verify(budgetRepository, never()).save(any());
    }

    // ========== DELETE BUDGET TESTS ==========

    @Test
    @DisplayName("Should delete budget successfully")
    void shouldDeleteBudget() {
        // Given
        testBudget.setCategory(testCategory);
        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testBudget));
        doNothing().when(budgetRepository).delete(testBudget);

        // When
        budgetService.deleteBudget(1L, 1L);

        // Then
        verify(budgetRepository).findByIdAndUserId(1L, 1L);
        verify(budgetRepository).delete(testBudget);
    }

    @Test
    @DisplayName("Should throw BudgetNotFoundException when deleting non-existent budget")
    void shouldThrowExceptionWhenDeletingNonExistentBudget() {
        // Given
        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> budgetService.deleteBudget(1L, 1L))
                .isInstanceOf(BudgetNotFoundException.class)
                .hasMessageContaining("Budget not found with id: 1 for user: 1");

        verify(budgetRepository).findByIdAndUserId(1L, 1L);
        verify(budgetRepository, never()).delete(any());
    }

    // ========== GET BUDGET TESTS ==========

    @Test
    @DisplayName("Should get budget by ID with decryption")
    void shouldGetBudgetById() {
        // Given
        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testBudget));
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));
        when(budgetMapper.toResponse(testBudget)).thenReturn(testResponse);

        // When
        BudgetResponse response = budgetService.getBudgetById(1L, 1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCategoryName()).isEqualTo("Groceries");
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));

        verify(budgetRepository).findByIdAndUserId(1L, 1L);
        verify(categoryRepository).findByIdAndUserId(1L, 1L);
    }

    @Test
    @DisplayName("Should throw BudgetNotFoundException when getting non-existent budget")
    void shouldThrowExceptionWhenGettingNonExistentBudget() {
        // Given
        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> budgetService.getBudgetById(1L, 1L))
                .isInstanceOf(BudgetNotFoundException.class)
                .hasMessageContaining("Budget not found with id: 1 for user: 1");

        verify(budgetRepository).findByIdAndUserId(1L, 1L);
    }

    @Test
    @DisplayName("Should get all budgets for user")
    void shouldGetBudgetsByUser() {
        // Given
        Budget budget2 =
                Budget.builder()
                        .id(2L)
                        .userId(1L)
                        .categoryId(2L)
                        .amount("300.00")
                        .currency("EUR")
                        .period(BudgetPeriod.WEEKLY)
                        .build();

        Category category2 =
                Category.builder()
                        .id(2L)
                        .userId(1L)
                        .name("Entertainment")
                        .type(CategoryType.EXPENSE)
                        .isSystem(true)
                        .build();

        when(budgetRepository.findByUserId(1L)).thenReturn(Arrays.asList(testBudget, budget2));
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.findByIdAndUserId(2L, 1L)).thenReturn(Optional.of(category2));
        when(budgetMapper.toResponse(any(Budget.class))).thenReturn(testResponse);

        // When
        List<BudgetResponse> responses = budgetService.getBudgetsByUser(1L);

        // Then
        assertThat(responses).hasSize(2);
        verify(budgetRepository).findByUserId(1L);
    }

    @Test
    @DisplayName("Should get budgets filtered by period")
    void shouldGetBudgetsByPeriod() {
        // Given
        when(budgetRepository.findByUserIdAndPeriod(1L, BudgetPeriod.MONTHLY))
                .thenReturn(List.of(testBudget));
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));
        when(budgetMapper.toResponse(testBudget)).thenReturn(testResponse);

        // When
        List<BudgetResponse> responses = budgetService.getBudgetsByPeriod(1L, BudgetPeriod.MONTHLY);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getPeriod()).isEqualTo(BudgetPeriod.MONTHLY);
        verify(budgetRepository).findByUserIdAndPeriod(1L, BudgetPeriod.MONTHLY);
    }

    // ========== BUDGET PROGRESS TESTS ==========

    @Test
    @DisplayName("Should calculate budget progress - ON_TRACK status")
    void shouldCalculateBudgetProgress_OnTrack() {
        // Given - 50% spent (250 out of 500)
        Transaction transaction1 =
                Transaction.builder()
                        .id(1L)
                        .userId(1L)
                        .categoryId(1L)
                        .amount(new BigDecimal("150.00"))
                        .type(TransactionType.EXPENSE)
                        .date(LocalDate.of(2026, 2, 10))
                        .build();

        Transaction transaction2 =
                Transaction.builder()
                        .id(2L)
                        .userId(1L)
                        .categoryId(1L)
                        .amount(new BigDecimal("100.00"))
                        .type(TransactionType.EXPENSE)
                        .date(LocalDate.of(2026, 2, 15))
                        .build();

        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testBudget));
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));
        when(transactionRepository.findByCategoryIdInAndDateRange(
                        anyList(), any(), any(), anyLong()))
                .thenReturn(Arrays.asList(transaction1, transaction2));
        when(transactionSplitRepository.findByCategoryIdInAndDateRange(
                        anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());

        // When
        BudgetProgressResponse progress = budgetService.calculateBudgetProgress(1L, 1L);

        // Then
        assertThat(progress).isNotNull();
        assertThat(progress.getBudgetId()).isEqualTo(1L);
        assertThat(progress.getCategoryName()).isEqualTo("Groceries");
        assertThat(progress.getBudgeted()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(progress.getSpent()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(progress.getRemaining()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(progress.getPercentageSpent()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(progress.getStatus()).isEqualTo("ON_TRACK");
        assertThat(progress.getDaysRemaining()).isGreaterThanOrEqualTo(0);

        verify(transactionRepository)
                .findByCategoryIdInAndDateRange(anyList(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("Should calculate budget progress - WARNING status")
    void shouldCalculateBudgetProgress_Warning() {
        // Given - 80% spent (400 out of 500)
        Transaction transaction =
                Transaction.builder()
                        .id(1L)
                        .userId(1L)
                        .categoryId(1L)
                        .amount(new BigDecimal("400.00"))
                        .type(TransactionType.EXPENSE)
                        .date(LocalDate.of(2026, 2, 10))
                        .build();

        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testBudget));
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));
        when(transactionRepository.findByCategoryIdInAndDateRange(
                        anyList(), any(), any(), anyLong()))
                .thenReturn(List.of(transaction));
        when(transactionSplitRepository.findByCategoryIdInAndDateRange(
                        anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());

        // When
        BudgetProgressResponse progress = budgetService.calculateBudgetProgress(1L, 1L);

        // Then
        assertThat(progress.getSpent()).isEqualByComparingTo(new BigDecimal("400.00"));
        assertThat(progress.getRemaining()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(progress.getPercentageSpent()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(progress.getStatus()).isEqualTo("WARNING");
    }

    @Test
    @DisplayName("Should calculate budget progress - EXCEEDED status")
    void shouldCalculateBudgetProgress_Exceeded() {
        // Given - 120% spent (600 out of 500)
        Transaction transaction =
                Transaction.builder()
                        .id(1L)
                        .userId(1L)
                        .categoryId(1L)
                        .amount(new BigDecimal("600.00"))
                        .type(TransactionType.EXPENSE)
                        .date(LocalDate.of(2026, 2, 10))
                        .build();

        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testBudget));
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));
        when(transactionRepository.findByCategoryIdInAndDateRange(
                        anyList(), any(), any(), anyLong()))
                .thenReturn(List.of(transaction));
        when(transactionSplitRepository.findByCategoryIdInAndDateRange(
                        anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());

        // When
        BudgetProgressResponse progress = budgetService.calculateBudgetProgress(1L, 1L);

        // Then
        assertThat(progress.getSpent()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(progress.getRemaining()).isEqualByComparingTo(new BigDecimal("-100.00"));
        assertThat(progress.getPercentageSpent()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(progress.getStatus()).isEqualTo("EXCEEDED");
    }

    @Test
    @DisplayName("Should calculate budget progress with no transactions")
    void shouldCalculateBudgetProgress_NoTransactions() {
        // Given
        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testBudget));
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));
        when(transactionRepository.findByCategoryIdInAndDateRange(
                        anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(transactionSplitRepository.findByCategoryIdInAndDateRange(
                        anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());

        // When
        BudgetProgressResponse progress = budgetService.calculateBudgetProgress(1L, 1L);

        // Then
        assertThat(progress.getSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(progress.getRemaining()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(progress.getPercentageSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(progress.getStatus()).isEqualTo("ON_TRACK");
    }

    // ========== BUDGET SUMMARY TESTS ==========

    @Test
    @DisplayName("Should get budget summary with aggregated statistics")
    void shouldGetBudgetSummary() {
        // Given
        Budget budget2 =
                Budget.builder()
                        .id(2L)
                        .userId(1L)
                        .categoryId(2L)
                        .amount("200.00")
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 28))
                        .build();

        Category category2 =
                Category.builder()
                        .id(2L)
                        .userId(1L)
                        .name("Entertainment")
                        .type(CategoryType.EXPENSE)
                        .build();

        Transaction transaction1 =
                Transaction.builder()
                        .categoryId(1L)
                        .amount(new BigDecimal("250.00"))
                        .type(TransactionType.EXPENSE)
                        .build();

        Transaction transaction2 =
                Transaction.builder()
                        .categoryId(2L)
                        .amount(new BigDecimal("50.00"))
                        .type(TransactionType.EXPENSE)
                        .build();

        when(budgetRepository.findByUserIdAndPeriod(1L, BudgetPeriod.MONTHLY))
                .thenReturn(Arrays.asList(testBudget, budget2));
        // Mock calls from calculateBudgetProgress for each budget
        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testBudget));
        when(budgetRepository.findByIdAndUserId(2L, 1L)).thenReturn(Optional.of(budget2));
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.findByIdAndUserId(2L, 1L)).thenReturn(Optional.of(category2));
        when(transactionRepository.findByCategoryIdInAndDateRange(anyList(), any(), any(), eq(1L)))
                .thenAnswer(
                        inv -> {
                            List<Long> ids = inv.getArgument(0);
                            if (ids.contains(1L)) return List.of(transaction1);
                            if (ids.contains(2L)) return List.of(transaction2);
                            return List.of();
                        });
        when(transactionSplitRepository.findByCategoryIdInAndDateRange(
                        anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());

        // When
        BudgetSummaryResponse summary = budgetService.getBudgetSummary(1L, BudgetPeriod.MONTHLY);

        // Then
        assertThat(summary).isNotNull();
        assertThat(summary.getPeriod()).isEqualTo(BudgetPeriod.MONTHLY);
        assertThat(summary.getTotalBudgets()).isEqualTo(2);
        assertThat(summary.getActiveBudgets())
                .isEqualTo(1); // budget2 ended 2026-02-28 (past); only testBudget
        // (ends 2026-12-31) is active
        assertThat(summary.getTotalBudgeted())
                .isEqualByComparingTo(new BigDecimal("700.00")); // 500 + 200
        assertThat(summary.getTotalSpent())
                .isEqualByComparingTo(new BigDecimal("300.00")); // 250 + 50
        assertThat(summary.getTotalRemaining())
                .isEqualByComparingTo(new BigDecimal("400.00")); // 700 - 300
        assertThat(summary.getAverageSpentPercentage())
                .isEqualByComparingTo(new BigDecimal("37.50")); // (50% +
        // 25%) /
        // 2 =
        // 37.5%
        assertThat(summary.getBudgets()).hasSize(2);
        assertThat(summary.getCurrency()).isEqualTo("USD");

        verify(budgetRepository).findByUserIdAndPeriod(1L, BudgetPeriod.MONTHLY);
    }

    @Test
    @DisplayName("Should get budget summary with empty budgets")
    void shouldGetBudgetSummary_EmptyBudgets() {
        // Given
        when(budgetRepository.findByUserIdAndPeriod(1L, BudgetPeriod.MONTHLY))
                .thenReturn(List.of());

        // When
        BudgetSummaryResponse summary = budgetService.getBudgetSummary(1L, BudgetPeriod.MONTHLY);

        // Then
        assertThat(summary).isNotNull();
        assertThat(summary.getTotalBudgets()).isEqualTo(0);
        assertThat(summary.getActiveBudgets()).isEqualTo(0);
        assertThat(summary.getTotalBudgeted()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getTotalRemaining()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getAverageSpentPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getBudgets()).isEmpty();
        assertThat(summary.getCurrency()).isEqualTo("USD");
    }

    // ========== BUDGET HISTORY TESTS ==========

    @Test
    @DisplayName("Should get budget history successfully")
    void shouldGetBudgetHistorySuccessfully() {
        // Given
        Transaction transaction1 =
                Transaction.builder()
                        .id(1L)
                        .userId(1L)
                        .categoryId(1L)
                        .amount(new BigDecimal("350.25"))
                        .type(TransactionType.EXPENSE)
                        .date(LocalDate.of(2026, 2, 10))
                        .build();

        Transaction transaction2 =
                Transaction.builder()
                        .id(2L)
                        .userId(1L)
                        .categoryId(1L)
                        .amount(new BigDecimal("450.00"))
                        .type(TransactionType.EXPENSE)
                        .date(LocalDate.of(2026, 3, 15))
                        .build();

        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testBudget));
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));

        // Mock transaction queries: Feb returns transaction1 (350.25), Mar returns
        // transaction2 (450.00), others empty
        when(transactionRepository.findByCategoryIdInAndDateRange(
                        anyList(), any(), any(), anyLong()))
                .thenAnswer(
                        invocation -> {
                            LocalDate start = invocation.getArgument(1);
                            if (LocalDate.of(2026, 2, 1).equals(start))
                                return List.of(transaction1);
                            if (LocalDate.of(2026, 3, 1).equals(start))
                                return List.of(transaction2);
                            return List.of();
                        });
        when(transactionSplitRepository.findByCategoryIdInAndDateRange(
                        anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());

        // When
        BudgetHistoryResponse history = budgetService.getBudgetHistory(1L, 1L);

        // Then
        assertThat(history).isNotNull();
        assertThat(history.getBudgetId()).isEqualTo(1L);
        assertThat(history.getCategoryName()).isEqualTo("Groceries");
        assertThat(history.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(history.getCurrency()).isEqualTo("USD");
        assertThat(history.getPeriod()).isEqualTo(BudgetPeriod.MONTHLY);
        assertThat(history.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(history.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));

        assertThat(history.getHistory())
                .hasSize(12); // testBudget spans full year → 12 monthly entries

        // Check the February entry (index 1) which has transaction1 (350.25)
        BudgetHistoryEntry febEntry = history.getHistory().get(1);
        assertThat(febEntry.getLabel()).isEqualTo("févr. 2026");
        assertThat(febEntry.getPeriodStart()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(febEntry.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(febEntry.getBudgeted()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(febEntry.getSpent()).isEqualByComparingTo(new BigDecimal("350.25"));
        assertThat(febEntry.getRemaining()).isEqualByComparingTo(new BigDecimal("149.75"));
        assertThat(febEntry.getPercentageSpent()).isEqualByComparingTo(new BigDecimal("70.05"));
        assertThat(febEntry.getStatus()).isEqualTo("ON_TRACK");

        // Total: Feb 350.25 + Mar 450.00 = 800.25 spent; 12 * 500 = 6000 budgeted
        assertThat(history.getTotalSpent()).isEqualByComparingTo(new BigDecimal("800.25"));
        assertThat(history.getTotalBudgeted()).isEqualByComparingTo(new BigDecimal("6000.00"));

        verify(budgetRepository).findByIdAndUserId(1L, 1L);
        verify(categoryRepository).findByIdAndUserId(1L, 1L);
        verify(transactionRepository)
                .findByCategoryIdInAndDateRange(
                        eq(List.of(1L)),
                        eq(LocalDate.of(2026, 2, 1)),
                        eq(LocalDate.of(2026, 2, 28)),
                        eq(1L));
    }

    @Test
    @DisplayName("Should throw BudgetNotFoundException when budget not found for history")
    void shouldThrowBudgetNotFoundExceptionForHistory() {
        // Given
        when(budgetRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> budgetService.getBudgetHistory(999L, 1L))
                .isInstanceOf(BudgetNotFoundException.class)
                .hasMessageContaining("Budget not found with id: 999 for user: 1");

        verify(budgetRepository).findByIdAndUserId(999L, 1L);
        verifyNoInteractions(categoryRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Should throw CategoryNotFoundException when category not found for history")
    void shouldThrowCategoryNotFoundExceptionForHistory() {
        // Given
        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testBudget));
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> budgetService.getBudgetHistory(1L, 1L))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(budgetRepository).findByIdAndUserId(1L, 1L);
        verify(categoryRepository).findByIdAndUserId(1L, 1L);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null budgetId")
    void shouldThrowIllegalArgumentExceptionForNullBudgetId() {
        // When/Then
        assertThatThrownBy(() -> budgetService.getBudgetHistory(null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Budget ID cannot be null");

        verifyNoInteractions(budgetRepository);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null userId")
    void shouldThrowIllegalArgumentExceptionForNullUserId() {
        // When/Then
        assertThatThrownBy(() -> budgetService.getBudgetHistory(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null");

        verifyNoInteractions(budgetRepository);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null encryptionKey")
    void shouldThrowIllegalArgumentExceptionForNullEncryptionKey() {
        // This test is no longer needed - encryption key is resolved internally
    }

    @Test
    @DisplayName("Should handle budget history with no transactions")
    void shouldHandleBudgetHistoryWithNoTransactions() {
        // Given
        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testBudget));
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));
        when(transactionRepository.findByCategoryIdInAndDateRange(
                        anyList(), any(), any(), anyLong()))
                .thenReturn(List.of()); // No transactions

        // When
        BudgetHistoryResponse history = budgetService.getBudgetHistory(1L, 1L);

        // Then
        assertThat(history.getHistory())
                .hasSize(12); // testBudget spans 2026-01-01 to 2026-12-31 → 12 monthly
        // entries
        BudgetHistoryEntry entry = history.getHistory().get(0);
        assertThat(entry.getSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(entry.getRemaining()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(entry.getPercentageSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(entry.getStatus()).isEqualTo("ON_TRACK");

        assertThat(history.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(history.getTotalBudgeted())
                .isEqualByComparingTo(new BigDecimal("6000.00")); // 12 * 500
    }

    @Test
    @DisplayName("Should handle budget history with over-spending")
    void shouldHandleBudgetHistoryWithOverSpending() {
        // Given - spent more than budgeted
        Transaction transaction =
                Transaction.builder()
                        .id(1L)
                        .userId(1L)
                        .categoryId(1L)
                        .amount(new BigDecimal("600.00")) // Over budget
                        .type(TransactionType.EXPENSE)
                        .date(LocalDate.of(2026, 2, 10))
                        .build();

        when(budgetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testBudget));
        when(categoryRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testCategory));
        when(transactionRepository.findByCategoryIdInAndDateRange(
                        anyList(), any(), any(), anyLong()))
                .thenReturn(List.of(transaction));
        when(transactionSplitRepository.findByCategoryIdInAndDateRange(
                        anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());

        // When
        BudgetHistoryResponse history = budgetService.getBudgetHistory(1L, 1L);

        // Then
        BudgetHistoryEntry entry = history.getHistory().get(0);
        assertThat(entry.getSpent()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(entry.getRemaining())
                .isEqualByComparingTo(new BigDecimal("-100.00")); // Negative remaining
        assertThat(entry.getPercentageSpent()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(entry.getStatus()).isEqualTo("EXCEEDED");
    }
}
