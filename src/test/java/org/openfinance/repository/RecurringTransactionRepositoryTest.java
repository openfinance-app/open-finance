package org.openfinance.repository;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
 * Integration tests for RecurringTransactionRepository.
 *
 * <p>Tests all 16 query methods with comprehensive scenarios including user isolation, active
 * filtering, due date checking, and authorization.
 *
 * <p>Requirements: REQ-2.3.6 - Recurring transaction repository operations
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(
        properties = {
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.show-sql=false",
            "spring.flyway.enabled=false",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
        })
@DisplayName("RecurringTransactionRepository Integration Tests")
class RecurringTransactionRepositoryTest {

    @Autowired private RecurringTransactionRepository recurringTransactionRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private AccountRepository accountRepository;

    @Autowired private CategoryRepository categoryRepository;

    private User user1;
    private User user2;
    private Account account1User1;
    private Account account2User1;
    private Account account1User2;
    private Category categoryExpense;
    private Category categoryIncome;

    @BeforeEach
    void setUp() {
        // Clean up database
        recurringTransactionRepository.deleteAll();
        accountRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // Create test users
        user1 =
                User.builder()
                        .username("user1")
                        .email("user1@test.com")
                        .passwordHash("$2a$10$hashedPasswordExample123456789")
                        .masterPasswordSalt("base64EncodedSaltExample==")
                        .baseCurrency("USD")
                        .build();
        user1 = userRepository.save(user1);

        user2 =
                User.builder()
                        .username("user2")
                        .email("user2@test.com")
                        .passwordHash("$2a$10$hashedPasswordExample123456789")
                        .masterPasswordSalt("base64EncodedSaltExample==")
                        .baseCurrency("USD")
                        .build();
        user2 = userRepository.save(user2);

        // Create test accounts
        account1User1 =
                Account.builder()
                        .userId(user1.getId())
                        .name("encrypted-Checking Account")
                        .type(AccountType.CHECKING)
                        .balance(new BigDecimal("1000.00"))
                        .currency("USD")
                        .isActive(true)
                        .build();
        account1User1 = accountRepository.save(account1User1);

        account2User1 =
                Account.builder()
                        .userId(user1.getId())
                        .name("encrypted-Savings Account")
                        .type(AccountType.SAVINGS)
                        .balance(new BigDecimal("5000.00"))
                        .currency("USD")
                        .isActive(true)
                        .build();
        account2User1 = accountRepository.save(account2User1);

        account1User2 =
                Account.builder()
                        .userId(user2.getId())
                        .name("encrypted-Checking Account")
                        .type(AccountType.CHECKING)
                        .balance(new BigDecimal("2000.00"))
                        .currency("USD")
                        .isActive(true)
                        .build();
        account1User2 = accountRepository.save(account1User2);

        // Create test categories
        categoryExpense =
                Category.builder()
                        .userId(user1.getId())
                        .name("encrypted-Rent")
                        .type(CategoryType.EXPENSE)
                        .icon("home")
                        .color("#FF0000")
                        .isSystem(false)
                        .build();
        categoryExpense = categoryRepository.save(categoryExpense);

        categoryIncome =
                Category.builder()
                        .userId(user1.getId())
                        .name("encrypted-Salary")
                        .type(CategoryType.INCOME)
                        .icon("dollar")
                        .color("#00FF00")
                        .isSystem(false)
                        .build();
        categoryIncome = categoryRepository.save(categoryIncome);
    }

    // ========== CRUD OPERATIONS TESTS ==========

    @Nested
    @DisplayName("CRUD Operations Tests")
    class CrudOperationsTests {

