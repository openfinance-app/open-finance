package org.openfinance.entity;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RecurringTransaction entity.
 *
 * <p>Tests entity helper methods for due checking, end date validation, next occurrence calculation
 * for all frequencies, and transfer detection.
 *
 * <p>Requirements: REQ-2.3.6 - Recurring transactions with configurable frequency
 */
@DisplayName("RecurringTransaction Entity Tests")
class RecurringTransactionTest {

    // ========== IS DUE TESTS ==========

    @Nested
    @DisplayName("isDue() Tests")
    class IsDueTests {

        @Test
        @DisplayName("Should return true when active and next occurrence is today")
        void shouldReturnTrueWhenActiveAndNextOccurrenceIsToday() {
            // Arrange
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .isActive(true)
                            .nextOccurrence(LocalDate.now())
                            .endDate(null)
                            .build();

            // Act & Assert
            assertThat(recurring.isDue()).isTrue();
        }

        @Test
        @DisplayName("Should return true when active and next occurrence is in the past")
        void shouldReturnTrueWhenActiveAndNextOccurrenceIsPast() {
            // Arrange
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .isActive(true)
                            .nextOccurrence(LocalDate.now().minusDays(5))
                            .endDate(null)
                            .build();

            // Act & Assert
            assertThat(recurring.isDue()).isTrue();
        }

        @Test
        @DisplayName("Should return false when inactive even if next occurrence is today")
        void shouldReturnFalseWhenInactive() {
            // Arrange
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .isActive(false)
                            .nextOccurrence(LocalDate.now())
                            .endDate(null)
                            .build();

            // Act & Assert
            assertThat(recurring.isDue()).isFalse();
        }

        @Test
        @DisplayName("Should return false when next occurrence is in the future")
        void shouldReturnFalseWhenNextOccurrenceIsFuture() {
            // Arrange
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .isActive(true)
                            .nextOccurrence(LocalDate.now().plusDays(5))
                            .endDate(null)
                            .build();

            // Act & Assert
            assertThat(recurring.isDue()).isFalse();
        }

        @Test
        @DisplayName("Should return false when end date is in the past")
        void shouldReturnFalseWhenEndDateIsPast() {
            // Arrange
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .isActive(true)
                            .nextOccurrence(LocalDate.now().minusDays(5))
                            .endDate(LocalDate.now().minusDays(10))
                            .build();

            // Act & Assert
            assertThat(recurring.isDue()).isFalse();
        }

        @Test
        @DisplayName("Should return true when end date is today")
        void shouldReturnTrueWhenEndDateIsToday() {
            // Arrange
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .isActive(true)
                            .nextOccurrence(LocalDate.now())
                            .endDate(LocalDate.now())
                            .build();

            // Act & Assert
            assertThat(recurring.isDue()).isTrue();
        }

        @Test
        @DisplayName("Should return true when end date is in the future")
        void shouldReturnTrueWhenEndDateIsFuture() {
            // Arrange
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .isActive(true)
                            .nextOccurrence(LocalDate.now())
                            .endDate(LocalDate.now().plusMonths(6))
                            .build();

            // Act & Assert
            assertThat(recurring.isDue()).isTrue();
        }

        @Test
        @DisplayName("Should return true when end date is null (indefinite)")
        void shouldReturnTrueWhenEndDateIsNull() {
            // Arrange
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .isActive(true)
                            .nextOccurrence(LocalDate.now())
                            .endDate(null)
                            .build();

            // Act & Assert
            assertThat(recurring.isDue()).isTrue();
        }
    }

    // ========== IS ENDED TESTS ==========

    @Nested
    @DisplayName("isEnded() Tests")
    class IsEndedTests {

        @Test
        @DisplayName("Should return true when end date is in the past")
        void shouldReturnTrueWhenEndDateIsPast() {
            // Arrange
            RecurringTransaction recurring =
                    RecurringTransaction.builder().endDate(LocalDate.now().minusDays(10)).build();

            // Act & Assert
            assertThat(recurring.isEnded()).isTrue();
        }

        @Test
        @DisplayName("Should return false when end date is today")
        void shouldReturnFalseWhenEndDateIsToday() {
            // Arrange
            RecurringTransaction recurring =
                    RecurringTransaction.builder().endDate(LocalDate.now()).build();

            // Act & Assert
            assertThat(recurring.isEnded()).isFalse();
        }

        @Test
        @DisplayName("Should return false when end date is in the future")
        void shouldReturnFalseWhenEndDateIsFuture() {
            // Arrange
            RecurringTransaction recurring =
                    RecurringTransaction.builder().endDate(LocalDate.now().plusMonths(6)).build();

            // Act & Assert
            assertThat(recurring.isEnded()).isFalse();
        }

