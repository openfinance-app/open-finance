package org.openfinance.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetPeriod;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for BudgetRepository.
 *
 * <p>Uses @DataJpaTest which configures an in-memory H2 database and provides transactional test
 * execution with rollback.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>CRUD operations
 *   <li>User isolation queries
 *   <li>Period filtering (WEEKLY, MONTHLY, QUARTERLY, YEARLY)
 *   <li>Category-based queries
 *   <li>Active budget queries (date range filtering)
 *   <li>Rollover budget queries
 *   <li>Count and existence checks
 * </ul>
 *
 * <p>Requirement REQ-2.9.1.1: Budget creation and management
 *
 * <p>Requirement REQ-2.9.1.2: Budget tracking by period and category
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
        })
@DisplayName("BudgetRepository Integration Tests")
class BudgetRepositoryTest {

    @Autowired private BudgetRepository budgetRepository;

    @Autowired private CategoryRepository categoryRepository;

    @Autowired private UserRepository userRepository;

    private User testUser1;
    private User testUser2;
    private Category groceriesCategory;
    private Category diningCategory;
    private Budget monthlyGroceriesBudget;
    private Budget weeklyDiningBudget;
    private Budget quarterlyBudget;

    @BeforeEach
    void setUp() {
        // Clear repositories before each test
        budgetRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // Create test users
        testUser1 =
                User.builder()
                        .username("testuser1")
                        .email("user1@example.com")
                        .passwordHash("$2a$10$hashedPasswordExample123456789")
                        .masterPasswordSalt("base64EncodedSaltExample==")
                        .build();
        testUser1 = userRepository.save(testUser1);

        testUser2 =
                User.builder()
                        .username("testuser2")
                        .email("user2@example.com")
                        .passwordHash("$2a$10$hashedPasswordExample987654321")
                        .masterPasswordSalt("base64EncodedSaltExample22==")
                        .build();
        testUser2 = userRepository.save(testUser2);

        // Create test categories
        groceriesCategory =
                Category.builder()
                        .userId(testUser1.getId())
                        .name("Groceries")
                        .type(CategoryType.EXPENSE)
                        .icon("🛒")
                        .color("#EF4444")
                        .isSystem(false)
                        .build();
        groceriesCategory = categoryRepository.save(groceriesCategory);

        diningCategory =
                Category.builder()
                        .userId(testUser1.getId())
                        .name("Dining Out")
                        .type(CategoryType.EXPENSE)
                        .icon("🍽️")
                        .color("#F59E0B")
                        .isSystem(false)
                        .build();
        diningCategory = categoryRepository.save(diningCategory);

        // Create test budgets
        // Monthly budget for February 2026
        monthlyGroceriesBudget =
                Budget.builder()
                        .userId(testUser1.getId())
                        .categoryId(groceriesCategory.getId())
                        .amount("encryptedAmount500") // In real usage, this would be encrypted
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 28))
                        .rollover(false)
                        .notes("Monthly grocery budget")
                        .build();

        // Weekly budget for current week
        weeklyDiningBudget =
                Budget.builder()
                        .userId(testUser1.getId())
                        .categoryId(diningCategory.getId())
                        .amount("encryptedAmount150") // In real usage, this would be encrypted
                        .currency("USD")
                        .period(BudgetPeriod.WEEKLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 7))
                        .rollover(true)
                        .notes("Weekly dining budget with rollover")
                        .build();

        // Quarterly budget (Q1 2026)
        quarterlyBudget =
                Budget.builder()
                        .userId(testUser1.getId())
                        .categoryId(groceriesCategory.getId())
                        .amount("encryptedAmount1500") // In real usage, this would be encrypted
                        .currency("USD")
                        .period(BudgetPeriod.QUARTERLY)
                        .startDate(LocalDate.of(2026, 1, 1))
                        .endDate(LocalDate.of(2026, 3, 31))
                        .rollover(false)
                        .notes("Q1 grocery budget")
                        .build();
    }

    // === CRUD Operation Tests ===

    @Test
    @DisplayName("Should save budget and generate ID")
    void shouldSaveBudgetAndGenerateId() {
        // When
        Budget savedBudget = budgetRepository.save(monthlyGroceriesBudget);

        // Then
        assertThat(savedBudget).isNotNull();
        assertThat(savedBudget.getId()).isNotNull();
        assertThat(savedBudget.getUserId()).isEqualTo(testUser1.getId());
        assertThat(savedBudget.getCategoryId()).isEqualTo(groceriesCategory.getId());
        assertThat(savedBudget.getAmount()).isEqualTo("encryptedAmount500");
        assertThat(savedBudget.getPeriod()).isEqualTo(BudgetPeriod.MONTHLY);
        assertThat(savedBudget.getStartDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(savedBudget.getEndDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(savedBudget.getRollover()).isFalse();
        assertThat(savedBudget.getNotes()).isEqualTo("Monthly grocery budget");
        assertThat(savedBudget.getCreatedAt()).isNotNull();
        assertThat(savedBudget.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should find budget by ID")
    void shouldFindBudgetById() {
        // Given
        Budget savedBudget = budgetRepository.save(monthlyGroceriesBudget);

        // When
        Optional<Budget> found = budgetRepository.findById(savedBudget.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedBudget.getId());
        assertThat(found.get().getAmount()).isEqualTo("encryptedAmount500");
    }

    @Test
    @DisplayName("Should update budget")
    void shouldUpdateBudget() {
        // Given
        Budget savedBudget = budgetRepository.save(monthlyGroceriesBudget);

        // When
        savedBudget.setAmount("encryptedAmount600");
        savedBudget.setRollover(true);
        savedBudget.setNotes("Updated budget with rollover");
        Budget updatedBudget = budgetRepository.save(savedBudget);

        // Then
        assertThat(updatedBudget.getAmount()).isEqualTo("encryptedAmount600");
        assertThat(updatedBudget.getRollover()).isTrue();
        assertThat(updatedBudget.getNotes()).isEqualTo("Updated budget with rollover");
        assertThat(updatedBudget.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should delete budget")
    void shouldDeleteBudget() {
        // Given
        Budget savedBudget = budgetRepository.save(monthlyGroceriesBudget);
        Long budgetId = savedBudget.getId();

        // When
        budgetRepository.delete(savedBudget);

        // Then
        Optional<Budget> found = budgetRepository.findById(budgetId);
        assertThat(found).isEmpty();
    }

    // === User Isolation Tests ===

    @Test
    @DisplayName("Should find all budgets by user ID")
    void shouldFindAllBudgetsByUserId() {
        // Given
        budgetRepository.save(monthlyGroceriesBudget);
        budgetRepository.save(weeklyDiningBudget);

        Category user2Category =
                Category.builder()
                        .userId(testUser2.getId())
                        .name("User2 Category")
                        .type(CategoryType.EXPENSE)
                        .icon("💰")
                        .color("#10B981")
                        .isSystem(false)
                        .build();
        user2Category = categoryRepository.save(user2Category);

        Budget user2Budget =
                Budget.builder()
                        .userId(testUser2.getId())
                        .categoryId(user2Category.getId())
                        .amount("encryptedAmount300")
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 28))
                        .rollover(false)
                        .build();
        budgetRepository.save(user2Budget);

        // When
        List<Budget> user1Budgets = budgetRepository.findByUserId(testUser1.getId());
        List<Budget> user2Budgets = budgetRepository.findByUserId(testUser2.getId());

        // Then
        assertThat(user1Budgets).hasSize(2);
        assertThat(user1Budgets).extracting("userId").containsOnly(testUser1.getId());
        assertThat(user2Budgets).hasSize(1);
        assertThat(user2Budgets).extracting("userId").containsOnly(testUser2.getId());
    }

    @Test
    @DisplayName("Should find budget by ID and user ID")
    void shouldFindBudgetByIdAndUserId() {
        // Given
        Budget savedBudget = budgetRepository.save(monthlyGroceriesBudget);

        // When
        Optional<Budget> found =
                budgetRepository.findByIdAndUserId(savedBudget.getId(), testUser1.getId());
        Optional<Budget> notFound =
                budgetRepository.findByIdAndUserId(savedBudget.getId(), testUser2.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedBudget.getId());
        assertThat(notFound).isEmpty(); // User 2 cannot access User 1's budget
    }

    // === Period Filtering Tests ===

    @Test
    @DisplayName("Should find budgets by user ID and period")
    void shouldFindBudgetsByUserIdAndPeriod() {
        // Given
        budgetRepository.save(monthlyGroceriesBudget);
        budgetRepository.save(weeklyDiningBudget);
        budgetRepository.save(quarterlyBudget);

        // When
        List<Budget> monthlyBudgets =
                budgetRepository.findByUserIdAndPeriod(testUser1.getId(), BudgetPeriod.MONTHLY);
        List<Budget> weeklyBudgets =
                budgetRepository.findByUserIdAndPeriod(testUser1.getId(), BudgetPeriod.WEEKLY);
        List<Budget> quarterlyBudgets =
                budgetRepository.findByUserIdAndPeriod(testUser1.getId(), BudgetPeriod.QUARTERLY);
        List<Budget> yearlyBudgets =
                budgetRepository.findByUserIdAndPeriod(testUser1.getId(), BudgetPeriod.YEARLY);

        // Then
        assertThat(monthlyBudgets).hasSize(1);
        assertThat(monthlyBudgets.get(0).getPeriod()).isEqualTo(BudgetPeriod.MONTHLY);
        assertThat(weeklyBudgets).hasSize(1);
        assertThat(weeklyBudgets.get(0).getPeriod()).isEqualTo(BudgetPeriod.WEEKLY);
        assertThat(quarterlyBudgets).hasSize(1);
        assertThat(quarterlyBudgets.get(0).getPeriod()).isEqualTo(BudgetPeriod.QUARTERLY);
        assertThat(yearlyBudgets).isEmpty();
    }

    // === Category-Based Tests ===

    @Test
    @DisplayName("Should find budgets by category ID")
    void shouldFindBudgetsByCategoryId() {
        // Given
        budgetRepository.save(monthlyGroceriesBudget);
        budgetRepository.save(quarterlyBudget); // Also groceries category
        budgetRepository.save(weeklyDiningBudget); // Different category

        // When
        List<Budget> groceriesBudgets =
                budgetRepository.findByCategoryId(groceriesCategory.getId());
        List<Budget> diningBudgets = budgetRepository.findByCategoryId(diningCategory.getId());

        // Then
        assertThat(groceriesBudgets).hasSize(2);
        assertThat(groceriesBudgets)
                .extracting("categoryId")
                .containsOnly(groceriesCategory.getId());
        assertThat(diningBudgets).hasSize(1);
        assertThat(diningBudgets).extracting("categoryId").containsOnly(diningCategory.getId());
    }

    @Test
    @DisplayName("Should find budgets by user ID and category ID")
    void shouldFindBudgetsByUserIdAndCategoryId() {
        // Given
        budgetRepository.save(monthlyGroceriesBudget);
        budgetRepository.save(quarterlyBudget); // Also groceries category
        budgetRepository.save(weeklyDiningBudget);

        // When
        List<Budget> userGroceriesBudgets =
                budgetRepository.findByUserIdAndCategoryId(
                        testUser1.getId(), groceriesCategory.getId());

        // Then
        assertThat(userGroceriesBudgets).hasSize(2);
        assertThat(userGroceriesBudgets).extracting("userId").containsOnly(testUser1.getId());
        assertThat(userGroceriesBudgets)
                .extracting("categoryId")
                .containsOnly(groceriesCategory.getId());
    }

    // === Active Budget Tests (Date Range Filtering) ===

    @Test
    @DisplayName("Should find active budgets by user ID and date")
    void shouldFindActiveBudgetsByUserIdAndDate() {
        // Given
        budgetRepository.save(monthlyGroceriesBudget); // Feb 1-28
        budgetRepository.save(weeklyDiningBudget); // Feb 1-7
        budgetRepository.save(quarterlyBudget); // Jan 1 - Mar 31

        // When - Check Feb 5, 2026 (should find all three)
        List<Budget> activeBudgetsFeb5 =
                budgetRepository.findActiveByUserIdAndDate(
                        testUser1.getId(), LocalDate.of(2026, 2, 5));

        // When - Check Feb 10, 2026 (should find monthly and quarterly, not weekly)
        List<Budget> activeBudgetsFeb10 =
                budgetRepository.findActiveByUserIdAndDate(
                        testUser1.getId(), LocalDate.of(2026, 2, 10));

        // When - Check Dec 15, 2025 (should find none)
        List<Budget> activeBudgetsDec =
                budgetRepository.findActiveByUserIdAndDate(
                        testUser1.getId(), LocalDate.of(2025, 12, 15));

        // Then
        assertThat(activeBudgetsFeb5).hasSize(3);
        assertThat(activeBudgetsFeb10).hasSize(2); // Weekly budget expired
        assertThat(activeBudgetsFeb10)
                .extracting("period")
                .containsExactlyInAnyOrder(BudgetPeriod.MONTHLY, BudgetPeriod.QUARTERLY);
        assertThat(activeBudgetsDec).isEmpty();
    }

    @Test
    @DisplayName("Should find active budgets by user ID, period, and date")
    void shouldFindActiveBudgetsByUserIdAndPeriodAndDate() {
        // Given
        budgetRepository.save(monthlyGroceriesBudget);
        budgetRepository.save(weeklyDiningBudget);
        budgetRepository.save(quarterlyBudget);

        // When - Find active monthly budgets on Feb 5
        List<Budget> activeMonthly =
                budgetRepository.findActiveByUserIdAndPeriodAndDate(
                        testUser1.getId(), BudgetPeriod.MONTHLY, LocalDate.of(2026, 2, 5));

        // When - Find active weekly budgets on Feb 5
        List<Budget> activeWeekly =
                budgetRepository.findActiveByUserIdAndPeriodAndDate(
                        testUser1.getId(), BudgetPeriod.WEEKLY, LocalDate.of(2026, 2, 5));

        // Then
        assertThat(activeMonthly).hasSize(1);
        assertThat(activeMonthly.get(0).getPeriod()).isEqualTo(BudgetPeriod.MONTHLY);
        assertThat(activeWeekly).hasSize(1);
        assertThat(activeWeekly.get(0).getPeriod()).isEqualTo(BudgetPeriod.WEEKLY);
    }

    // === Budget Lookup Tests ===

    @Test
    @DisplayName("Should find budget by user, category, and period")
    void shouldFindBudgetByUserIdAndCategoryIdAndPeriod() {
        // Given
        budgetRepository.save(monthlyGroceriesBudget);
        budgetRepository.save(quarterlyBudget); // Same category, different period

        // When
        Optional<Budget> monthlyBudget =
                budgetRepository.findByUserIdAndCategoryIdAndPeriod(
                        testUser1.getId(), groceriesCategory.getId(), BudgetPeriod.MONTHLY);
        Optional<Budget> quarterlyFound =
                budgetRepository.findByUserIdAndCategoryIdAndPeriod(
                        testUser1.getId(), groceriesCategory.getId(), BudgetPeriod.QUARTERLY);
        Optional<Budget> weeklyNotFound =
                budgetRepository.findByUserIdAndCategoryIdAndPeriod(
                        testUser1.getId(), groceriesCategory.getId(), BudgetPeriod.WEEKLY);

        // Then
        assertThat(monthlyBudget).isPresent();
        assertThat(monthlyBudget.get().getPeriod()).isEqualTo(BudgetPeriod.MONTHLY);
        assertThat(quarterlyFound).isPresent();
        assertThat(quarterlyFound.get().getPeriod()).isEqualTo(BudgetPeriod.QUARTERLY);
        assertThat(weeklyNotFound).isEmpty();
    }

    // === Rollover Budget Tests ===

    @Test
    @DisplayName("Should find rollover budgets by user ID")
    void shouldFindRolloverBudgetsByUserId() {
        // Given
        budgetRepository.save(monthlyGroceriesBudget); // rollover = false
        budgetRepository.save(weeklyDiningBudget); // rollover = true

        Budget anotherRolloverBudget =
                Budget.builder()
                        .userId(testUser1.getId())
                        .categoryId(diningCategory.getId())
                        .amount("encryptedAmount200")
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 3, 1))
                        .endDate(LocalDate.of(2026, 3, 31))
                        .rollover(true)
                        .build();
        budgetRepository.save(anotherRolloverBudget);

        // When
        List<Budget> rolloverBudgets =
                budgetRepository.findRolloverBudgetsByUserId(testUser1.getId());

        // Then
        assertThat(rolloverBudgets).hasSize(2);
        assertThat(rolloverBudgets).extracting("rollover").containsOnly(true);
    }

    // === Expired Budget Tests ===

    @Test
    @DisplayName("Should find expired budgets by user ID")
    void shouldFindExpiredBudgetsByUserId() {
        // Given
        budgetRepository.save(monthlyGroceriesBudget); // Ends Feb 28
        budgetRepository.save(weeklyDiningBudget); // Ends Feb 7
        budgetRepository.save(quarterlyBudget); // Ends Mar 31

        // When - Check for expired budgets as of Mar 1, 2026
        List<Budget> expiredBudgets =
                budgetRepository.findExpiredBudgetsByUserId(
                        testUser1.getId(), LocalDate.of(2026, 3, 1));

        // Then - Monthly (Feb 28) and Weekly (Feb 7) should be expired
        assertThat(expiredBudgets).hasSize(2);
        assertThat(expiredBudgets)
                .extracting("period")
                .containsExactlyInAnyOrder(BudgetPeriod.MONTHLY, BudgetPeriod.WEEKLY);
    }

    // === Count Tests ===

    @Test
    @DisplayName("Should count budgets by user ID")
    void shouldCountBudgetsByUserId() {
        // Given
        budgetRepository.save(monthlyGroceriesBudget);
        budgetRepository.save(weeklyDiningBudget);
        budgetRepository.save(quarterlyBudget);

        // When
        Long count = budgetRepository.countByUserId(testUser1.getId());
        Long user2Count = budgetRepository.countByUserId(testUser2.getId());

        // Then
        assertThat(count).isEqualTo(3L);
        assertThat(user2Count).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should count active budgets by user ID and date")
    void shouldCountActiveBudgetsByUserIdAndDate() {
        // Given
        budgetRepository.save(monthlyGroceriesBudget);
        budgetRepository.save(weeklyDiningBudget);
        budgetRepository.save(quarterlyBudget);

        // When
        Long countFeb5 =
                budgetRepository.countActiveByUserIdAndDate(
                        testUser1.getId(), LocalDate.of(2026, 2, 5));
        Long countFeb10 =
                budgetRepository.countActiveByUserIdAndDate(
                        testUser1.getId(), LocalDate.of(2026, 2, 10));

        // Then
        assertThat(countFeb5).isEqualTo(3L); // All active
        assertThat(countFeb10).isEqualTo(2L); // Weekly expired
    }

    @Test
    @DisplayName("Should count budgets by user ID and period")
    void shouldCountBudgetsByUserIdAndPeriod() {
        // Given
        budgetRepository.save(monthlyGroceriesBudget);
        budgetRepository.save(weeklyDiningBudget);
        budgetRepository.save(quarterlyBudget);

        // When
        Long monthlyCount =
                budgetRepository.countByUserIdAndPeriod(testUser1.getId(), BudgetPeriod.MONTHLY);
        Long weeklyCount =
                budgetRepository.countByUserIdAndPeriod(testUser1.getId(), BudgetPeriod.WEEKLY);
        Long yearlyCount =
                budgetRepository.countByUserIdAndPeriod(testUser1.getId(), BudgetPeriod.YEARLY);

        // Then
        assertThat(monthlyCount).isEqualTo(1L);
        assertThat(weeklyCount).isEqualTo(1L);
        assertThat(yearlyCount).isEqualTo(0L);
    }

    // === Existence Tests ===

    @Test
    @DisplayName("Should check if budget exists by user, category, and period")
    void shouldCheckBudgetExistsByUserIdAndCategoryIdAndPeriod() {
        // Given
        budgetRepository.save(monthlyGroceriesBudget);

        // When
        boolean existsMonthly =
                budgetRepository.existsByUserIdAndCategoryIdAndPeriod(
                        testUser1.getId(), groceriesCategory.getId(), BudgetPeriod.MONTHLY);
        boolean existsWeekly =
                budgetRepository.existsByUserIdAndCategoryIdAndPeriod(
                        testUser1.getId(), groceriesCategory.getId(), BudgetPeriod.WEEKLY);

        // Then
        assertThat(existsMonthly).isTrue();
        assertThat(existsWeekly).isFalse();
    }

    // === Edge Case Tests ===

    @Test
    @DisplayName("Should return empty list when no budgets exist for user")
    void shouldReturnEmptyListWhenNoBudgetsExistForUser() {
        // When
        List<Budget> budgets = budgetRepository.findByUserId(testUser1.getId());

        // Then
        assertThat(budgets).isEmpty();
    }

    @Test
    @DisplayName("Should handle budget on boundary dates")
    void shouldHandleBudgetOnBoundaryDates() {
        // Given
        budgetRepository.save(monthlyGroceriesBudget); // Feb 1-28

        // When - Check start date
        List<Budget> activeOnStart =
                budgetRepository.findActiveByUserIdAndDate(
                        testUser1.getId(), LocalDate.of(2026, 2, 1));

        // When - Check end date
        List<Budget> activeOnEnd =
                budgetRepository.findActiveByUserIdAndDate(
                        testUser1.getId(), LocalDate.of(2026, 2, 28));

        // When - Check day before start
        List<Budget> activeBeforeStart =
                budgetRepository.findActiveByUserIdAndDate(
                        testUser1.getId(), LocalDate.of(2026, 1, 31));

        // When - Check day after end
        List<Budget> activeAfterEnd =
                budgetRepository.findActiveByUserIdAndDate(
                        testUser1.getId(), LocalDate.of(2026, 3, 1));

        // Then
        assertThat(activeOnStart).hasSize(1); // Inclusive
        assertThat(activeOnEnd).hasSize(1); // Inclusive
        assertThat(activeBeforeStart).isEmpty();
        assertThat(activeAfterEnd).isEmpty();
    }

    @Test
    @DisplayName("Should order budgets by start date descending")
    void shouldOrderBudgetsByStartDateDescending() {
        // Given
        budgetRepository.save(weeklyDiningBudget); // Feb 1-7
        budgetRepository.save(quarterlyBudget); // Jan 1 - Mar 31
        budgetRepository.save(monthlyGroceriesBudget); // Feb 1-28

        // When
        List<Budget> budgets = budgetRepository.findByUserId(testUser1.getId());

        // Then - Should be ordered by start date DESC (most recent first)
        assertThat(budgets).hasSize(3);
        assertThat(budgets.get(0).getStartDate())
                .isEqualTo(LocalDate.of(2026, 2, 1)); // Monthly or Weekly
        assertThat(budgets.get(2).getStartDate())
                .isEqualTo(LocalDate.of(2026, 1, 1)); // Quarterly (oldest)
    }
}