        @Test
        @DisplayName("Should save and find recurring transaction by ID")
        void shouldSaveAndFindById() {
            // Arrange
            RecurringTransaction recurring =
                    createMonthlyRent(user1, account1User1, categoryExpense);

            // Act
            RecurringTransaction saved = recurringTransactionRepository.save(recurring);
            Optional<RecurringTransaction> found =
                    recurringTransactionRepository.findById(saved.getId());

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(saved.getId());
            assertThat(found.get().getDescription()).isEqualTo("encrypted-Monthly Rent");
            assertThat(found.get().getAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
            assertThat(found.get().getFrequency()).isEqualTo(RecurringFrequency.MONTHLY);
        }

        @Test
        @DisplayName("Should delete recurring transaction by ID")
        void shouldDeleteById() {
            // Arrange
            RecurringTransaction recurring =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            RecurringTransaction saved = recurringTransactionRepository.save(recurring);

            // Act
            recurringTransactionRepository.deleteById(saved.getId());
            Optional<RecurringTransaction> found =
                    recurringTransactionRepository.findById(saved.getId());

            // Assert
            assertThat(found).isEmpty();
        }
    }

    // ========== USER ISOLATION TESTS ==========

    @Nested
    @DisplayName("User Isolation Tests")
    class UserIsolationTests {

        @Test
        @DisplayName("findByUserId() should return only user's recurring transactions")
        void shouldReturnOnlyUserRecurringTransactions() {
            // Arrange
            RecurringTransaction recurring1User1 =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            RecurringTransaction recurring2User1 =
                    createBiweeklySalary(user1, account1User1, categoryIncome);
            RecurringTransaction recurringUser2 =
                    createMonthlyRent(user2, account1User2, categoryExpense);

            recurringTransactionRepository.saveAll(
                    List.of(recurring1User1, recurring2User1, recurringUser2));

            // Act
            List<RecurringTransaction> user1Recurring =
                    recurringTransactionRepository.findByUserId(user1.getId());
            List<RecurringTransaction> user2Recurring =
                    recurringTransactionRepository.findByUserId(user2.getId());

            // Assert
            assertThat(user1Recurring).hasSize(2);
            assertThat(user1Recurring)
                    .extracting(RecurringTransaction::getUserId)
                    .containsOnly(user1.getId());

            assertThat(user2Recurring).hasSize(1);
            assertThat(user2Recurring)
                    .extracting(RecurringTransaction::getUserId)
                    .containsOnly(user2.getId());
        }