        @Test
        @DisplayName("Should return false when end date is null (indefinite)")
        void shouldReturnFalseWhenEndDateIsNull() {
            // Arrange
            RecurringTransaction recurring = RecurringTransaction.builder().endDate(null).build();

            // Act & Assert
            assertThat(recurring.isEnded()).isFalse();
        }
    }

    // ========== CALCULATE NEXT OCCURRENCE TESTS ==========

    @Nested
    @DisplayName("calculateNextOccurrence() Tests")
    class CalculateNextOccurrenceTests {

        @Test
        @DisplayName("Should add 1 day for DAILY frequency")
        void shouldAddOneDayForDaily() {
            // Arrange
            LocalDate startDate = LocalDate.of(2025, 1, 15);
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .frequency(RecurringFrequency.DAILY)
                            .nextOccurrence(startDate)
                            .build();

            // Act
            LocalDate nextDate = recurring.calculateNextOccurrence();

            // Assert
            assertThat(nextDate).isEqualTo(LocalDate.of(2025, 1, 16));
        }

        @Test
        @DisplayName("Should add 7 days for WEEKLY frequency")
        void shouldAddSevenDaysForWeekly() {
            // Arrange
            LocalDate startDate = LocalDate.of(2025, 1, 15);
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .frequency(RecurringFrequency.WEEKLY)
                            .nextOccurrence(startDate)
                            .build();

            // Act
            LocalDate nextDate = recurring.calculateNextOccurrence();

            // Assert
            assertThat(nextDate).isEqualTo(LocalDate.of(2025, 1, 22));
        }

        @Test
        @DisplayName("Should add 14 days for BIWEEKLY frequency")
        void shouldAddFourteenDaysForBiweekly() {
            // Arrange
            LocalDate startDate = LocalDate.of(2025, 1, 15);
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .frequency(RecurringFrequency.BIWEEKLY)
                            .nextOccurrence(startDate)
                            .build();

            // Act
            LocalDate nextDate = recurring.calculateNextOccurrence();

            // Assert
            assertThat(nextDate).isEqualTo(LocalDate.of(2025, 1, 29));
        }

        @Test
        @DisplayName("Should add 1 month for MONTHLY frequency")
        void shouldAddOneMonthForMonthly() {
            // Arrange
            LocalDate startDate = LocalDate.of(2025, 1, 15);
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .frequency(RecurringFrequency.MONTHLY)
                            .nextOccurrence(startDate)
                            .build();

            // Act
            LocalDate nextDate = recurring.calculateNextOccurrence();

            // Assert
            assertThat(nextDate).isEqualTo(LocalDate.of(2025, 2, 15));
        }

        @Test
        @DisplayName("Should handle month-end edge case for MONTHLY frequency (Jan 31 -> Feb 28)")
        void shouldHandleMonthEndEdgeCaseForMonthly() {
            // Arrange - January 31 (non-leap year 2025)
            LocalDate startDate = LocalDate.of(2025, 1, 31);
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .frequency(RecurringFrequency.MONTHLY)
                            .nextOccurrence(startDate)
                            .build();

            // Act
            LocalDate nextDate = recurring.calculateNextOccurrence();

            // Assert - Should be February 28 (2025 is not a leap year)
            assertThat(nextDate).isEqualTo(LocalDate.of(2025, 2, 28));
        }

        @Test
        @DisplayName("Should handle leap year for MONTHLY frequency (Jan 31 -> Feb 29)")
        void shouldHandleLeapYearForMonthly() {
            // Arrange - January 31 (leap year 2024)
            LocalDate startDate = LocalDate.of(2024, 1, 31);
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .frequency(RecurringFrequency.MONTHLY)
                            .nextOccurrence(startDate)
                            .build();

            // Act
            LocalDate nextDate = recurring.calculateNextOccurrence();

            // Assert - Should be February 29 (2024 is a leap year)
            assertThat(nextDate).isEqualTo(LocalDate.of(2024, 2, 29));
        }

        @Test
        @DisplayName("Should add 3 months for QUARTERLY frequency")
        void shouldAddThreeMonthsForQuarterly() {
            // Arrange
            LocalDate startDate = LocalDate.of(2025, 1, 15);
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .frequency(RecurringFrequency.QUARTERLY)
                            .nextOccurrence(startDate)
                            .build();

            // Act
            LocalDate nextDate = recurring.calculateNextOccurrence();

            // Assert
            assertThat(nextDate).isEqualTo(LocalDate.of(2025, 4, 15));
        }

