package org.openfinance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.ExchangeRate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for the ExchangeRateRepository.
 *
 * <p>Tests custom query methods and JPA repository functionality:
 *
 * <ul>
 *   <li>findLatestByBaseCurrencyAndTargetCurrency() - latest rate for currency pair
 *   <li>findByBaseCurrencyAndTargetCurrencyAndRateDate() - exact date rate
 *   <li>findByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualOrderByRateDateDesc() -
 *       historical rates
 *   <li>findByRateDate() - all rates for specific date
 *   <li>deleteByRateDateBefore() - cleanup old rates
 *   <li>Basic CRUD operations inherited from JpaRepository
 * </ul>
 *
 * @author Open-Finance Development Team
 * @since 1.0
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test") // Uses H2 test database
@DisplayName("ExchangeRate Repository Tests")
class ExchangeRateRepositoryTest {

    @Autowired private ExchangeRateRepository exchangeRateRepository;

    @BeforeEach
    void setUp() {
        // Clean database before each test
        exchangeRateRepository.deleteAll();
    }

    // ==================== findLatestByBaseCurrencyAndTargetCurrency() Tests ====================

    @Test
    @DisplayName("Should find latest exchange rate for currency pair")
    void shouldFindLatestExchangeRate() {
        // Arrange
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate lastWeek = today.minusDays(7);

        ExchangeRate rate1 = createRate("USD", "EUR", new BigDecimal("0.85"), lastWeek);
        ExchangeRate rate2 = createRate("USD", "EUR", new BigDecimal("0.86"), yesterday);
        ExchangeRate rate3 = createRate("USD", "EUR", new BigDecimal("0.87"), today);

        exchangeRateRepository.saveAll(List.of(rate1, rate2, rate3));

        // Act
        List<ExchangeRate> latest =
                exchangeRateRepository.findLatestByBaseCurrencyAndTargetCurrency("USD", "EUR");

        // Assert
        assertThat(latest).isNotEmpty();
        assertThat(latest.get(0).getRateDate()).isEqualTo(today);
        assertThat(latest.get(0).getRate()).isEqualByComparingTo("0.87");
    }

    @Test
    @DisplayName("Should return multiple rates ordered by date descending")
    void shouldReturnMultipleRatesOrderedByDateDescending() {
        // Arrange
        LocalDate date1 = LocalDate.of(2024, 1, 1);
        LocalDate date2 = LocalDate.of(2024, 1, 15);
        LocalDate date3 = LocalDate.of(2024, 2, 1);

        ExchangeRate rate1 = createRate("USD", "GBP", new BigDecimal("0.79"), date1);
        ExchangeRate rate2 = createRate("USD", "GBP", new BigDecimal("0.80"), date2);
        ExchangeRate rate3 = createRate("USD", "GBP", new BigDecimal("0.81"), date3);

        exchangeRateRepository.saveAll(List.of(rate1, rate3, rate2)); // Save out of order

        // Act
        List<ExchangeRate> rates =
                exchangeRateRepository.findLatestByBaseCurrencyAndTargetCurrency("USD", "GBP");

        // Assert
        assertThat(rates).hasSize(3);
        assertThat(rates)
                .extracting(ExchangeRate::getRateDate)
                .containsExactly(date3, date2, date1); // descending order
    }

    @Test
    @DisplayName("Should return empty list when no rates found for currency pair")
    void shouldReturnEmptyListWhenNoRatesFound() {
        // Act
        List<ExchangeRate> rates =
                exchangeRateRepository.findLatestByBaseCurrencyAndTargetCurrency("USD", "XXX");

        // Assert
        assertThat(rates).isEmpty();
    }

    @Test
    @DisplayName("Should be case sensitive for currency codes")
    void shouldBeCaseSensitiveForCurrencyCodes() {
        // Arrange
        ExchangeRate rate = createRate("USD", "EUR", new BigDecimal("0.85"), LocalDate.now());
        exchangeRateRepository.save(rate);

        // Act
        List<ExchangeRate> rates =
                exchangeRateRepository.findLatestByBaseCurrencyAndTargetCurrency("usd", "eur");

        // Assert
        assertThat(rates).isEmpty();
    }