        @Test
        @DisplayName("findByUserId() should return empty list for new user")
        void shouldReturnEmptyListForNewUser() {
            // Arrange
            Long nonExistentUserId = 99999L;

            // Act
            List<RecurringTransaction> result =
                    recurringTransactionRepository.findByUserId(nonExistentUserId);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // ========== ACTIVE FILTERING TESTS ==========

    @Nested
    @DisplayName("Active Filtering Tests")
    class ActiveFilteringTests {

        @Test
        @DisplayName("findByUserIdAndIsActive() should return only active recurring transactions")
        void shouldReturnOnlyActiveRecurringTransactions() {
            // Arrange
            RecurringTransaction active1 = createMonthlyRent(user1, account1User1, categoryExpense);
            active1.setIsActive(true);

            RecurringTransaction active2 =
                    createBiweeklySalary(user1, account1User1, categoryIncome);
            active2.setIsActive(true);

            RecurringTransaction inactive =
                    createWeeklySubscription(user1, account1User1, categoryExpense);
            inactive.setIsActive(false);

            recurringTransactionRepository.saveAll(List.of(active1, active2, inactive));

            // Act
            List<RecurringTransaction> activeRecurring =
                    recurringTransactionRepository.findByUserIdAndIsActive(user1.getId());

            // Assert
            assertThat(activeRecurring).hasSize(2);
            assertThat(activeRecurring)
                    .extracting(RecurringTransaction::getIsActive)
                    .containsOnly(true);
        }

        @Test
        @DisplayName("findByUserIdAndIsActive() should return empty list when all are inactive")
        void shouldReturnEmptyListWhenAllInactive() {
            // Arrange
            RecurringTransaction inactive1 =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            inactive1.setIsActive(false);

            RecurringTransaction inactive2 =
                    createWeeklySubscription(user1, account1User1, categoryExpense);
            inactive2.setIsActive(false);

            recurringTransactionRepository.saveAll(List.of(inactive1, inactive2));

            // Act
            List<RecurringTransaction> activeRecurring =
                    recurringTransactionRepository.findByUserIdAndIsActive(user1.getId());

            // Assert
            assertThat(activeRecurring).isEmpty();
        }
    }

    // ========== AUTHORIZATION QUERIES TESTS ==========

    @Nested
    @DisplayName("Authorization Queries Tests")
    class AuthorizationQueriesTests {

        @Test
        @DisplayName("findByIdAndUserId() should return recurring transaction when owned by user")
        void shouldReturnRecurringTransactionWhenOwnedByUser() {
            // Arrange
            RecurringTransaction recurring =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            RecurringTransaction saved = recurringTransactionRepository.save(recurring);

            // Act
            Optional<RecurringTransaction> found =
                    recurringTransactionRepository.findByIdAndUserId(saved.getId(), user1.getId());

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(saved.getId());
        }

        @Test
        @DisplayName("findByIdAndUserId() should return empty when not owned by user")
        void shouldReturnEmptyWhenNotOwnedByUser() {
            // Arrange
            RecurringTransaction recurring =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            RecurringTransaction saved = recurringTransactionRepository.save(recurring);

            // Act
            Optional<RecurringTransaction> found =
                    recurringTransactionRepository.findByIdAndUserId(saved.getId(), user2.getId());

            // Assert
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("existsByIdAndUserId() should return true when owned by user")
        void shouldReturnTrueWhenOwnedByUser() {
            // Arrange
            RecurringTransaction recurring =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            RecurringTransaction saved = recurringTransactionRepository.save(recurring);

            // Act
            boolean exists =
                    recurringTransactionRepository.existsByIdAndUserId(
                            saved.getId(), user1.getId());

            // Assert
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("existsByIdAndUserId() should return false when not owned by user")
        void shouldReturnFalseWhenNotOwnedByUser() {
            // Arrange
            RecurringTransaction recurring =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            RecurringTransaction saved = recurringTransactionRepository.save(recurring);

            // Act
            boolean exists =
                    recurringTransactionRepository.existsByIdAndUserId(
                            saved.getId(), user2.getId());

            // Assert
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("deleteByIdAndUserId() should delete only when owned by user")
        void shouldDeleteOnlyWhenOwnedByUser() {
            // Arrange
            RecurringTransaction recurring1 =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            RecurringTransaction recurring2 =
                    createBiweeklySalary(user1, account1User1, categoryIncome);
            recurringTransactionRepository.saveAll(List.of(recurring1, recurring2));

            // Act
            int deletedCount =
                    recurringTransactionRepository.deleteByIdAndUserId(
                            recurring1.getId(), user2.getId());

            // Assert
            assertThat(deletedCount).isZero();
            assertThat(recurringTransactionRepository.findById(recurring1.getId())).isPresent();
        }
    }

    // ========== DUE RECURRING TRANSACTIONS TESTS (CRITICAL) ==========

    @Nested
    @DisplayName("Due Recurring Transactions Tests (CRITICAL)")
    class DueRecurringTransactionsTests {

        @Test
        @DisplayName(
                "findDueRecurringTransactions() should return active recurring where nextOccurrence is today")
        void shouldReturnDueRecurringTransactionsForToday() {
            // Arrange
            RecurringTransaction dueToday =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            dueToday.setNextOccurrence(LocalDate.now());
            dueToday.setIsActive(true);
            dueToday.setEndDate(null);

            recurringTransactionRepository.save(dueToday);

            // Act
            List<RecurringTransaction> dueRecurring =
                    recurringTransactionRepository.findDueRecurringTransactions(LocalDate.now());

            // Assert
            assertThat(dueRecurring).hasSize(1);
            assertThat(dueRecurring.get(0).getId()).isEqualTo(dueToday.getId());
        }

        @Test
        @DisplayName(
                "findDueRecurringTransactions() should return active recurring where nextOccurrence is in past (overdue)")
        void shouldReturnOverdueRecurringTransactions() {
            // Arrange
            RecurringTransaction overdue = createMonthlyRent(user1, account1User1, categoryExpense);
            overdue.setNextOccurrence(LocalDate.now().minusDays(5));
            overdue.setIsActive(true);
            overdue.setEndDate(null);

            recurringTransactionRepository.save(overdue);

            // Act
            List<RecurringTransaction> dueRecurring =
                    recurringTransactionRepository.findDueRecurringTransactions(LocalDate.now());

            // Assert
            assertThat(dueRecurring).hasSize(1);
            assertThat(dueRecurring.get(0).getNextOccurrence()).isBefore(LocalDate.now());
        }

        @Test
        @DisplayName("findDueRecurringTransactions() should exclude future recurring transactions")
        void shouldExcludeFutureRecurringTransactions() {
            // Arrange
            RecurringTransaction future = createMonthlyRent(user1, account1User1, categoryExpense);
            future.setNextOccurrence(LocalDate.now().plusDays(5));
            future.setIsActive(true);
            future.setEndDate(null);

            recurringTransactionRepository.save(future);

            // Act
            List<RecurringTransaction> dueRecurring =
                    recurringTransactionRepository.findDueRecurringTransactions(LocalDate.now());

            // Assert
            assertThat(dueRecurring).isEmpty();
        }

        @Test
        @DisplayName(
                "findDueRecurringTransactions() should exclude inactive recurring transactions")
        void shouldExcludeInactiveRecurringTransactions() {
            // Arrange
            RecurringTransaction inactive =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            inactive.setNextOccurrence(LocalDate.now());
            inactive.setIsActive(false);
            inactive.setEndDate(null);

            recurringTransactionRepository.save(inactive);

            // Act
            List<RecurringTransaction> dueRecurring =
                    recurringTransactionRepository.findDueRecurringTransactions(LocalDate.now());

            // Assert
            assertThat(dueRecurring).isEmpty();
        }

        @Test
        @DisplayName("findDueRecurringTransactions() should exclude ended recurring transactions")
        void shouldExcludeEndedRecurringTransactions() {
            // Arrange
            RecurringTransaction ended = createMonthlyRent(user1, account1User1, categoryExpense);
            ended.setNextOccurrence(LocalDate.now());
            ended.setIsActive(true);
            ended.setEndDate(LocalDate.now().minusDays(10));

            recurringTransactionRepository.save(ended);

            // Act
            List<RecurringTransaction> dueRecurring =
                    recurringTransactionRepository.findDueRecurringTransactions(LocalDate.now());

            // Assert
            assertThat(dueRecurring).isEmpty();
        }

        @Test
        @DisplayName(
                "findDueRecurringTransactions() should include recurring with endDate = null (indefinite)")
        void shouldIncludeIndefiniteRecurringTransactions() {
            // Arrange
            RecurringTransaction indefinite =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            indefinite.setNextOccurrence(LocalDate.now());
            indefinite.setIsActive(true);
            indefinite.setEndDate(null);

            recurringTransactionRepository.save(indefinite);

            // Act
            List<RecurringTransaction> dueRecurring =
                    recurringTransactionRepository.findDueRecurringTransactions(LocalDate.now());

            // Assert
            assertThat(dueRecurring).hasSize(1);
            assertThat(dueRecurring.get(0).getEndDate()).isNull();
        }

        @Test
        @DisplayName(
                "findDueRecurringTransactions() should include recurring with endDate in future")
        void shouldIncludeRecurringWithEndDateInFuture() {
            // Arrange
            RecurringTransaction futureEnd =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            futureEnd.setNextOccurrence(LocalDate.now());
            futureEnd.setIsActive(true);
            futureEnd.setEndDate(LocalDate.now().plusMonths(6));

            recurringTransactionRepository.save(futureEnd);

            // Act
            List<RecurringTransaction> dueRecurring =
                    recurringTransactionRepository.findDueRecurringTransactions(LocalDate.now());

            // Assert
            assertThat(dueRecurring).hasSize(1);
        }

        @Test
        @DisplayName(
                "findDueRecurringTransactionsByUserId() should return only user's due recurring")
        void shouldReturnOnlyUsersDueRecurringTransactions() {
            // Arrange
            RecurringTransaction dueUser1 =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            dueUser1.setNextOccurrence(LocalDate.now());
            dueUser1.setIsActive(true);

            RecurringTransaction dueUser2 =
                    createMonthlyRent(user2, account1User2, categoryExpense);
            dueUser2.setNextOccurrence(LocalDate.now());
            dueUser2.setIsActive(true);

            recurringTransactionRepository.saveAll(List.of(dueUser1, dueUser2));

            // Act
            List<RecurringTransaction> user1DueRecurring =
                    recurringTransactionRepository.findDueRecurringTransactionsByUserId(
                            user1.getId(), LocalDate.now());

            // Assert
            assertThat(user1DueRecurring).hasSize(1);
            assertThat(user1DueRecurring)
                    .extracting(RecurringTransaction::getUserId)
                    .containsOnly(user1.getId());
        }
    }

    // ========== FREQUENCY FILTERING TESTS ==========

    @Nested
    @DisplayName("Frequency Filtering Tests")
    class FrequencyFilteringTests {

        @Test
        @DisplayName(
                "findByUserIdAndFrequency() should return only recurring transactions with specified frequency")
        void shouldReturnOnlyRecurringWithSpecifiedFrequency() {
            // Arrange
            RecurringTransaction monthly1 =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            RecurringTransaction monthly2 =
                    createMonthlySubscription(user1, account1User1, categoryExpense);
            RecurringTransaction biweekly =
                    createBiweeklySalary(user1, account1User1, categoryIncome);
            RecurringTransaction weekly =
                    createWeeklySubscription(user1, account1User1, categoryExpense);

            recurringTransactionRepository.saveAll(List.of(monthly1, monthly2, biweekly, weekly));

            // Act
            List<RecurringTransaction> monthlyRecurring =
                    recurringTransactionRepository.findByUserIdAndFrequency(
                            user1.getId(), RecurringFrequency.MONTHLY);

            // Assert
            assertThat(monthlyRecurring).hasSize(2);
            assertThat(monthlyRecurring)
                    .extracting(RecurringTransaction::getFrequency)
                    .containsOnly(RecurringFrequency.MONTHLY);
        }

        @Test
        @DisplayName(
                "findByUserIdAndFrequency() should return empty list when no matching frequency")
        void shouldReturnEmptyListWhenNoMatchingFrequency() {
            // Arrange
            RecurringTransaction monthly = createMonthlyRent(user1, account1User1, categoryExpense);
            recurringTransactionRepository.save(monthly);

            // Act
            List<RecurringTransaction> dailyRecurring =
                    recurringTransactionRepository.findByUserIdAndFrequency(
                            user1.getId(), RecurringFrequency.DAILY);

            // Assert
            assertThat(dailyRecurring).isEmpty();
        }
    }

    // ========== ACCOUNT FILTERING TESTS ==========

    @Nested
    @DisplayName("Account Filtering Tests")
    class AccountFilteringTests {

        @Test
        @DisplayName("findByAccountId() should return recurring where account is source")
        void shouldReturnRecurringWhereAccountIsSource() {
            // Arrange
            RecurringTransaction recurring =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            recurringTransactionRepository.save(recurring);

            // Act
            List<RecurringTransaction> accountRecurring =
                    recurringTransactionRepository.findByAccountId(account1User1.getId());

            // Assert
            assertThat(accountRecurring).hasSize(1);
            assertThat(accountRecurring.get(0).getAccountId()).isEqualTo(account1User1.getId());
        }

        @Test
        @DisplayName(
                "findByAccountId() should return recurring where account is destination (transfer)")
        void shouldReturnRecurringWhereAccountIsDestination() {
            // Arrange
            RecurringTransaction transfer = createTransfer(user1, account1User1, account2User1);
            recurringTransactionRepository.save(transfer);

            // Act
            List<RecurringTransaction> account2Recurring =
                    recurringTransactionRepository.findByAccountId(account2User1.getId());

            // Assert
            assertThat(account2Recurring).hasSize(1);
            assertThat(account2Recurring.get(0).getToAccountId()).isEqualTo(account2User1.getId());
        }

        @Test
        @DisplayName("findByUserIdAndAccountId() should return recurring for user and account")
        void shouldReturnRecurringForUserAndAccount() {
            // Arrange
            RecurringTransaction recurring1 =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            RecurringTransaction recurring2 =
                    createBiweeklySalary(user1, account2User1, categoryIncome);
            RecurringTransaction recurringUser2 =
                    createMonthlyRent(user2, account1User2, categoryExpense);

            recurringTransactionRepository.saveAll(List.of(recurring1, recurring2, recurringUser2));

            // Act
            List<RecurringTransaction> account1Recurring =
                    recurringTransactionRepository.findByUserIdAndAccountId(
                            user1.getId(), account1User1.getId());

            // Assert
            assertThat(account1Recurring).hasSize(1);
            assertThat(account1Recurring)
                    .extracting(RecurringTransaction::getAccountId)
                    .containsOnly(account1User1.getId());
        }
    }

    // ========== ENDING SOON TESTS ==========

    @Nested
    @DisplayName("Ending Soon Tests")
    class EndingSoonTests {

        @Test
        @DisplayName(
                "findEndingSoon() should return recurring transactions ending within date range")
        void shouldReturnRecurringEndingSoon() {
            // Arrange
            LocalDate today = LocalDate.now();
            LocalDate in30Days = today.plusDays(30);

            RecurringTransaction endingSoon =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            endingSoon.setEndDate(today.plusDays(15));
            endingSoon.setIsActive(true);

            RecurringTransaction endingLater =
                    createBiweeklySalary(user1, account1User1, categoryIncome);
            endingLater.setEndDate(today.plusDays(60));
            endingLater.setIsActive(true);

            recurringTransactionRepository.saveAll(List.of(endingSoon, endingLater));

            // Act
            List<RecurringTransaction> endingSoonList =
                    recurringTransactionRepository.findEndingSoon(user1.getId(), today, in30Days);

            // Assert
            assertThat(endingSoonList).hasSize(1);
            assertThat(endingSoonList.get(0).getId()).isEqualTo(endingSoon.getId());
        }

        @Test
        @DisplayName("findEndingSoon() should exclude inactive recurring transactions")
        void shouldExcludeInactiveFromEndingSoon() {
            // Arrange
            LocalDate today = LocalDate.now();
            LocalDate in30Days = today.plusDays(30);

            RecurringTransaction inactive =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            inactive.setEndDate(today.plusDays(15));
            inactive.setIsActive(false);

            recurringTransactionRepository.save(inactive);

            // Act
            List<RecurringTransaction> endingSoonList =
                    recurringTransactionRepository.findEndingSoon(user1.getId(), today, in30Days);

            // Assert
            assertThat(endingSoonList).isEmpty();
        }

        @Test
        @DisplayName("findEndingSoon() should exclude recurring with null endDate")
        void shouldExcludeNullEndDateFromEndingSoon() {
            // Arrange
            LocalDate today = LocalDate.now();
            LocalDate in30Days = today.plusDays(30);

            RecurringTransaction indefinite =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            indefinite.setEndDate(null);
            indefinite.setIsActive(true);

            recurringTransactionRepository.save(indefinite);

            // Act
            List<RecurringTransaction> endingSoonList =
                    recurringTransactionRepository.findEndingSoon(user1.getId(), today, in30Days);

            // Assert
            assertThat(endingSoonList).isEmpty();
        }
    }

    // ========== AGGREGATION TESTS ==========

    @Nested
    @DisplayName("Aggregation Tests")
    class AggregationTests {

        @Test
        @DisplayName("countByUserId() should return total count of recurring transactions")
        void shouldReturnTotalCountOfRecurringTransactions() {
            // Arrange
            RecurringTransaction recurring1 =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            RecurringTransaction recurring2 =
                    createBiweeklySalary(user1, account1User1, categoryIncome);
            RecurringTransaction recurring3 =
                    createWeeklySubscription(user1, account1User1, categoryExpense);
            recurring3.setIsActive(false);

            recurringTransactionRepository.saveAll(List.of(recurring1, recurring2, recurring3));

            // Act
            long count = recurringTransactionRepository.countByUserId(user1.getId());

            // Assert
            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName(
                "countByUserIdAndIsActive() should return count of active recurring transactions")
        void shouldReturnCountOfActiveRecurringTransactions() {
            // Arrange
            RecurringTransaction active1 = createMonthlyRent(user1, account1User1, categoryExpense);
            RecurringTransaction active2 =
                    createBiweeklySalary(user1, account1User1, categoryIncome);
            RecurringTransaction inactive =
                    createWeeklySubscription(user1, account1User1, categoryExpense);
            inactive.setIsActive(false);

            recurringTransactionRepository.saveAll(List.of(active1, active2, inactive));

            // Act
            long activeCount =
                    recurringTransactionRepository.countByUserIdAndIsActive(user1.getId());

            // Assert
            assertThat(activeCount).isEqualTo(2);
        }

        @Test
        @DisplayName("countByUserId() should return 0 for new user")
        void shouldReturnZeroCountForNewUser() {
            // Arrange
            Long nonExistentUserId = 99999L;

            // Act
            long count = recurringTransactionRepository.countByUserId(nonExistentUserId);

            // Assert
            assertThat(count).isZero();
        }
    }

    // ========== ORDERING TESTS ==========

    @Nested
    @DisplayName("Ordering Tests")
    class OrderingTests {

        @Test
        @DisplayName("findByUserId() should order by nextOccurrence ASC")
        void shouldOrderByNextOccurrenceAscending() {
            // Arrange
            RecurringTransaction recurring1 =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            recurring1.setNextOccurrence(LocalDate.now().plusDays(10));

            RecurringTransaction recurring2 =
                    createBiweeklySalary(user1, account1User1, categoryIncome);
            recurring2.setNextOccurrence(LocalDate.now().plusDays(5));

            RecurringTransaction recurring3 =
                    createWeeklySubscription(user1, account1User1, categoryExpense);
            recurring3.setNextOccurrence(LocalDate.now().plusDays(15));

            recurringTransactionRepository.saveAll(List.of(recurring1, recurring2, recurring3));

            // Act
            List<RecurringTransaction> orderedRecurring =
                    recurringTransactionRepository.findByUserId(user1.getId());

            // Assert
            assertThat(orderedRecurring).hasSize(3);
            assertThat(orderedRecurring.get(0).getNextOccurrence())
                    .isEqualTo(LocalDate.now().plusDays(5));
            assertThat(orderedRecurring.get(1).getNextOccurrence())
                    .isEqualTo(LocalDate.now().plusDays(10));
            assertThat(orderedRecurring.get(2).getNextOccurrence())
                    .isEqualTo(LocalDate.now().plusDays(15));
        }

        @Test
        @DisplayName("findEndingSoon() should order by endDate ASC")
        void shouldOrderEndingSoonByEndDateAscending() {
            // Arrange
            LocalDate today = LocalDate.now();

            RecurringTransaction recurring1 =
                    createMonthlyRent(user1, account1User1, categoryExpense);
            recurring1.setEndDate(today.plusDays(20));
            recurring1.setIsActive(true);

            RecurringTransaction recurring2 =
                    createBiweeklySalary(user1, account1User1, categoryIncome);
            recurring2.setEndDate(today.plusDays(10));
            recurring2.setIsActive(true);

            recurringTransactionRepository.saveAll(List.of(recurring1, recurring2));

            // Act
            List<RecurringTransaction> endingSoonList =
                    recurringTransactionRepository.findEndingSoon(
                            user1.getId(), today, today.plusDays(30));

            // Assert
            assertThat(endingSoonList).hasSize(2);
            assertThat(endingSoonList.get(0).getEndDate()).isEqualTo(today.plusDays(10));
            assertThat(endingSoonList.get(1).getEndDate()).isEqualTo(today.plusDays(20));
        }
    }

    // ========== HELPER METHODS ==========

    private RecurringTransaction createMonthlyRent(User user, Account account, Category category) {
        return RecurringTransaction.builder()
                .userId(user.getId())
                .accountId(account.getId())
                .type(TransactionType.EXPENSE)
                .amount(new BigDecimal("1500.00"))
                .currency("USD")
                .categoryId(category.getId())
                .description("encrypted-Monthly Rent")
                .notes("encrypted-Rent for apartment 123")
                .frequency(RecurringFrequency.MONTHLY)
                .nextOccurrence(LocalDate.now().plusDays(1))
                .endDate(null)
                .isActive(true)
                .build();
    }

    private RecurringTransaction createBiweeklySalary(
            User user, Account account, Category category) {
        return RecurringTransaction.builder()
                .userId(user.getId())
                .accountId(account.getId())
                .type(TransactionType.INCOME)
                .amount(new BigDecimal("2500.00"))
                .currency("USD")
                .categoryId(category.getId())
                .description("encrypted-Biweekly Paycheck")
                .frequency(RecurringFrequency.BIWEEKLY)
                .nextOccurrence(LocalDate.now().plusDays(1))
                .endDate(null)
                .isActive(true)
                .build();
    }

    private RecurringTransaction createWeeklySubscription(
            User user, Account account, Category category) {
        return RecurringTransaction.builder()
                .userId(user.getId())
                .accountId(account.getId())
                .type(TransactionType.EXPENSE)
                .amount(new BigDecimal("15.00"))
                .currency("USD")
                .categoryId(category.getId())
                .description("encrypted-Weekly Subscription")
                .frequency(RecurringFrequency.WEEKLY)
                .nextOccurrence(LocalDate.now().plusDays(1))
                .endDate(null)
                .isActive(true)
                .build();
    }

    private RecurringTransaction createMonthlySubscription(
            User user, Account account, Category category) {
        return RecurringTransaction.builder()
                .userId(user.getId())
                .accountId(account.getId())
                .type(TransactionType.EXPENSE)
                .amount(new BigDecimal("20.00"))
                .currency("USD")
                .categoryId(category.getId())
                .description("encrypted-Netflix Subscription")
                .frequency(RecurringFrequency.MONTHLY)
                .nextOccurrence(LocalDate.now().plusDays(1))
                .endDate(null)
                .isActive(true)
                .build();
    }

    private RecurringTransaction createTransfer(User user, Account fromAccount, Account toAccount) {
        return RecurringTransaction.builder()
                .userId(user.getId())
                .accountId(fromAccount.getId())
                .toAccountId(toAccount.getId())
                .type(TransactionType.TRANSFER)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .categoryId(null)
                .description("encrypted-Weekly Savings Transfer")
                .frequency(RecurringFrequency.WEEKLY)
                .nextOccurrence(LocalDate.now().plusDays(1))
                .endDate(null)
                .isActive(true)
                .build();
    }
}
