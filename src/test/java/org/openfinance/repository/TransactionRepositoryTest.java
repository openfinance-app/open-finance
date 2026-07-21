package org.openfinance.repository;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for TransactionRepository.
 *
 * <p>Uses @DataJpaTest which configures an in-memory H2 database and provides transactional test
 * execution with rollback.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>CRUD operations
 *   <li>User isolation queries
 *   <li>Date range filtering
 *   <li>Account and category filtering
 *   <li>Transaction type filtering (INCOME, EXPENSE, TRANSFER)
 *   <li>Soft-delete handling
 *   <li>Reconciliation status filtering
 *   <li>Count and existence checks
 * </ul>
 *
 * <p>Requirement REQ-2.4: Transaction management and data access
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
        })
@DisplayName("TransactionRepository Integration Tests")
class TransactionRepositoryTest {

    @Autowired private TransactionRepository transactionRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private AccountRepository accountRepository;

    @Autowired private CategoryRepository categoryRepository;

    private User testUser1;
    private User testUser2;
    private Account checkingAccount;
    private Account savingsAccount;
    private Category salaryCategory;
    private Category groceryCategory;
    private Transaction incomeTransaction;
    private Transaction expenseTransaction;
    private Transaction transferTransaction;

    @BeforeEach
    void setUp() {
        // Clear repositories before each test
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // Create test users
        testUser1 =
                User.builder()
                        .username("testuser1")
                        .email("user1@example.com")
                        .passwordHash("$2a$10$hashedPasswordExample123456789")
                        .masterPasswordSalt("base64EncodedSaltExample==")
                        .baseCurrency("USD")
                        .build();
        testUser1 = userRepository.save(testUser1);

        testUser2 =
                User.builder()
                        .username("testuser2")
                        .email("user2@example.com")
                        .passwordHash("$2a$10$hashedPasswordExample987654321")
                        .masterPasswordSalt("base64EncodedSaltExample22==")
                        .baseCurrency("USD")
                        .build();
        testUser2 = userRepository.save(testUser2);

        // Create test accounts
        checkingAccount =
                Account.builder()
                        .userId(testUser1.getId())
                        .name("Checking Account")
                        .type(AccountType.CHECKING)
                        .currency("USD")
                        .balance(new BigDecimal("5000.00"))
                        .isActive(true)
                        .build();
        checkingAccount = accountRepository.save(checkingAccount);

        savingsAccount =
                Account.builder()
                        .userId(testUser1.getId())
                        .name("Savings Account")
                        .type(AccountType.SAVINGS)
                        .currency("USD")
                        .balance(new BigDecimal("10000.00"))
                        .isActive(true)
                        .build();
        savingsAccount = accountRepository.save(savingsAccount);

        // Create test categories
        salaryCategory =
                Category.builder()
                        .userId(testUser1.getId())
                        .name("Salary")
                        .type(CategoryType.INCOME)
                        .icon("💰")
                        .isSystem(true)
                        .build();
        salaryCategory = categoryRepository.save(salaryCategory);

        groceryCategory =
                Category.builder()
                        .userId(testUser1.getId())
                        .name("Groceries")
                        .type(CategoryType.EXPENSE)
                        .icon("🛒")
                        .isSystem(false)
                        .build();
        groceryCategory = categoryRepository.save(groceryCategory);

        // Create test transactions
        incomeTransaction =
                Transaction.builder()
                        .userId(testUser1.getId())
                        .accountId(checkingAccount.getId())
                        .type(TransactionType.INCOME)
                        .amount(new BigDecimal("3000.00"))
                        .currency("USD")
                        .categoryId(salaryCategory.getId())
                        .date(LocalDate.of(2026, 1, 15))
                        .description("Monthly Salary")
                        .isReconciled(false)
                        .isDeleted(false)
                        .build();

        expenseTransaction =
                Transaction.builder()
                        .userId(testUser1.getId())
                        .accountId(checkingAccount.getId())
                        .type(TransactionType.EXPENSE)
                        .amount(new BigDecimal("150.50"))
                        .currency("USD")
                        .categoryId(groceryCategory.getId())
                        .date(LocalDate.of(2026, 1, 20))
                        .description("Weekly groceries")
                        .isReconciled(false)
                        .isDeleted(false)
                        .build();

        transferTransaction =
                Transaction.builder()
                        .userId(testUser1.getId())
                        .accountId(checkingAccount.getId())
                        .toAccountId(savingsAccount.getId())
                        .type(TransactionType.TRANSFER)
                        .amount(new BigDecimal("500.00"))
                        .currency("USD")
                        .date(LocalDate.of(2026, 1, 25))
                        .description("Transfer to savings")
                        .isReconciled(false)
                        .isDeleted(false)
                        .build();
    }