    // ==================== findByBaseCurrencyAndTargetCurrencyAndRateDate() Tests
    // ====================

    @Test
    @DisplayName("Should find exchange rate for specific date")
    void shouldFindExchangeRateForSpecificDate() {
        // Arrange
        LocalDate date = LocalDate.of(2024, 3, 15);
        ExchangeRate rate = createRate("USD", "JPY", new BigDecimal("150.50"), date);
        exchangeRateRepository.save(rate);

        // Act
        Optional<ExchangeRate> found =
                exchangeRateRepository.findByBaseCurrencyAndTargetCurrencyAndRateDate(
                        "USD", "JPY", date);

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getRate()).isEqualByComparingTo("150.50");
        assertThat(found.get().getRateDate()).isEqualTo(date);
    }

    @Test
    @DisplayName("Should return empty when exact date not found")
    void shouldReturnEmptyWhenExactDateNotFound() {
        // Arrange
        LocalDate date1 = LocalDate.of(2024, 3, 10);
        LocalDate date2 = LocalDate.of(2024, 3, 15);
        ExchangeRate rate = createRate("USD", "CHF", new BigDecimal("0.88"), date1);
        exchangeRateRepository.save(rate);

        // Act
        Optional<ExchangeRate> found =
                exchangeRateRepository.findByBaseCurrencyAndTargetCurrencyAndRateDate(
                        "USD", "CHF", date2);

        // Assert
        assertThat(found).isEmpty();
    }

    // ====================
    // findByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualOrderByRateDateDesc() Tests
    // ====================

    @Test
    @DisplayName("Should find most recent rate on or before date")
    void shouldFindMostRecentRateOnOrBeforeDate() {
        // Arrange
        LocalDate march10 = LocalDate.of(2024, 3, 10);
        LocalDate march15 = LocalDate.of(2024, 3, 15);
        LocalDate march20 = LocalDate.of(2024, 3, 20);

        ExchangeRate rate1 = createRate("BTC", "USD", new BigDecimal("60000"), march10);
        ExchangeRate rate2 = createRate("BTC", "USD", new BigDecimal("62000"), march20);

        exchangeRateRepository.saveAll(List.of(rate1, rate2));

        // Act - query for March 15 (between the two dates)
        List<ExchangeRate> rates =
                exchangeRateRepository
                        .findByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(
                                "BTC", "USD", march15);

        // Assert
        assertThat(rates).hasSize(1);
        assertThat(rates.get(0).getRateDate()).isEqualTo(march10);
        assertThat(rates.get(0).getRate()).isEqualByComparingTo("60000");
    }

    @Test
    @DisplayName("Should include exact date match in historical query")
    void shouldIncludeExactDateMatchInHistoricalQuery() {
        // Arrange
        LocalDate targetDate = LocalDate.of(2024, 3, 15);
        ExchangeRate rate = createRate("EUR", "USD", new BigDecimal("1.10"), targetDate);
        exchangeRateRepository.save(rate);

        // Act
        List<ExchangeRate> rates =
                exchangeRateRepository
                        .findByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(
                                "EUR", "USD", targetDate);

        // Assert
        assertThat(rates).hasSize(1);
        assertThat(rates.get(0).getRateDate()).isEqualTo(targetDate);
    }

    @Test
    @DisplayName("Should return empty list when no historical rates found")
    void shouldReturnEmptyListWhenNoHistoricalRatesFound() {
        // Arrange
        LocalDate futureDate = LocalDate.of(2024, 12, 31);
        ExchangeRate rate = createRate("GBP", "EUR", new BigDecimal("1.15"), futureDate);
        exchangeRateRepository.save(rate);

        LocalDate pastDate = LocalDate.of(2024, 1, 1);

        // Act
        List<ExchangeRate> rates =
                exchangeRateRepository
                        .findByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(
                                "GBP", "EUR", pastDate);

        // Assert
        assertThat(rates).isEmpty();
    }

    @Test
    @DisplayName("Should return all historical rates in descending order")
    void shouldReturnAllHistoricalRatesInDescendingOrder() {
        // Arrange
        LocalDate jan = LocalDate.of(2024, 1, 1);
        LocalDate feb = LocalDate.of(2024, 2, 1);
        LocalDate mar = LocalDate.of(2024, 3, 1);
        LocalDate apr = LocalDate.of(2024, 4, 1);

        ExchangeRate rate1 = createRate("ETH", "USD", new BigDecimal("2000"), jan);
        ExchangeRate rate2 = createRate("ETH", "USD", new BigDecimal("2200"), feb);
        ExchangeRate rate3 = createRate("ETH", "USD", new BigDecimal("2400"), mar);

        exchangeRateRepository.saveAll(List.of(rate1, rate2, rate3));

        // Act - query up to March (should get 3 rates)
        List<ExchangeRate> rates =
                exchangeRateRepository
                        .findByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(
                                "ETH", "USD", mar);

        // Assert
        assertThat(rates).hasSize(3);
        assertThat(rates).extracting(ExchangeRate::getRateDate).containsExactly(mar, feb, jan);
    }

    // ==================== findByRateDate() Tests ====================

    @Test
    @DisplayName("Should find all exchange rates for specific date")
    void shouldFindAllExchangeRatesForSpecificDate() {
        // Arrange
        LocalDate date = LocalDate.of(2024, 5, 1);

        ExchangeRate rate1 = createRate("USD", "EUR", new BigDecimal("0.85"), date);
        ExchangeRate rate2 = createRate("USD", "GBP", new BigDecimal("0.79"), date);
        ExchangeRate rate3 = createRate("BTC", "USD", new BigDecimal("60000"), date);
        ExchangeRate rate4 =
                createRate("USD", "JPY", new BigDecimal("150"), LocalDate.of(2024, 5, 2));

        exchangeRateRepository.saveAll(List.of(rate1, rate2, rate3, rate4));

        // Act
        List<ExchangeRate> rates = exchangeRateRepository.findByRateDate(date);

        // Assert
        assertThat(rates).hasSize(3);
        assertThat(rates).allMatch(r -> r.getRateDate().equals(date));
    }

    @Test
    @DisplayName("Should return empty list when no rates for date")
    void shouldReturnEmptyListWhenNoRatesForDate() {
        // Act
        List<ExchangeRate> rates = exchangeRateRepository.findByRateDate(LocalDate.of(2020, 1, 1));

        // Assert
        assertThat(rates).isEmpty();
    }

    // ==================== deleteByRateDateBefore() Tests ====================

    @Test
    @DisplayName("Should delete rates older than cutoff date")
    void shouldDeleteRatesOlderThanCutoffDate() {
        // Arrange
        LocalDate old1 = LocalDate.of(2023, 1, 1);
        LocalDate old2 = LocalDate.of(2023, 6, 1);
        LocalDate cutoff = LocalDate.of(2024, 1, 1);
        LocalDate recent = LocalDate.of(2024, 6, 1);

        ExchangeRate rate1 = createRate("USD", "EUR", new BigDecimal("0.85"), old1);
        ExchangeRate rate2 = createRate("USD", "GBP", new BigDecimal("0.79"), old2);
        ExchangeRate rate3 = createRate("USD", "JPY", new BigDecimal("150"), recent);

        exchangeRateRepository.saveAll(List.of(rate1, rate2, rate3));

        // Act
        exchangeRateRepository.deleteByRateDateBefore(cutoff);
        exchangeRateRepository.flush(); // Ensure deletion is executed

        // Assert
        List<ExchangeRate> remaining = exchangeRateRepository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getRateDate()).isEqualTo(recent);
    }

    @Test
    @DisplayName("Should not delete rates on or after cutoff date")
    void shouldNotDeleteRatesOnOrAfterCutoffDate() {
        // Arrange
        LocalDate cutoff = LocalDate.of(2024, 1, 1);
        LocalDate onCutoff = cutoff;
        LocalDate afterCutoff = cutoff.plusDays(1);

        ExchangeRate rate1 = createRate("USD", "EUR", new BigDecimal("0.85"), onCutoff);
        ExchangeRate rate2 = createRate("USD", "GBP", new BigDecimal("0.79"), afterCutoff);

        exchangeRateRepository.saveAll(List.of(rate1, rate2));

        // Act
        exchangeRateRepository.deleteByRateDateBefore(cutoff);
        exchangeRateRepository.flush();

        // Assert
        List<ExchangeRate> remaining = exchangeRateRepository.findAll();
        assertThat(remaining).hasSize(2);
    }

    // ==================== CRUD Operations Tests ====================

    @Test
    @DisplayName("Should save and retrieve exchange rate")
    void shouldSaveAndRetrieveExchangeRate() {
        // Arrange
        ExchangeRate rate = createRate("USD", "CAD", new BigDecimal("1.35"), LocalDate.now());

        // Act
        ExchangeRate saved = exchangeRateRepository.save(rate);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBaseCurrency()).isEqualTo("USD");
        assertThat(saved.getTargetCurrency()).isEqualTo("CAD");

        // Verify retrieval
        Optional<ExchangeRate> retrieved = exchangeRateRepository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getRate()).isEqualByComparingTo("1.35");
    }

    @Test
    @DisplayName("Should update exchange rate")
    void shouldUpdateExchangeRate() {
        // Arrange
        ExchangeRate rate = createRate("USD", "MXN", new BigDecimal("17.50"), LocalDate.now());
        ExchangeRate saved = exchangeRateRepository.save(rate);

        // Act
        saved.setRate(new BigDecimal("18.00"));
        ExchangeRate updated = exchangeRateRepository.save(saved);

        // Assert
        assertThat(updated.getRate()).isEqualByComparingTo("18.00");

        // Verify update persisted
        Optional<ExchangeRate> retrieved = exchangeRateRepository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getRate()).isEqualByComparingTo("18.00");
    }

    @Test
    @DisplayName("Should delete exchange rate")
    void shouldDeleteExchangeRate() {
        // Arrange
        ExchangeRate rate = createRate("USD", "AUD", new BigDecimal("1.50"), LocalDate.now());
        ExchangeRate saved = exchangeRateRepository.save(rate);

        // Act
        exchangeRateRepository.deleteById(saved.getId());

        // Assert
        Optional<ExchangeRate> deleted = exchangeRateRepository.findById(saved.getId());
        assertThat(deleted).isEmpty();
    }

    @Test
    @DisplayName("Should enforce unique constraint for currency pair and date")
    void shouldEnforceUniqueConstraintForCurrencyPairAndDate() {
        // Arrange
        LocalDate date = LocalDate.now();
        ExchangeRate rate1 = createRate("USD", "NZD", new BigDecimal("1.60"), date);
        ExchangeRate rate2 = createRate("USD", "NZD", new BigDecimal("1.65"), date);

        exchangeRateRepository.save(rate1);

        // Act & Assert
        // Should throw exception due to unique constraint violation
        try {
            exchangeRateRepository.save(rate2);
            exchangeRateRepository.flush(); // Force constraint check
            // If we get here, the constraint didn't work as expected
            assertThat(true).as("Unique constraint should have been violated").isFalse();
        } catch (Exception e) {
            // Expected: unique constraint violation
            assertThat(e).isNotNull();
        }
    }

    @Test
    @DisplayName("Should allow same currency pair on different dates")
    void shouldAllowSameCurrencyPairOnDifferentDates() {
        // Arrange
        LocalDate date1 = LocalDate.of(2024, 5, 1);
        LocalDate date2 = LocalDate.of(2024, 5, 2);

        ExchangeRate rate1 = createRate("USD", "SEK", new BigDecimal("10.50"), date1);
        ExchangeRate rate2 = createRate("USD", "SEK", new BigDecimal("10.55"), date2);

        // Act & Assert - should not throw exception
        exchangeRateRepository.save(rate1);
        exchangeRateRepository.save(rate2);
        exchangeRateRepository.flush();

        List<ExchangeRate> rates =
                exchangeRateRepository.findLatestByBaseCurrencyAndTargetCurrency("USD", "SEK");
        assertThat(rates).hasSize(2);
    }

    // ==================== Helper Methods ====================

    private ExchangeRate createRate(String base, String target, BigDecimal rate, LocalDate date) {
        return ExchangeRate.builder()
                .baseCurrency(base)
                .targetCurrency(target)
                .rate(rate)
                .rateDate(date)
                .source("test")
                .build();
    }
}
