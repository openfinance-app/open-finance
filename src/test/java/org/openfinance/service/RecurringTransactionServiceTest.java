package org.openfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.RecurringTransactionRequest;
import org.openfinance.dto.RecurringTransactionResponse;
import org.openfinance.entity.*;
import org.openfinance.exception.AccountNotFoundException;
import org.openfinance.exception.CategoryNotFoundException;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.exception.RecurringTransactionNotFoundException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.RecurringTransactionRepository;
import org.openfinance.security.EncryptionService;

/**
 * Unit tests for RecurringTransactionService.
 *
 * <p>
 * Tests cover:
 *
 * <ul>
 * <li>CRUD operations (create, read, update, delete)
 * <li>CRITICAL: processRecurringTransactions() - batch processing with error
 * handling
 * <li>Pause/resume recurring transactions
 * <li>Validation and authorization
 * <li>Encryption/decryption of description and notes
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RecurringTransactionService Unit Tests")
class RecurringTransactionServiceTest {

        @Mock
        private RecurringTransactionRepository recurringTransactionRepository;

        @Mock
        private AccountRepository accountRepository;

        @Mock
        private CategoryRepository categoryRepository;

        @Mock
        private EncryptionService encryptionService;

        @Mock
        private TransactionService transactionService;

        @Mock
        private OperationHistoryService operationHistoryService;

        @Mock
        private CurrencyRepository currencyRepository;

        @Mock
        private SearchTokenService searchTokenService;

        @InjectMocks
        private RecurringTransactionService recurringTransactionService;

        private User testUser;
        private Account testAccount;
        private Account testToAccount;
        private Category testExpenseCategory;
        private Category testIncomeCategory;
        private RecurringTransaction testRecurringTransaction;
        private RecurringTransactionRequest testRequest;

        @BeforeEach
        void setUp() {
                // Create test user
                testUser = User.builder().id(1L).username("testuser").email("test@example.com").build();

                // Create test accounts
                testAccount = Account.builder()
                                .id(100L)
                                .userId(1L)
                                .name("Checking")
                                .type(AccountType.CHECKING)
                                .currency("USD")
                                .build();

                testToAccount = Account.builder()
                                .id(200L)
                                .userId(1L)
                                .name("Savings")
                                .type(AccountType.SAVINGS)
                                .currency("USD")
                                .build();

                // Create test categories
                testExpenseCategory = Category.builder()
                                .id(50L)
                                .userId(1L)
                                .name("Rent")
                                .type(CategoryType.EXPENSE)
                                .icon("home")
                                .color("#FF5733")
                                .isSystem(false)
                                .build();

                testIncomeCategory = Category.builder()
                                .id(60L)
                                .userId(1L)
                                .name("Salary")
                                .type(CategoryType.INCOME)
                                .icon("dollar-sign")
                                .color("#28A745")
                                .isSystem(false)
                                .build();

                // Create test recurring transaction request
                testRequest = RecurringTransactionRequest.builder()
                                .accountId(100L)
                                .type(TransactionType.EXPENSE)
                                .amount(new BigDecimal("1500.00"))
                                .currency("USD")
                                .categoryId(50L)
                                .description("Monthly rent payment")
                                .notes("Due on the 1st")
                                .frequency(RecurringFrequency.MONTHLY)
                                .nextOccurrence(LocalDate.of(2026, 3, 1))
                                .endDate(LocalDate.of(2026, 12, 31))
                                .build();

                // Create test recurring transaction entity
                testRecurringTransaction = RecurringTransaction.builder()
                                .id(1L)
                                .userId(1L)
                                .accountId(100L)
                                .type(TransactionType.EXPENSE)
                                .amount(new BigDecimal("1500.00"))
                                .currency("USD")
                                .categoryId(50L)
                                .description("Monthly rent payment")
                                .notes("Due on the 1st")
                                .frequency(RecurringFrequency.MONTHLY)
                                .nextOccurrence(LocalDate.of(2026, 3, 1))
                                .endDate(LocalDate.of(2026, 12, 31))
                                .isActive(true)
                                .build();
        }

        // ========== CREATE RECURRING TRANSACTION TESTS ==========

        @Nested
        @DisplayName("Create Recurring Transaction")
        class CreateRecurringTransactionTests {

                @Test
                @DisplayName("Should create recurring transaction successfully")
                void shouldCreateRecurringTransaction() {
                        // Given
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));
                        when(categoryRepository.findByIdAndUserId(50L, 1L))
                                        .thenReturn(Optional.of(testExpenseCategory));
                        when(recurringTransactionRepository.save(any(RecurringTransaction.class)))
                                        .thenReturn(testRecurringTransaction);

                        // When
                        RecurringTransactionResponse response = recurringTransactionService.createRecurringTransaction(
                                        1L, testRequest);

                        // Then
                        assertThat(response).isNotNull();
                        assertThat(response.getId()).isEqualTo(1L);
                        assertThat(response.getDescription()).isEqualTo("Monthly rent payment");
                        assertThat(response.getNotes()).isEqualTo("Due on the 1st");
                        assertThat(response.getFrequency()).isEqualTo(RecurringFrequency.MONTHLY);
                        assertThat(response.getIsActive()).isTrue();

                        verify(accountRepository, atLeastOnce()).findByIdAndUserId(100L, 1L);
                        verify(categoryRepository, atLeastOnce()).findByIdAndUserId(50L, 1L);
                        verify(recurringTransactionRepository).save(any(RecurringTransaction.class));
                }

                @Test
                @DisplayName("Should throw AccountNotFoundException when account not found")
                void shouldThrowAccountNotFoundWhenAccountNotFound() {
                        // Given
                        when(accountRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.empty());

                        // When/Then
                        assertThatThrownBy(
                                        () -> recurringTransactionService.createRecurringTransaction(
                                                        1L, testRequest))
                                        .isInstanceOf(AccountNotFoundException.class);

                        verify(accountRepository).findByIdAndUserId(100L, 1L);
                        verify(recurringTransactionRepository, never()).save(any());
                }

                @Test
                @DisplayName("Should throw CategoryNotFoundException when category not found")
                void shouldThrowCategoryNotFoundWhenCategoryNotFound() {
                        // Given
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));
                        when(categoryRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.empty());

                        // When/Then
                        assertThatThrownBy(
                                        () -> recurringTransactionService.createRecurringTransaction(
                                                        1L, testRequest))
                                        .isInstanceOf(CategoryNotFoundException.class);

                        verify(categoryRepository).findByIdAndUserId(50L, 1L);
                        verify(recurringTransactionRepository, never()).save(any());
                }

                @Test
                @DisplayName("Should throw InvalidTransactionException when category type mismatch")
                void shouldThrowExceptionWhenCategoryTypeMismatch() {
                        // Given - EXPENSE transaction with INCOME category
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));
                        when(categoryRepository.findByIdAndUserId(50L, 1L))
                                        .thenReturn(Optional.of(testIncomeCategory));

                        // When/Then
                        assertThatThrownBy(
                                        () -> recurringTransactionService.createRecurringTransaction(
                                                        1L, testRequest))
                                        .isInstanceOf(InvalidTransactionException.class)
                                        .hasMessageContaining("EXPENSE transactions must use an EXPENSE category");

                        verify(recurringTransactionRepository, never()).save(any());
                }

                @Test
                @DisplayName("Should throw InvalidTransactionException when TRANSFER missing toAccountId")
                void shouldThrowExceptionWhenTransferMissingDestination() {
                        // Given
                        RecurringTransactionRequest transferRequest = RecurringTransactionRequest.builder()
                                        .accountId(100L)
                                        .type(TransactionType.TRANSFER)
                                        .amount(new BigDecimal("500.00"))
                                        .currency("USD")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.of(2026, 3, 1))
                                        .build();

                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));

                        // When/Then
                        assertThatThrownBy(
                                        () -> recurringTransactionService.createRecurringTransaction(
                                                        1L, transferRequest))
                                        .isInstanceOf(InvalidTransactionException.class)
                                        .hasMessageContaining("TRANSFER transactions must have a destination account");

                        verify(recurringTransactionRepository, never()).save(any());
                }

                @Test
                @DisplayName("Should throw InvalidTransactionException when TRANSFER has same source and destination")
                void shouldThrowExceptionWhenTransferSameAccounts() {
                        // Given
                        RecurringTransactionRequest transferRequest = RecurringTransactionRequest.builder()
                                        .accountId(100L)
                                        .toAccountId(100L) // Same as accountId
                                        .type(TransactionType.TRANSFER)
                                        .amount(new BigDecimal("500.00"))
                                        .currency("USD")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.of(2026, 3, 1))
                                        .build();

                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));

                        // When/Then
                        assertThatThrownBy(
                                        () -> recurringTransactionService.createRecurringTransaction(
                                                        1L, transferRequest))
                                        .isInstanceOf(InvalidTransactionException.class)
                                        .hasMessageContaining("Source and destination accounts must be different");

                        verify(recurringTransactionRepository, never()).save(any());
                }

                @Test
                @DisplayName("Should throw InvalidTransactionException when TRANSFER has category")
                void shouldThrowExceptionWhenTransferHasCategory() {
                        // Given
                        RecurringTransactionRequest transferRequest = RecurringTransactionRequest.builder()
                                        .accountId(100L)
                                        .toAccountId(200L)
                                        .type(TransactionType.TRANSFER)
                                        .amount(new BigDecimal("500.00"))
                                        .currency("USD")
                                        .categoryId(50L) // Should not have category
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.of(2026, 3, 1))
                                        .build();

                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));

                        // When/Then
                        assertThatThrownBy(
                                        () -> recurringTransactionService.createRecurringTransaction(
                                                        1L, transferRequest))
                                        .isInstanceOf(InvalidTransactionException.class)
                                        .hasMessageContaining("TRANSFER transactions should not have a category");

                        verify(recurringTransactionRepository, never()).save(any());
                }

                @Test
                @DisplayName("Should throw InvalidTransactionException when endDate before nextOccurrence")
                void shouldThrowExceptionWhenEndDateBeforeNextOccurrence() {
                        // Given
                        RecurringTransactionRequest invalidRequest = RecurringTransactionRequest.builder()
                                        .accountId(100L)
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("1500.00"))
                                        .currency("USD")
                                        .categoryId(50L)
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.of(2026, 12, 1))
                                        .endDate(LocalDate.of(2026, 11, 1)) // Before nextOccurrence
                                        .build();

                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));
                        when(categoryRepository.findByIdAndUserId(50L, 1L))
                                        .thenReturn(Optional.of(testExpenseCategory));

                        // When/Then
                        assertThatThrownBy(
                                        () -> recurringTransactionService.createRecurringTransaction(
                                                        1L, invalidRequest))
                                        .isInstanceOf(InvalidTransactionException.class)
                                        .hasMessageContaining("End date must be after next occurrence date");

                        verify(recurringTransactionRepository, never()).save(any());
                }

                @Test
                @DisplayName("Should create TRANSFER recurring transaction successfully")
                void shouldCreateTransferRecurringTransaction() {
                        // Given
                        RecurringTransactionRequest transferRequest = RecurringTransactionRequest.builder()
                                        .accountId(100L)
                                        .toAccountId(200L)
                                        .type(TransactionType.TRANSFER)
                                        .amount(new BigDecimal("500.00"))
                                        .currency("USD")
                                        .description("Monthly savings transfer")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.of(2026, 3, 1))
                                        .build();

                        RecurringTransaction savedTransfer = RecurringTransaction.builder()
                                        .id(2L)
                                        .userId(1L)
                                        .accountId(100L)
                                        .toAccountId(200L)
                                        .type(TransactionType.TRANSFER)
                                        .amount(new BigDecimal("500.00"))
                                        .currency("USD")
                                        .description("Monthly savings transfer")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.of(2026, 3, 1))
                                        .isActive(true)
                                        .build();

                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));
                        when(accountRepository.findByIdAndUserId(200L, 1L))
                                        .thenReturn(Optional.of(testToAccount));
                        when(recurringTransactionRepository.save(any(RecurringTransaction.class)))
                                        .thenReturn(savedTransfer);

                        // When
                        RecurringTransactionResponse response = recurringTransactionService.createRecurringTransaction(
                                        1L, transferRequest);

                        // Then
                        assertThat(response).isNotNull();
                        assertThat(response.getId()).isEqualTo(2L);
                        assertThat(response.getType()).isEqualTo(TransactionType.TRANSFER);
                        assertThat(response.getToAccountId()).isEqualTo(200L);
                        assertThat(response.getCategoryId()).isNull();

                        verify(accountRepository, atLeastOnce()).findByIdAndUserId(100L, 1L);
                        verify(accountRepository, atLeastOnce()).findByIdAndUserId(200L, 1L);
                        verify(recurringTransactionRepository).save(any(RecurringTransaction.class));
                }

                @Test
                @DisplayName("BUG-REC-001: Should successfully create recurring transaction with long description (>16 chars)")
                void shouldCreateRecurringTransactionWithLongDescription() {
                        // Given - Test descriptions of various lengths to verify encryption works
                        // correctly
                        String shortDescription = "Test"; // 4 chars
                        String mediumDescription = "Monthly Bill"; // 12 chars
                        String longDescription = "Monthly Rent Payment"; // 20 chars - previously failed
                        String veryLongDescription = "This is a very long recurring transaction description that should still work correctly with encryption"; // 104
                        // chars

                        // Test with 20-character description (the bug threshold)
                        RecurringTransactionRequest longDescRequest = RecurringTransactionRequest.builder()
                                        .accountId(100L)
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("1500.00"))
                                        .currency("USD")
                                        .categoryId(50L)
                                        .description(longDescription)
                                        .notes("Additional notes for this recurring payment")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.of(2026, 3, 1))
                                        .build();

                        RecurringTransaction savedRecurring = RecurringTransaction.builder()
                                        .id(3L)
                                        .userId(1L)
                                        .accountId(100L)
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("1500.00"))
                                        .currency("USD")
                                        .categoryId(50L)
                                        .description(longDescription)
                                        .notes("Additional notes for this recurring payment")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.of(2026, 3, 1))
                                        .isActive(true)
                                        .build();

                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));
                        when(categoryRepository.findByIdAndUserId(50L, 1L))
                                        .thenReturn(Optional.of(testExpenseCategory));
                        when(recurringTransactionRepository.save(any(RecurringTransaction.class)))
                                        .thenReturn(savedRecurring);

                        // When
                        RecurringTransactionResponse response = recurringTransactionService.createRecurringTransaction(
                                        1L, longDescRequest);

                        // Then
                        assertThat(response).isNotNull();
                        assertThat(response.getId()).isEqualTo(3L);
                        assertThat(response.getDescription()).isEqualTo(longDescription);
                        assertThat(response.getDescription().length()).isEqualTo(20); // Verify length
                        assertThat(response.getNotes())
                                        .isEqualTo("Additional notes for this recurring payment");
                        assertThat(response.getFrequency()).isEqualTo(RecurringFrequency.MONTHLY);
                        assertThat(response.getIsActive()).isTrue();

                        verify(recurringTransactionRepository).save(any(RecurringTransaction.class));

                        // The service now passes plain text to the entity and relies on the JPA
                        // converter to encrypt at persistence time.
                        ArgumentCaptor<RecurringTransaction> captor = ArgumentCaptor
                                        .forClass(RecurringTransaction.class);
                        verify(recurringTransactionRepository).save(captor.capture());
                        RecurringTransaction savedEntity = captor.getValue();
                        assertThat(savedEntity.getDescription()).isEqualTo(longDescription);
                }
        }

        // ========== UPDATE RECURRING TRANSACTION TESTS ==========

        @Nested
        @DisplayName("Update Recurring Transaction")
        class UpdateRecurringTransactionTests {

                @Test
                @DisplayName("Should update recurring transaction successfully")
                void shouldUpdateRecurringTransaction() {
                        // Given
                        RecurringTransactionRequest updateRequest = RecurringTransactionRequest.builder()
                                        .accountId(100L)
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("1600.00")) // Updated amount
                                        .currency("USD")
                                        .categoryId(50L)
                                        .description("Updated rent payment")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.of(2026, 3, 1))
                                        .endDate(LocalDate.of(2026, 12, 31))
                                        .build();

                        RecurringTransaction updatedEntity = RecurringTransaction.builder()
                                        .id(1L)
                                        .userId(1L)
                                        .accountId(100L)
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("1600.00"))
                                        .currency("USD")
                                        .categoryId(50L)
                                        .description("Updated rent payment")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.of(2026, 3, 1))
                                        .endDate(LocalDate.of(2026, 12, 31))
                                        .isActive(true)
                                        .build();

                        when(recurringTransactionRepository.findByIdAndUserId(1L, 1L))
                                        .thenReturn(Optional.of(testRecurringTransaction));
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));
                        when(categoryRepository.findByIdAndUserId(50L, 1L))
                                        .thenReturn(Optional.of(testExpenseCategory));
                        when(recurringTransactionRepository.save(any(RecurringTransaction.class)))
                                        .thenReturn(updatedEntity);

                        // When
                        RecurringTransactionResponse response = recurringTransactionService.updateRecurringTransaction(
                                        1L, 1L, updateRequest);

                        // Then
                        assertThat(response).isNotNull();
                        assertThat(response.getId()).isEqualTo(1L);
                        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("1600.00"));
                        assertThat(response.getDescription()).isEqualTo("Updated rent payment");

                        verify(recurringTransactionRepository).findByIdAndUserId(1L, 1L);
                        verify(recurringTransactionRepository).save(any(RecurringTransaction.class));
                }

                @Test
                @DisplayName("Should throw RecurringTransactionNotFoundException when not found")
                void shouldThrowNotFoundExceptionWhenUpdatingNonExistent() {
                        // Given
                        when(recurringTransactionRepository.findByIdAndUserId(999L, 1L))
                                        .thenReturn(Optional.empty());

                        // When/Then
                        assertThatThrownBy(
                                        () -> recurringTransactionService.updateRecurringTransaction(
                                                        999L, 1L, testRequest))
                                        .isInstanceOf(RecurringTransactionNotFoundException.class);

                        verify(recurringTransactionRepository).findByIdAndUserId(999L, 1L);
                        verify(recurringTransactionRepository, never()).save(any());
                }

                @Test
                @DisplayName("Should throw RecurringTransactionNotFoundException when updating another user's transaction")
                void shouldThrowNotFoundExceptionWhenUpdatingOtherUsersTransaction() {
                        // Given
                        when(recurringTransactionRepository.findByIdAndUserId(1L, 999L))
                                        .thenReturn(Optional.empty());

                        // When/Then
                        assertThatThrownBy(
                                        () -> recurringTransactionService.updateRecurringTransaction(
                                                        1L, 999L, testRequest))
                                        .isInstanceOf(RecurringTransactionNotFoundException.class);

                        verify(recurringTransactionRepository).findByIdAndUserId(1L, 999L);
                        verify(recurringTransactionRepository, never()).save(any());
                }
        }

        // ========== DELETE RECURRING TRANSACTION TESTS ==========

        @Nested
        @DisplayName("Delete Recurring Transaction")
        class DeleteRecurringTransactionTests {

                @Test
                @DisplayName("Should delete recurring transaction successfully")
                void shouldDeleteRecurringTransaction() {
                        // Given
                        when(recurringTransactionRepository.existsByIdAndUserId(1L, 1L)).thenReturn(true);

                        // When
                        recurringTransactionService.deleteRecurringTransaction(1L, 1L);

                        // Then
                        verify(recurringTransactionRepository).existsByIdAndUserId(1L, 1L);
                        verify(recurringTransactionRepository).deleteByIdAndUserId(1L, 1L);
                        verify(searchTokenService).removeEntity("RECURRING_TRANSACTION", 1L);
                }

                @Test
                @DisplayName("Should throw RecurringTransactionNotFoundException when not found")
                void shouldThrowNotFoundExceptionWhenDeletingNonExistent() {
                        // Given
                        when(recurringTransactionRepository.existsByIdAndUserId(999L, 1L)).thenReturn(false);

                        // When/Then
                        assertThatThrownBy(
                                        () -> recurringTransactionService.deleteRecurringTransaction(999L, 1L))
                                        .isInstanceOf(RecurringTransactionNotFoundException.class);

                        verify(recurringTransactionRepository).existsByIdAndUserId(999L, 1L);
                        verify(recurringTransactionRepository, never())
                                        .deleteByIdAndUserId(anyLong(), anyLong());
                }

                @Test
                @DisplayName("Should throw RecurringTransactionNotFoundException when deleting another user's transaction")
                void shouldThrowNotFoundExceptionWhenDeletingOtherUsersTransaction() {
                        // Given
                        when(recurringTransactionRepository.existsByIdAndUserId(1L, 999L)).thenReturn(false);

                        // When/Then
                        assertThatThrownBy(
                                        () -> recurringTransactionService.deleteRecurringTransaction(1L, 999L))
                                        .isInstanceOf(RecurringTransactionNotFoundException.class);

                        verify(recurringTransactionRepository).existsByIdAndUserId(1L, 999L);
                        verify(recurringTransactionRepository, never())
                                        .deleteByIdAndUserId(anyLong(), anyLong());
                }
        }

        // ========== GET RECURRING TRANSACTION TESTS ==========

        @Nested
        @DisplayName("Get Recurring Transaction")
        class GetRecurringTransactionTests {

                @Test
                @DisplayName("Should get recurring transaction by ID successfully")
                void shouldGetRecurringTransactionById() {
                        // Given
                        when(recurringTransactionRepository.findByIdAndUserId(1L, 1L))
                                        .thenReturn(Optional.of(testRecurringTransaction));
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));

                        // When
                        RecurringTransactionResponse response = recurringTransactionService.getRecurringTransactionById(
                                        1L, 1L);

                        // Then
                        assertThat(response).isNotNull();
                        assertThat(response.getId()).isEqualTo(1L);
                        assertThat(response.getDescription()).isEqualTo("Monthly rent payment");
                        assertThat(response.getNotes()).isEqualTo("Due on the 1st");

                        verify(recurringTransactionRepository).findByIdAndUserId(1L, 1L);
                }

                @Test
                @DisplayName("Should throw RecurringTransactionNotFoundException when not found")
                void shouldThrowNotFoundExceptionWhenGettingNonExistent() {
                        // Given
                        when(recurringTransactionRepository.findByIdAndUserId(999L, 1L))
                                        .thenReturn(Optional.empty());

                        // When/Then
                        assertThatThrownBy(
                                        () -> recurringTransactionService.getRecurringTransactionById(
                                                        999L, 1L))
                                        .isInstanceOf(RecurringTransactionNotFoundException.class);

                        verify(recurringTransactionRepository).findByIdAndUserId(999L, 1L);
                }

                @Test
                @DisplayName("Should get all recurring transactions for user")
                void shouldGetAllRecurringTransactions() {
                        // Given
                        RecurringTransaction rt2 = RecurringTransaction.builder()
                                        .id(2L)
                                        .userId(1L)
                                        .accountId(100L)
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("50.00"))
                                        .currency("USD")
                                        .categoryId(50L)
                                        .description("Netflix subscription")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.of(2026, 3, 15))
                                        .isActive(true)
                                        .build();

                        when(recurringTransactionRepository.findByUserId(1L))
                                        .thenReturn(Arrays.asList(testRecurringTransaction, rt2));
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));

                        // When
                        List<RecurringTransactionResponse> responses = recurringTransactionService
                                        .getAllRecurringTransactions(1L);

                        // Then
                        assertThat(responses).hasSize(2);
                        assertThat(responses.get(0).getId()).isEqualTo(1L);
                        assertThat(responses.get(1).getId()).isEqualTo(2L);

                        verify(recurringTransactionRepository).findByUserId(1L);
                }

                @Test
                @DisplayName("Should get only active recurring transactions")
                void shouldGetActiveRecurringTransactions() {
                        // Given
                        when(recurringTransactionRepository.findByUserIdAndIsActive(1L))
                                        .thenReturn(Collections.singletonList(testRecurringTransaction));
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));

                        // When
                        List<RecurringTransactionResponse> responses = recurringTransactionService
                                        .getActiveRecurringTransactions(
                                                        1L);

                        // Then
                        assertThat(responses).hasSize(1);
                        assertThat(responses.get(0).getId()).isEqualTo(1L);
                        assertThat(responses.get(0).getIsActive()).isTrue();

                        verify(recurringTransactionRepository).findByUserIdAndIsActive(1L);
                }

                @Test
                @DisplayName("Should get due recurring transactions")
                void shouldGetDueRecurringTransactions() {
                        // Given
                        LocalDate today = LocalDate.now();
                        when(recurringTransactionRepository.findDueRecurringTransactionsByUserId(
                                        eq(1L), any(LocalDate.class)))
                                        .thenReturn(Collections.singletonList(testRecurringTransaction));
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));

                        // When
                        List<RecurringTransactionResponse> responses = recurringTransactionService
                                        .getDueRecurringTransactions(1L);

                        // Then
                        assertThat(responses).hasSize(1);
                        assertThat(responses.get(0).getId()).isEqualTo(1L);

                        verify(recurringTransactionRepository)
                                        .findDueRecurringTransactionsByUserId(eq(1L), any(LocalDate.class));
                }

                @Test
                @DisplayName("Should get recurring transactions by frequency")
                void shouldGetRecurringTransactionsByFrequency() {
                        // Given
                        when(recurringTransactionRepository.findByUserIdAndFrequency(
                                        1L, RecurringFrequency.MONTHLY))
                                        .thenReturn(Collections.singletonList(testRecurringTransaction));
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));

                        // When
                        List<RecurringTransactionResponse> responses = recurringTransactionService
                                        .getRecurringTransactionsByFrequency(
                                                        1L, RecurringFrequency.MONTHLY);

                        // Then
                        assertThat(responses).hasSize(1);
                        assertThat(responses.get(0).getFrequency()).isEqualTo(RecurringFrequency.MONTHLY);

                        verify(recurringTransactionRepository)
                                        .findByUserIdAndFrequency(1L, RecurringFrequency.MONTHLY);
                }
        }

        // ========== PAUSE/RESUME TESTS ==========

        @Nested
        @DisplayName("Pause and Resume Recurring Transaction")
        class PauseResumeTests {

                @Test
                @DisplayName("Should pause recurring transaction successfully")
                void shouldPauseRecurringTransaction() {
                        // Given
                        when(recurringTransactionRepository.findByIdAndUserId(1L, 1L))
                                        .thenReturn(Optional.of(testRecurringTransaction));
                        when(recurringTransactionRepository.save(any(RecurringTransaction.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));

                        // When
                        RecurringTransactionResponse response = recurringTransactionService.pauseRecurringTransaction(
                                        1L, 1L);

                        // Then
                        assertThat(response).isNotNull();
                        assertThat(response.getIsActive()).isFalse();

                        ArgumentCaptor<RecurringTransaction> captor = ArgumentCaptor
                                        .forClass(RecurringTransaction.class);
                        verify(recurringTransactionRepository).save(captor.capture());
                        assertThat(captor.getValue().getIsActive()).isFalse();
                }

                @Test
                @DisplayName("Should resume recurring transaction successfully")
                void shouldResumeRecurringTransaction() {
                        // Given
                        testRecurringTransaction.setIsActive(false); // Start as inactive
                        testRecurringTransaction.setNextOccurrence(
                                        LocalDate.now().minusDays(10)); // Recent date

                        when(recurringTransactionRepository.findByIdAndUserId(1L, 1L))
                                        .thenReturn(Optional.of(testRecurringTransaction));
                        when(recurringTransactionRepository.save(any(RecurringTransaction.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));

                        // When
                        RecurringTransactionResponse response = recurringTransactionService.resumeRecurringTransaction(
                                        1L, 1L);

                        // Then
                        assertThat(response).isNotNull();
                        assertThat(response.getIsActive()).isTrue();

                        ArgumentCaptor<RecurringTransaction> captor = ArgumentCaptor
                                        .forClass(RecurringTransaction.class);
                        verify(recurringTransactionRepository).save(captor.capture());
                        assertThat(captor.getValue().getIsActive()).isTrue();
                }

                @Test
                @DisplayName("Should adjust next occurrence when resuming old recurring transaction")
                void shouldAdjustNextOccurrenceWhenResumingOldRecurringTransaction() {
                        // Given
                        testRecurringTransaction.setIsActive(false);
                        testRecurringTransaction.setNextOccurrence(
                                        LocalDate.now().minusMonths(2)); // More than 1 month old

                        when(recurringTransactionRepository.findByIdAndUserId(1L, 1L))
                                        .thenReturn(Optional.of(testRecurringTransaction));
                        when(recurringTransactionRepository.save(any(RecurringTransaction.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));

                        // When
                        recurringTransactionService.resumeRecurringTransaction(1L, 1L);

                        // Then
                        ArgumentCaptor<RecurringTransaction> captor = ArgumentCaptor
                                        .forClass(RecurringTransaction.class);
                        verify(recurringTransactionRepository).save(captor.capture());
                        RecurringTransaction saved = captor.getValue();

                        assertThat(saved.getIsActive()).isTrue();
                        assertThat(saved.getNextOccurrence()).isEqualTo(LocalDate.now());
                }
        }

        // ========== PROCESS RECURRING TRANSACTIONS TESTS (CRITICAL) ==========

        @Nested
        @DisplayName("Process Recurring Transactions (CRITICAL)")
        class ProcessRecurringTransactionsTests {

                @Test
                @DisplayName("Should process due recurring transactions successfully")
                void shouldProcessDueRecurringTransactions() {
                        // Given
                        LocalDate today = LocalDate.of(2026, 3, 1);
                        RecurringTransaction rt1 = RecurringTransaction.builder()
                                        .id(1L)
                                        .userId(1L)
                                        .accountId(100L)
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("1500.00"))
                                        .currency("USD")
                                        .categoryId(50L)
                                        .description("Rent")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(today)
                                        .isActive(true)
                                        .build();

                        RecurringTransaction rt2 = RecurringTransaction.builder()
                                        .id(2L)
                                        .userId(2L)
                                        .accountId(101L)
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("50.00"))
                                        .currency("USD")
                                        .categoryId(51L)
                                        .description("Netflix")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(today)
                                        .isActive(true)
                                        .build();

                        when(recurringTransactionRepository.findDueRecurringTransactions(any(LocalDate.class)))
                                        .thenReturn(Arrays.asList(rt1, rt2));
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));
                        when(accountRepository.findByIdAndUserId(101L, 2L))
                                        .thenReturn(Optional.of(testToAccount));
                        when(recurringTransactionRepository.save(any(RecurringTransaction.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));

                        // When
                        RecurringTransactionService.ProcessingResult result = recurringTransactionService
                                        .processRecurringTransactions();

                        // Then
                        assertThat(result).isNotNull();
                        assertThat(result.getProcessedCount()).isEqualTo(2);
                        assertThat(result.getFailedCount()).isEqualTo(0);
                        assertThat(result.getErrors()).isEmpty();

                        verify(recurringTransactionRepository)
                                        .findDueRecurringTransactions(any(LocalDate.class));
                        verify(recurringTransactionRepository, times(2)).save(any(RecurringTransaction.class));
                }

                @Test
                @DisplayName("Should calculate next occurrence correctly")
                void shouldCalculateNextOccurrenceCorrectly() {
                        // Given
                        LocalDate startDate = LocalDate.of(2026, 3, 1);
                        RecurringTransaction rt = RecurringTransaction.builder()
                                        .id(1L)
                                        .userId(1L)
                                        .accountId(100L)
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("1500.00"))
                                        .currency("USD")
                                        .categoryId(50L)
                                        .description("Rent")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(startDate)
                                        .isActive(true)
                                        .build();

                        when(recurringTransactionRepository.findDueRecurringTransactions(any(LocalDate.class)))
                                        .thenReturn(Collections.singletonList(rt));
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));
                        when(recurringTransactionRepository.save(any(RecurringTransaction.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));

                        // When
                        recurringTransactionService.processRecurringTransactions();

                        // Then
                        ArgumentCaptor<RecurringTransaction> captor = ArgumentCaptor
                                        .forClass(RecurringTransaction.class);
                        verify(recurringTransactionRepository).save(captor.capture());

                        RecurringTransaction saved = captor.getValue();
                        assertThat(saved.getNextOccurrence())
                                        .isEqualTo(LocalDate.of(2026, 4, 1)); // One month later
                }

                @Test
                @DisplayName("Should set isActive=false when endDate reached")
                void shouldSetInactiveWhenEndDateReached() {
                        // Given
                        LocalDate today = LocalDate.of(2026, 12, 1);
                        LocalDate endDate = LocalDate.of(2026, 12, 31);

                        RecurringTransaction rt = RecurringTransaction.builder()
                                        .id(1L)
                                        .userId(1L)
                                        .accountId(100L)
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("1500.00"))
                                        .currency("USD")
                                        .categoryId(50L)
                                        .description("Rent")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(today)
                                        .endDate(endDate)
                                        .isActive(true)
                                        .build();

                        when(recurringTransactionRepository.findDueRecurringTransactions(any(LocalDate.class)))
                                        .thenReturn(Collections.singletonList(rt));
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));
                        when(recurringTransactionRepository.save(any(RecurringTransaction.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));

                        // When
                        recurringTransactionService.processRecurringTransactions();

                        // Then
                        ArgumentCaptor<RecurringTransaction> captor = ArgumentCaptor
                                        .forClass(RecurringTransaction.class);
                        verify(recurringTransactionRepository).save(captor.capture());

                        RecurringTransaction saved = captor.getValue();
                        // Next occurrence would be 2027-01-01, which is after endDate 2026-12-31
                        assertThat(saved.getIsActive()).isFalse();
                }

                @Test
                @DisplayName("Should continue processing when one transaction fails")
                void shouldContinueProcessingWhenOneTransactionFails() {
                        // Given
                        LocalDate today = LocalDate.now();

                        RecurringTransaction rt1 = RecurringTransaction.builder()
                                        .id(1L)
                                        .userId(1L)
                                        .accountId(100L)
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("1500.00"))
                                        .currency("USD")
                                        .categoryId(50L)
                                        .description("Rent")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(today)
                                        .isActive(true)
                                        .build();

                        RecurringTransaction rt2 = RecurringTransaction.builder()
                                        .id(2L)
                                        .userId(2L)
                                        .accountId(999L) // Non-existent account - will fail
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("50.00"))
                                        .currency("USD")
                                        .categoryId(51L)
                                        .description("Netflix")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(today)
                                        .isActive(true)
                                        .build();

                        RecurringTransaction rt3 = RecurringTransaction.builder()
                                        .id(3L)
                                        .userId(3L)
                                        .accountId(102L)
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("100.00"))
                                        .currency("USD")
                                        .categoryId(52L)
                                        .description("Internet")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(today)
                                        .isActive(true)
                                        .build();

                        when(recurringTransactionRepository.findDueRecurringTransactions(any(LocalDate.class)))
                                        .thenReturn(Arrays.asList(rt1, rt2, rt3));
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));
                        when(accountRepository.findByIdAndUserId(999L, 2L))
                                        .thenReturn(Optional.empty()); // Fails
                        when(accountRepository.findByIdAndUserId(102L, 3L))
                                        .thenReturn(Optional.of(testToAccount));
                        when(recurringTransactionRepository.save(any(RecurringTransaction.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));

                        // When
                        RecurringTransactionService.ProcessingResult result = recurringTransactionService
                                        .processRecurringTransactions();

                        // Then
                        assertThat(result.getProcessedCount()).isEqualTo(2);
                        assertThat(result.getFailedCount()).isEqualTo(1);
                        assertThat(result.getErrors()).hasSize(1);
                        assertThat(result.getErrors().get(0))
                                        .contains("Failed to process recurring transaction 2");

                        // Verify two successful saves (rt1 and rt3)
                        verify(recurringTransactionRepository, times(2)).save(any(RecurringTransaction.class));
                }

                @Test
                @DisplayName("Should process user-specific recurring transactions")
                void shouldProcessUserSpecificRecurringTransactions() {
                        // Given
                        LocalDate today = LocalDate.now();
                        RecurringTransaction rt = RecurringTransaction.builder()
                                        .id(1L)
                                        .userId(1L)
                                        .accountId(100L)
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("1500.00"))
                                        .currency("USD")
                                        .categoryId(50L)
                                        .description("Rent")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(today)
                                        .isActive(true)
                                        .build();

                        when(recurringTransactionRepository.findDueRecurringTransactionsByUserId(
                                        eq(1L), any(LocalDate.class)))
                                        .thenReturn(Collections.singletonList(rt));
                        when(accountRepository.findByIdAndUserId(100L, 1L))
                                        .thenReturn(Optional.of(testAccount));
                        when(recurringTransactionRepository.save(any(RecurringTransaction.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));

                        // When
                        RecurringTransactionService.ProcessingResult result = recurringTransactionService
                                        .processRecurringTransactionsForUser(1L);

                        // Then
                        assertThat(result.getProcessedCount()).isEqualTo(1);
                        assertThat(result.getFailedCount()).isEqualTo(0);
                        assertThat(result.getErrors()).isEmpty();

                        verify(recurringTransactionRepository)
                                        .findDueRecurringTransactionsByUserId(eq(1L), any(LocalDate.class));
                        verify(recurringTransactionRepository).save(any(RecurringTransaction.class));
                }

                @Test
                @DisplayName("Should return empty result when no due recurring transactions")
                void shouldReturnEmptyResultWhenNoDueRecurringTransactions() {
                        // Given
                        when(recurringTransactionRepository.findDueRecurringTransactions(any(LocalDate.class)))
                                        .thenReturn(Collections.emptyList());

                        // When
                        RecurringTransactionService.ProcessingResult result = recurringTransactionService
                                        .processRecurringTransactions();

                        // Then
                        assertThat(result.getProcessedCount()).isEqualTo(0);
                        assertThat(result.getFailedCount()).isEqualTo(0);
                        assertThat(result.getErrors()).isEmpty();

                        verify(recurringTransactionRepository)
                                        .findDueRecurringTransactions(any(LocalDate.class));
                        verify(recurringTransactionRepository, never()).save(any());
                }
        }
}