        @Test
        @DisplayName("Should add 1 year for YEARLY frequency")
        void shouldAddOneYearForYearly() {
            // Arrange
            LocalDate startDate = LocalDate.of(2025, 1, 15);
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .frequency(RecurringFrequency.YEARLY)
                            .nextOccurrence(startDate)
                            .build();

            // Act
            LocalDate nextDate = recurring.calculateNextOccurrence();

            // Assert
            assertThat(nextDate).isEqualTo(LocalDate.of(2026, 1, 15));
        }

        @Test
        @DisplayName("Should handle leap year Feb 29 -> Mar 1 for YEARLY frequency")
        void shouldHandleLeapYearFeb29ForYearly() {
            // Arrange - February 29, 2024 (leap year)
            LocalDate startDate = LocalDate.of(2024, 2, 29);
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .frequency(RecurringFrequency.YEARLY)
                            .nextOccurrence(startDate)
                            .build();

            // Act
            LocalDate nextDate = recurring.calculateNextOccurrence();

            // Assert - Should be February 28, 2025 (non-leap year)
            assertThat(nextDate).isEqualTo(LocalDate.of(2025, 2, 28));
        }
    }

    // ========== IS TRANSFER TESTS ==========

    @Nested
    @DisplayName("isTransfer() Tests")
    class IsTransferTests {

        @Test
        @DisplayName("Should return true when type is TRANSFER")
        void shouldReturnTrueWhenTypeIsTransfer() {
            // Arrange
            RecurringTransaction recurring =
                    RecurringTransaction.builder().type(TransactionType.TRANSFER).build();

            // Act & Assert
            assertThat(recurring.isTransfer()).isTrue();
        }

        @Test
        @DisplayName("Should return false when type is INCOME")
        void shouldReturnFalseWhenTypeIsIncome() {
            // Arrange
            RecurringTransaction recurring =
                    RecurringTransaction.builder().type(TransactionType.INCOME).build();

            // Act & Assert
            assertThat(recurring.isTransfer()).isFalse();
        }

        @Test
        @DisplayName("Should return false when type is EXPENSE")
        void shouldReturnFalseWhenTypeIsExpense() {
            // Arrange
            RecurringTransaction recurring =
                    RecurringTransaction.builder().type(TransactionType.EXPENSE).build();

            // Act & Assert
            assertThat(recurring.isTransfer()).isFalse();
        }
    }

