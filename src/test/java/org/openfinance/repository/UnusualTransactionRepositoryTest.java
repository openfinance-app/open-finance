package org.openfinance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for the unusual-transaction-detection queries added to {@link
 * TransactionRepository}.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>{@code findByUserIdAndCreatedAtAfter} — recent transactions lookup
 *   <li>{@code countByUserIdAndPayeeAndCreatedAtBefore} — first-time payee check
 *   <li>{@code findByUserIdAndPayeeAndCreatedAtBefore} — per-payee history
 *   <li>{@code findExpensesByUserIdAndCategoryIdAndCreatedAtBefore} — category history
 * </ul>
 *
 * <p>Uses H2 in-memory database with {@code ddl-auto=create-drop} so no Flyway migrations are
 * needed.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
        })
@DisplayName("TransactionRepository — unusual transaction detection queries")
class UnusualTransactionRepositoryTest {

    @Autowired private TransactionRepository transactionRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private AccountRepository accountRepository;

    @Autowired private CategoryRepository categoryRepository;

    private User user1;
    private User user2;
    private Account account;
    private Category expenseCategory;

    // Reference timestamps used throughout tests
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 10, 12, 0);
    private static final LocalDateTime CUTOFF = BASE.plusDays(30); // "since" boundary

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        user1 =
                userRepository.save(
                        User.builder()
                                .username("user1")
                                .email("user1@test.com")
                                .passwordHash("hash1")
                                .masterPasswordSalt("salt1")
                                .baseCurrency("USD")
                                .build());

        user2 =
                userRepository.save(
                        User.builder()
                                .username("user2")
                                .email("user2@test.com")
                                .passwordHash("hash2")
                                .masterPasswordSalt("salt2")
                                .baseCurrency("USD")
                                .build());

        account =
                accountRepository.save(
                        Account.builder()
                                .userId(user1.getId())
                                .name("Checking")
                                .type(AccountType.CHECKING)
                                .currency("EUR")
                                .balance(BigDecimal.ZERO)
                                .isActive(true)
                                .build());

        expenseCategory =
                categoryRepository.save(
                        Category.builder()
                                .userId(user1.getId())
                                .name("Groceries")
                                .type(CategoryType.EXPENSE)
                                .icon("🛒")
                                .isSystem(false)
                                .build());
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    /**
     * Saves and returns a transaction whose {@code createdAt} is set by forcibly flushing it after
     * saving (the @PrePersist sets it to now). We rely on the JPA @PrePersist setting createdAt to
     * "now" and then manipulate it via a raw JPQL update workaround — instead, we just save the
     * entity and compare against real LocalDateTime.now() values by using relative saves in the
     * correct test ordering.
     */
    private Transaction saveExpense(
            Long userId,
            Long accountId,
            Long categoryId,
            String payee,
            double amount,
            LocalDate date,
            boolean deleted) {
        return transactionRepository.save(
                Transaction.builder()
                        .userId(userId)
                        .accountId(accountId)
                        .type(TransactionType.EXPENSE)
                        .amount(BigDecimal.valueOf(amount))
                        .currency("EUR")
                        .categoryId(categoryId)
                        .payee(payee)
                        .date(date)
                        .isReconciled(false)
                        .isDeleted(deleted)
                        .build());
    }

    private Transaction saveIncome(Long userId, Long accountId, String payee, double amount) {
        return transactionRepository.save(
                Transaction.builder()
                        .userId(userId)
                        .accountId(accountId)
                        .type(TransactionType.INCOME)
                        .amount(BigDecimal.valueOf(amount))
                        .currency("EUR")
                        .payee(payee)
                        .date(LocalDate.now())
                        .isReconciled(false)
                        .isDeleted(false)
                        .build());
    }

    // ------------------------------------------------------------------
    // findByUserIdAndCreatedAtAfter
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByUserIdAndCreatedAtAfter")
    class FindByUserIdAndCreatedAtAfter {

        @Test
        @DisplayName("Returns transactions created after the cutoff")
        void returnsTransactionsCreatedAfterCutoff() {
            // Save some transactions – all persist with createdAt = now()
            Transaction t1 =
                    saveExpense(
                            user1.getId(),
                            account.getId(),
                            expenseCategory.getId(),
                            "Supermarket",
                            50,
                            LocalDate.now(),
                            false);
            Transaction t2 =
                    saveExpense(
                            user1.getId(),
                            account.getId(),
                            expenseCategory.getId(),
                            "Amazon",
                            80,
                            LocalDate.now(),
                            false);

            // Query with a past cutoff — should find both
            List<Transaction> result =
                    transactionRepository.findByUserIdAndCreatedAtAfter(
                            user1.getId(), LocalDateTime.now().minusMinutes(5));

            assertThat(result).extracting(Transaction::getId).contains(t1.getId(), t2.getId());
        }

        @Test
        @DisplayName("Returns empty list when all transactions are older than cutoff")
        void returnsEmptyListWhenAllOlderThanCutoff() {
            saveExpense(
                    user1.getId(),
                    account.getId(),
                    expenseCategory.getId(),
                    "OldShop",
                    20,
                    LocalDate.now().minusDays(60),
                    false);

            // Query with a future cutoff (in the future relative to "now")
            List<Transaction> result =
                    transactionRepository.findByUserIdAndCreatedAtAfter(
                            user1.getId(), LocalDateTime.now().plusMinutes(5));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Excludes soft-deleted transactions")
        void excludesSoftDeletedTransactions() {
            saveExpense(
                    user1.getId(),
                    account.getId(),
                    expenseCategory.getId(),
                    "DeletedShop",
                    30,
                    LocalDate.now(),
                    true); // deleted
            Transaction live =
                    saveExpense(
                            user1.getId(),
                            account.getId(),
                            expenseCategory.getId(),
                            "LiveShop",
                            30,
                            LocalDate.now(),
                            false);

            List<Transaction> result =
                    transactionRepository.findByUserIdAndCreatedAtAfter(
                            user1.getId(), LocalDateTime.now().minusMinutes(5));

            assertThat(result).extracting(Transaction::getId).containsOnly(live.getId());
        }

        @Test
        @DisplayName("Respects user isolation (user2 transactions not returned)")
        void respectsUserIsolation() {
            Account account2 =
                    accountRepository.save(
                            Account.builder()
                                    .userId(user2.getId())
                                    .name("Acc2")
                                    .type(AccountType.CHECKING)
                                    .currency("EUR")
                                    .balance(BigDecimal.ZERO)
                                    .isActive(true)
                                    .build());

            saveExpense(user1.getId(), account.getId(), null, "ShopA", 50, LocalDate.now(), false);
            saveExpense(user2.getId(), account2.getId(), null, "ShopB", 80, LocalDate.now(), false);

            List<Transaction> result =
                    transactionRepository.findByUserIdAndCreatedAtAfter(
                            user1.getId(), LocalDateTime.now().minusMinutes(5));

            assertThat(result).allMatch(t -> t.getUserId().equals(user1.getId()));
        }
    }

    // ------------------------------------------------------------------
    // countByUserIdAndPayeeAndCreatedAtBefore
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("countByUserIdAndPayeeAndCreatedAtBefore")
    class CountByUserIdAndPayeeAndCreatedAtBefore {

        @Test
        @DisplayName("Returns 0 for a brand-new payee")
        void returnsZeroForNewPayee() {
            long count =
                    transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            user1.getId(), "BrandNewPayee", LocalDateTime.now().plusMinutes(5));

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("Returns correct count for existing payee")
        void returnsCorrectCountForExistingPayee() {
            saveExpense(
                    user1.getId(), account.getId(), null, "Netflix", 15, LocalDate.now(), false);
            saveExpense(
                    user1.getId(), account.getId(), null, "Netflix", 15, LocalDate.now(), false);
            saveExpense(
                    user1.getId(), account.getId(), null, "Spotify", 10, LocalDate.now(), false);

            long count =
                    transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            user1.getId(), "Netflix", LocalDateTime.now().plusMinutes(5));

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("Excludes transactions created at or after the cutoff (exclusive upper bound)")
        void excludesTransactionsAtOrAfterCutoff() {
            // All saved transactions have createdAt = now(); cutoff = 5 min ago
            saveExpense(
                    user1.getId(), account.getId(), null, "Netflix", 15, LocalDate.now(), false);

            long count =
                    transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            user1.getId(), "Netflix", LocalDateTime.now().minusMinutes(5));

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("Excludes soft-deleted transactions")
        void excludesSoftDeleted() {
            saveExpense(user1.getId(), account.getId(), null, "Netflix", 15, LocalDate.now(), true);

            long count =
                    transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            user1.getId(), "Netflix", LocalDateTime.now().plusMinutes(5));

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("Respects user isolation")
        void respectsUserIsolation() {
            Account account2 =
                    accountRepository.save(
                            Account.builder()
                                    .userId(user2.getId())
                                    .name("Acc2")
                                    .type(AccountType.CHECKING)
                                    .currency("EUR")
                                    .balance(BigDecimal.ZERO)
                                    .isActive(true)
                                    .build());

            saveExpense(
                    user2.getId(), account2.getId(), null, "Netflix", 15, LocalDate.now(), false);

            long count =
                    transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            user1.getId(), "Netflix", LocalDateTime.now().plusMinutes(5));

            assertThat(count).isZero();
        }
    }

    // ------------------------------------------------------------------
    // findByUserIdAndPayeeAndCreatedAtBefore
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByUserIdAndPayeeAndCreatedAtBefore")
    class FindByUserIdAndPayeeAndCreatedAtBefore {

        @Test
        @DisplayName("Returns all prior transactions for a payee")
        void returnsAllPriorTransactionsForPayee() {
            Transaction t1 =
                    saveExpense(
                            user1.getId(),
                            account.getId(),
                            null,
                            "Amazon",
                            20,
                            LocalDate.now(),
                            false);
            Transaction t2 =
                    saveExpense(
                            user1.getId(),
                            account.getId(),
                            null,
                            "Amazon",
                            25,
                            LocalDate.now(),
                            false);
            saveExpense(user1.getId(), account.getId(), null, "Ebay", 30, LocalDate.now(), false);

            List<Transaction> result =
                    transactionRepository.findByUserIdAndPayeeAndCreatedAtBefore(
                            user1.getId(), "Amazon", LocalDateTime.now().plusMinutes(5));

            assertThat(result)
                    .extracting(Transaction::getId)
                    .containsExactlyInAnyOrder(t1.getId(), t2.getId());
        }

        @Test
        @DisplayName("Returns empty list when no prior transactions exist for payee")
        void returnsEmptyListWhenNoHistory() {
            List<Transaction> result =
                    transactionRepository.findByUserIdAndPayeeAndCreatedAtBefore(
                            user1.getId(), "UnknownPayee", LocalDateTime.now().plusMinutes(5));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Excludes soft-deleted transactions")
        void excludesSoftDeleted() {
            saveExpense(user1.getId(), account.getId(), null, "Amazon", 20, LocalDate.now(), true);

            List<Transaction> result =
                    transactionRepository.findByUserIdAndPayeeAndCreatedAtBefore(
                            user1.getId(), "Amazon", LocalDateTime.now().plusMinutes(5));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Amounts are correct for statistical calculations")
        void amountsAreCorrect() {
            saveExpense(user1.getId(), account.getId(), null, "Gym", 49.99, LocalDate.now(), false);
            saveExpense(user1.getId(), account.getId(), null, "Gym", 50.00, LocalDate.now(), false);
            saveExpense(user1.getId(), account.getId(), null, "Gym", 50.01, LocalDate.now(), false);

            List<Transaction> result =
                    transactionRepository.findByUserIdAndPayeeAndCreatedAtBefore(
                            user1.getId(), "Gym", LocalDateTime.now().plusMinutes(5));

            double totalAmount =
                    result.stream().mapToDouble(t -> t.getAmount().doubleValue()).sum();
            assertThat(totalAmount).isCloseTo(150.0, org.assertj.core.data.Offset.offset(0.01));
        }
    }

    // ------------------------------------------------------------------
    // findExpensesByUserIdAndCategoryIdAndCreatedAtBefore
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findExpensesByUserIdAndCategoryIdAndCreatedAtBefore")
    class FindExpensesByUserIdAndCategoryIdAndCreatedAtBefore {

        @Test
        @DisplayName("Returns only EXPENSE transactions for a given category")
        void returnsOnlyExpensesForCategory() {
            Transaction expense =
                    saveExpense(
                            user1.getId(),
                            account.getId(),
                            expenseCategory.getId(),
                            null,
                            60,
                            LocalDate.now(),
                            false);
            saveIncome(user1.getId(), account.getId(), null, 3000); // Should be excluded

            List<Transaction> result =
                    transactionRepository.findExpensesByUserIdAndCategoryIdAndCreatedAtBefore(
                            user1.getId(),
                            expenseCategory.getId(),
                            LocalDateTime.now().plusMinutes(5));

            assertThat(result).extracting(Transaction::getId).containsOnly(expense.getId());
        }

        @Test
        @DisplayName("Excludes soft-deleted transactions")
        void excludesSoftDeleted() {
            saveExpense(
                    user1.getId(),
                    account.getId(),
                    expenseCategory.getId(),
                    null,
                    60,
                    LocalDate.now(),
                    true); // deleted

            List<Transaction> result =
                    transactionRepository.findExpensesByUserIdAndCategoryIdAndCreatedAtBefore(
                            user1.getId(),
                            expenseCategory.getId(),
                            LocalDateTime.now().plusMinutes(5));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Respects cutoff — excludes transactions created after cutoff")
        void respectsCutoff() {
            saveExpense(
                    user1.getId(),
                    account.getId(),
                    expenseCategory.getId(),
                    null,
                    60,
                    LocalDate.now(),
                    false);

            // cutoff in the past → nothing should be returned
            List<Transaction> result =
                    transactionRepository.findExpensesByUserIdAndCategoryIdAndCreatedAtBefore(
                            user1.getId(),
                            expenseCategory.getId(),
                            LocalDateTime.now().minusMinutes(5));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Returns empty list when no history for category")
        void returnsEmptyListWhenNoCategoryHistory() {
            Category otherCategory =
                    categoryRepository.save(
                            Category.builder()
                                    .userId(user1.getId())
                                    .name("Travel")
                                    .type(CategoryType.EXPENSE)
                                    .icon("✈")
                                    .isSystem(false)
                                    .build());

            List<Transaction> result =
                    transactionRepository.findExpensesByUserIdAndCategoryIdAndCreatedAtBefore(
                            user1.getId(),
                            otherCategory.getId(),
                            LocalDateTime.now().plusMinutes(5));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Respects user isolation across categories")
        void respectsUserIsolation() {
            Account account2 =
                    accountRepository.save(
                            Account.builder()
                                    .userId(user2.getId())
                                    .name("Acc2")
                                    .type(AccountType.CHECKING)
                                    .currency("EUR")
                                    .balance(BigDecimal.ZERO)
                                    .isActive(true)
                                    .build());

            // user2 has transactions in the same category ID (by coincidence)
            Category cat2 =
                    categoryRepository.save(
                            Category.builder()
                                    .userId(user2.getId())
                                    .name("Groceries2")
                                    .type(CategoryType.EXPENSE)
                                    .icon("🛒")
                                    .isSystem(false)
                                    .build());
            saveExpense(
                    user2.getId(),
                    account2.getId(),
                    cat2.getId(),
                    null,
                    100,
                    LocalDate.now(),
                    false);

            // user1 has no transactions at all
            List<Transaction> result =
                    transactionRepository.findExpensesByUserIdAndCategoryIdAndCreatedAtBefore(
                            user1.getId(),
                            expenseCategory.getId(),
                            LocalDateTime.now().plusMinutes(5));

            assertThat(result).isEmpty();
        }
    }
}