    // === CRUD Operation Tests ===

    @Test
    @DisplayName("Should save transaction and generate ID")
    void shouldSaveTransactionAndGenerateId() {
        // When
        Transaction savedTransaction = transactionRepository.save(incomeTransaction);

        // Then
        assertThat(savedTransaction).isNotNull();
        assertThat(savedTransaction.getId()).isNotNull();
        assertThat(savedTransaction.getUserId()).isEqualTo(testUser1.getId());
        assertThat(savedTransaction.getAccountId()).isEqualTo(checkingAccount.getId());
        assertThat(savedTransaction.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(savedTransaction.getAmount()).isEqualByComparingTo(new BigDecimal("3000.00"));
        assertThat(savedTransaction.getCurrency()).isEqualTo("USD");
        assertThat(savedTransaction.getCategoryId()).isEqualTo(salaryCategory.getId());
        assertThat(savedTransaction.getDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(savedTransaction.getIsDeleted()).isFalse();
        assertThat(savedTransaction.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should find transaction by ID")
    void shouldFindTransactionById() {
        // Given
        Transaction savedTransaction = transactionRepository.save(incomeTransaction);

        // When
        Optional<Transaction> found = transactionRepository.findById(savedTransaction.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedTransaction.getId());
        assertThat(found.get().getDescription()).isEqualTo("Monthly Salary");
    }

    @Test
    @DisplayName("Should update transaction")
    void shouldUpdateTransaction() {
        // Given
        Transaction savedTransaction = transactionRepository.save(incomeTransaction);

        // When
        savedTransaction.setAmount(new BigDecimal("3500.00"));
        savedTransaction.setDescription("Updated Salary");
        Transaction updatedTransaction = transactionRepository.save(savedTransaction);

        // Then
        assertThat(updatedTransaction.getAmount()).isEqualByComparingTo(new BigDecimal("3500.00"));
        assertThat(updatedTransaction.getDescription()).isEqualTo("Updated Salary");
        assertThat(updatedTransaction.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should soft delete transaction")
    void shouldSoftDeleteTransaction() {
        // Given
        Transaction savedTransaction = transactionRepository.save(incomeTransaction);

        // When
        savedTransaction.setIsDeleted(true);
        transactionRepository.save(savedTransaction);

        // Then - Still exists in database but marked as deleted
        Optional<Transaction> found = transactionRepository.findById(savedTransaction.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getIsDeleted()).isTrue();
    }

    // === User Isolation Tests ===

    @Test
    @DisplayName("Should find all transactions by user ID")
    void shouldFindAllTransactionsByUserId() {
        // Given
        transactionRepository.save(incomeTransaction);
        transactionRepository.save(expenseTransaction);
        transactionRepository.save(transferTransaction);

        // Create transaction for different user
        Account user2Account =
                Account.builder()
                        .userId(testUser2.getId())
                        .name("User2 Account")
                        .type(AccountType.CHECKING)
                        .currency("USD")
                        .balance(BigDecimal.ZERO)
                        .isActive(true)
                        .build();
        user2Account = accountRepository.save(user2Account);

        Transaction user2Transaction =
                Transaction.builder()
                        .userId(testUser2.getId())
                        .accountId(user2Account.getId())
                        .type(TransactionType.INCOME)
                        .amount(new BigDecimal("1000.00"))
                        .currency("USD")
                        .date(LocalDate.of(2026, 1, 10))
                        .isDeleted(false)
                        .build();
        transactionRepository.save(user2Transaction);

        // When
        List<Transaction> user1Transactions = transactionRepository.findByUserId(testUser1.getId());
        List<Transaction> user2Transactions = transactionRepository.findByUserId(testUser2.getId());

        // Then
        assertThat(user1Transactions).hasSize(3);
        assertThat(user2Transactions).hasSize(1);
    }

    @Test
    @DisplayName("Should find transaction by ID and user ID")
    void shouldFindTransactionByIdAndUserId() {
        // Given
        Transaction savedTransaction = transactionRepository.save(incomeTransaction);

        // When
        Optional<Transaction> found =
                transactionRepository.findByIdAndUserId(
                        savedTransaction.getId(), testUser1.getId());
        Optional<Transaction> notFound =
                transactionRepository.findByIdAndUserId(
                        savedTransaction.getId(), testUser2.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getDescription()).isEqualTo("Monthly Salary");
        assertThat(notFound).isEmpty(); // Different user cannot access
    }

    @Test
    @DisplayName("Should not find soft-deleted transaction in user query")
    void shouldNotFindSoftDeletedTransactionInUserQuery() {
        // Given
        incomeTransaction.setIsDeleted(true);
        transactionRepository.save(incomeTransaction);
        transactionRepository.save(expenseTransaction);

        // When
        List<Transaction> transactions = transactionRepository.findByUserId(testUser1.getId());

        // Then
        assertThat(transactions).hasSize(1); // Only non-deleted
        assertThat(transactions.get(0).getDescription()).isEqualTo("Weekly groceries");
    }

    // === Account Filtering Tests ===

    @Test
    @DisplayName("Should find transactions by account ID")
    void shouldFindTransactionsByAccountId() {
        // Given
        transactionRepository.save(incomeTransaction);
        transactionRepository.save(expenseTransaction);
        transactionRepository.save(transferTransaction);

        // When
        List<Transaction> checkingTransactions =
                transactionRepository.findByAccountId(checkingAccount.getId());

        // Then
        assertThat(checkingTransactions).hasSize(3);
        assertThat(checkingTransactions)
                .extracting(Transaction::getDescription)
                .contains("Monthly Salary", "Weekly groceries", "Transfer to savings");
    }

    @Test
    @DisplayName("Should find transactions by user ID and account ID")
    void shouldFindTransactionsByUserIdAndAccountId() {
        // Given
        transactionRepository.save(incomeTransaction);
        transactionRepository.save(expenseTransaction);

        // When
        List<Transaction> transactions =
                transactionRepository.findByUserIdAndAccountId(
                        testUser1.getId(), checkingAccount.getId());

        // Then
        assertThat(transactions).hasSize(2);
    }

    @Test
    @DisplayName("Should find transfer transactions from both accounts")
    void shouldFindTransferTransactionsFromBothAccounts() {
        // Given
        transactionRepository.save(transferTransaction);

        // When
        List<Transaction> checkingTransactions =
                transactionRepository.findByAccountId(checkingAccount.getId());
        List<Transaction> savingsTransactions =
                transactionRepository.findByAccountId(savingsAccount.getId());

        // Then - Transfer should appear in both accounts
        assertThat(checkingTransactions).hasSize(1);
        assertThat(savingsTransactions).hasSize(1);
        assertThat(checkingTransactions.get(0).getId())
                .isEqualTo(savingsTransactions.get(0).getId());
    }

    // === Date Range Filtering Tests ===

    @Test
    @DisplayName("Should find transactions by date range")
    void shouldFindTransactionsByDateRange() {
        // Given
        transactionRepository.save(incomeTransaction); // Jan 15
        transactionRepository.save(expenseTransaction); // Jan 20
        transactionRepository.save(transferTransaction); // Jan 25

        // When
        List<Transaction> transactions =
                transactionRepository.findByUserIdAndDateBetween(
                        testUser1.getId(), LocalDate.of(2026, 1, 16), LocalDate.of(2026, 1, 24));

        // Then
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getDescription()).isEqualTo("Weekly groceries");
    }

    @Test
    @DisplayName("Should find transactions with inclusive date boundaries")
    void shouldFindTransactionsWithInclusiveDateBoundaries() {
        // Given
        transactionRepository.save(incomeTransaction); // Jan 15
        transactionRepository.save(expenseTransaction); // Jan 20

        // When
        List<Transaction> transactions =
                transactionRepository.findByUserIdAndDateBetween(
                        testUser1.getId(), LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 20));

        // Then - Both boundary dates included
        assertThat(transactions).hasSize(2);
    }

    // === Category Filtering Tests ===

    @Test
    @DisplayName("Should find transactions by category ID")
    void shouldFindTransactionsByCategoryId() {
        // Given
        transactionRepository.save(incomeTransaction);
        transactionRepository.save(expenseTransaction);

        // When
        List<Transaction> salaryTransactions =
                transactionRepository.findByCategoryId(salaryCategory.getId());
        List<Transaction> groceryTransactions =
                transactionRepository.findByCategoryId(groceryCategory.getId());

        // Then
        assertThat(salaryTransactions).hasSize(1);
        assertThat(salaryTransactions.get(0).getDescription()).isEqualTo("Monthly Salary");

        assertThat(groceryTransactions).hasSize(1);
        assertThat(groceryTransactions.get(0).getDescription()).isEqualTo("Weekly groceries");
    }

    @Test
    @DisplayName("Should find transactions by user ID and category ID")
    void shouldFindTransactionsByUserIdAndCategoryId() {
        // Given
        transactionRepository.save(incomeTransaction);
        transactionRepository.save(expenseTransaction);

        // When
        List<Transaction> transactions =
                transactionRepository.findByUserIdAndCategoryId(
                        testUser1.getId(), salaryCategory.getId());

        // Then
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getType()).isEqualTo(TransactionType.INCOME);
    }

    // === Transaction Type Filtering Tests ===

    @Test
    @DisplayName("Should find transactions by type")
    void shouldFindTransactionsByType() {
        // Given
        transactionRepository.save(incomeTransaction);
        transactionRepository.save(expenseTransaction);
        transactionRepository.save(transferTransaction);

        // When
        List<Transaction> incomeTransactions =
                transactionRepository.findByUserIdAndType(
                        testUser1.getId(), TransactionType.INCOME);
        List<Transaction> expenseTransactions =
                transactionRepository.findByUserIdAndType(
                        testUser1.getId(), TransactionType.EXPENSE);
        List<Transaction> transferTransactions =
                transactionRepository.findByUserIdAndType(
                        testUser1.getId(), TransactionType.TRANSFER);

        // Then
        assertThat(incomeTransactions).hasSize(1);
        assertThat(incomeTransactions.get(0).getDescription()).isEqualTo("Monthly Salary");

        assertThat(expenseTransactions).hasSize(1);
        assertThat(expenseTransactions.get(0).getDescription()).isEqualTo("Weekly groceries");

        assertThat(transferTransactions).hasSize(1);
        assertThat(transferTransactions.get(0).getDescription()).isEqualTo("Transfer to savings");
    }

    // === Reconciliation Tests ===

    @Test
    @DisplayName("Should find unreconciled transactions")
    void shouldFindUnreconciledTransactions() {
        // Given
        incomeTransaction.setIsReconciled(true);
        transactionRepository.save(incomeTransaction);
        transactionRepository.save(expenseTransaction); // Not reconciled

        // When
        List<Transaction> unreconciled =
                transactionRepository.findUnreconciledByAccountId(checkingAccount.getId());

        // Then
        assertThat(unreconciled).hasSize(1);
        assertThat(unreconciled.get(0).getDescription()).isEqualTo("Weekly groceries");
        assertThat(unreconciled.get(0).getIsReconciled()).isFalse();
    }

    @Test
    @DisplayName("Should order unreconciled transactions by date ascending")
    void shouldOrderUnreconciledTransactionsByDateAscending() {
        // Given
        expenseTransaction.setDate(LocalDate.of(2026, 1, 10));
        transactionRepository.save(expenseTransaction);

        transferTransaction.setDate(LocalDate.of(2026, 1, 5));
        transactionRepository.save(transferTransaction);

        // When
        List<Transaction> unreconciled =
                transactionRepository.findUnreconciledByAccountId(checkingAccount.getId());

        // Then
        assertThat(unreconciled).hasSize(2);
        assertThat(unreconciled.get(0).getDate()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(unreconciled.get(1).getDate()).isEqualTo(LocalDate.of(2026, 1, 10));
    }

    // === Count and Existence Tests ===

    @Test
    @DisplayName("Should count transactions by user ID")
    void shouldCountTransactionsByUserId() {
        // Given
        transactionRepository.save(incomeTransaction);
        transactionRepository.save(expenseTransaction);
        transactionRepository.save(transferTransaction);

        // When
        Long count = transactionRepository.countByUserId(testUser1.getId());

        // Then
        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should not count soft-deleted transactions")
    void shouldNotCountSoftDeletedTransactions() {
        // Given
        transactionRepository.save(incomeTransaction);
        expenseTransaction.setIsDeleted(true);
        transactionRepository.save(expenseTransaction);

        // When
        Long count = transactionRepository.countByUserId(testUser1.getId());

        // Then
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should check if transaction exists by ID and user ID")
    void shouldCheckIfTransactionExistsByIdAndUserId() {
        // Given
        Transaction savedTransaction = transactionRepository.save(incomeTransaction);

        // When
        boolean exists =
                transactionRepository.existsByIdAndUserId(
                        savedTransaction.getId(), testUser1.getId());
        boolean notExists =
                transactionRepository.existsByIdAndUserId(
                        savedTransaction.getId(), testUser2.getId());

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("Should return false for soft-deleted transaction existence check")
    void shouldReturnFalseForSoftDeletedTransactionExistenceCheck() {
        // Given
        incomeTransaction.setIsDeleted(true);
        Transaction savedTransaction = transactionRepository.save(incomeTransaction);

        // When
        boolean exists =
                transactionRepository.existsByIdAndUserId(
                        savedTransaction.getId(), testUser1.getId());

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Should return empty list when no transactions for user")
    void shouldReturnEmptyListWhenNoTransactionsForUser() {
        // When
        List<Transaction> transactions = transactionRepository.findByUserId(testUser2.getId());

        // Then
        assertThat(transactions).isEmpty();
    }

    @Test
    @DisplayName("Should return zero count for user with no transactions")
    void shouldReturnZeroCountForUserWithNoTransactions() {
        // When
        Long count = transactionRepository.countByUserId(testUser2.getId());

        // Then
        assertThat(count).isEqualTo(0L);
    }
}