    // ========== BUILDER PATTERN TESTS ==========

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("Should build recurring transaction with all fields")
        void shouldBuildRecurringTransactionWithAllFields() {
            // Arrange & Act
            LocalDate nextOccurrence = LocalDate.of(2025, 2, 1);
            LocalDate endDate = LocalDate.of(2025, 12, 31);

            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .id(1L)
                            .userId(100L)
                            .accountId(200L)
                            .toAccountId(null)
                            .type(TransactionType.EXPENSE)
                            .amount(new BigDecimal("1500.00"))
                            .currency("USD")
                            .categoryId(50L)
                            .description("Monthly Rent")
                            .notes("Rent for apartment 123")
                            .frequency(RecurringFrequency.MONTHLY)
                            .nextOccurrence(nextOccurrence)
                            .endDate(endDate)
                            .isActive(true)
                            .build();

            // Assert
            assertThat(recurring.getId()).isEqualTo(1L);
            assertThat(recurring.getUserId()).isEqualTo(100L);
            assertThat(recurring.getAccountId()).isEqualTo(200L);
            assertThat(recurring.getToAccountId()).isNull();
            assertThat(recurring.getType()).isEqualTo(TransactionType.EXPENSE);
            assertThat(recurring.getAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
            assertThat(recurring.getCurrency()).isEqualTo("USD");
            assertThat(recurring.getCategoryId()).isEqualTo(50L);
            assertThat(recurring.getDescription()).isEqualTo("Monthly Rent");
            assertThat(recurring.getNotes()).isEqualTo("Rent for apartment 123");
            assertThat(recurring.getFrequency()).isEqualTo(RecurringFrequency.MONTHLY);
            assertThat(recurring.getNextOccurrence()).isEqualTo(nextOccurrence);
            assertThat(recurring.getEndDate()).isEqualTo(endDate);
            assertThat(recurring.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("Should build recurring transaction for TRANSFER type with toAccountId")
        void shouldBuildRecurringTransactionForTransfer() {
            // Arrange & Act
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .userId(100L)
                            .accountId(200L)
                            .toAccountId(300L)
                            .type(TransactionType.TRANSFER)
                            .amount(new BigDecimal("500.00"))
                            .currency("USD")
                            .categoryId(null)
                            .description("Savings Transfer")
                            .frequency(RecurringFrequency.WEEKLY)
                            .nextOccurrence(LocalDate.now())
                            .isActive(true)
                            .build();

            // Assert
            assertThat(recurring.getType()).isEqualTo(TransactionType.TRANSFER);
            assertThat(recurring.getToAccountId()).isEqualTo(300L);
            assertThat(recurring.getCategoryId()).isNull();
            assertThat(recurring.isTransfer()).isTrue();
        }

        @Test
        @DisplayName("Should build recurring transaction for INCOME type")
        void shouldBuildRecurringTransactionForIncome() {
            // Arrange & Act
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .userId(100L)
                            .accountId(200L)
                            .type(TransactionType.INCOME)
                            .amount(new BigDecimal("5000.00"))
                            .currency("USD")
                            .categoryId(60L)
                            .description("Biweekly Paycheck")
                            .frequency(RecurringFrequency.BIWEEKLY)
                            .nextOccurrence(LocalDate.now())
                            .isActive(true)
                            .build();

            // Assert
            assertThat(recurring.getType()).isEqualTo(TransactionType.INCOME);
            assertThat(recurring.getFrequency()).isEqualTo(RecurringFrequency.BIWEEKLY);
            assertThat(recurring.isTransfer()).isFalse();
        }

        @Test
        @DisplayName("Should build recurring transaction with indefinite end date (null)")
        void shouldBuildRecurringTransactionWithIndefiniteEndDate() {
            // Arrange & Act
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .userId(100L)
                            .accountId(200L)
                            .type(TransactionType.EXPENSE)
                            .amount(new BigDecimal("20.00"))
                            .currency("USD")
                            .categoryId(70L)
                            .description("Daily Coffee")
                            .frequency(RecurringFrequency.DAILY)
                            .nextOccurrence(LocalDate.now())
                            .endDate(null)
                            .isActive(true)
                            .build();

            // Assert
            assertThat(recurring.getEndDate()).isNull();
            assertThat(recurring.isEnded()).isFalse();
        }
    }

    // ========== EQUALS AND HASHCODE TESTS ==========

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("Should be equal when IDs are the same")
        void shouldBeEqualWhenIdsAreSame() {
            RecurringTransaction recurring1 =
                    RecurringTransaction.builder().id(1L).userId(100L).description("Rent").build();

            RecurringTransaction recurring2 =
                    RecurringTransaction.builder()
                            .id(1L)
                            .userId(200L) // Different userId
                            .description("Subscription") // Different description
                            .build();

            assertThat(recurring1).isEqualTo(recurring2);
            assertThat(recurring1.hashCode()).isEqualTo(recurring2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when IDs are different")
        void shouldNotBeEqualWhenIdsAreDifferent() {
            RecurringTransaction recurring1 =
                    RecurringTransaction.builder().id(1L).description("Rent").build();

            RecurringTransaction recurring2 =
                    RecurringTransaction.builder().id(2L).description("Rent").build();

            assertThat(recurring1).isNotEqualTo(recurring2);
        }

        @Test
        @DisplayName("Should not be equal when one ID is null")
        void shouldNotBeEqualWhenOneIdIsNull() {
            RecurringTransaction recurring1 =
                    RecurringTransaction.builder().id(1L).description("Rent").build();

            RecurringTransaction recurring2 =
                    RecurringTransaction.builder().id(null).description("Rent").build();

            assertThat(recurring1).isNotEqualTo(recurring2);
        }
    }

    // ========== TOSTRING TESTS ==========

    @Nested
    @DisplayName("toString() Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should include explicitly marked fields in toString")
        void shouldIncludeExplicitFieldsInToString() {
            RecurringTransaction recurring =
                    RecurringTransaction.builder()
                            .id(1L)
                            .userId(100L)
                            .accountId(200L)
                            .type(TransactionType.EXPENSE)
                            .amount(new BigDecimal("1500.00"))
                            .description("Monthly Rent")
                            .frequency(RecurringFrequency.MONTHLY)
                            .nextOccurrence(LocalDate.of(2025, 2, 1))
                            .isActive(true)
                            .build();

            String toString = recurring.toString();

            // Should include @ToString.Include fields
            assertThat(toString).contains("id=1");
            assertThat(toString).contains("type=EXPENSE");
            assertThat(toString).contains("amount=1500.00");
            assertThat(toString).contains("description=Monthly Rent");
            assertThat(toString).contains("frequency=MONTHLY");
            assertThat(toString).contains("nextOccurrence=2025-02-01");
            assertThat(toString).contains("isActive=true");
        }
    }
}
