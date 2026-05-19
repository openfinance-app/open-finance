package org.openfinance.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the ExchangeRate entity.
 *
 * <p>Tests validation constraints and business logic:
 *
 * <ul>
 *   <li>Field validations (baseCurrency, targetCurrency, rate, rateDate)
 *   <li>ISO 4217 currency code format validation (3 uppercase letters)
 *   <li>Rate precision and BigDecimal handling
 *   <li>Helper methods (getInverseRate, isForCurrencyPair)
 *   <li>Builder pattern functionality
 *   <li>Default values (source = "system")
 * </ul>
 *
 * @author Open-Finance Development Team
 * @since 1.0
 */
@DisplayName("ExchangeRate Entity Tests")
class ExchangeRateTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== Valid ExchangeRate Tests ====================

    @Test
    @DisplayName("Should create valid fiat exchange rate")
    void shouldCreateValidFiatExchangeRate() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.85000000"))
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        assertThat(violations).isEmpty();
        assertThat(rate.getBaseCurrency()).isEqualTo("USD");
        assertThat(rate.getTargetCurrency()).isEqualTo("EUR");
        assertThat(rate.getRate()).isEqualByComparingTo("0.85000000");
        assertThat(rate.getRateDate()).isEqualTo(LocalDate.now());
        assertThat(rate.getSource()).isEqualTo("system"); // default value
    }

    @Test
    @DisplayName("Should create valid crypto exchange rate")
    void shouldCreateValidCryptoExchangeRate() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("BTC")
                        .targetCurrency("USD")
                        .rate(new BigDecimal("95000.00000000"))
                        .rateDate(LocalDate.now())
                        .source("yfinance")
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        assertThat(violations).isEmpty();
        assertThat(rate.getBaseCurrency()).isEqualTo("BTC");
        assertThat(rate.getTargetCurrency()).isEqualTo("USD");
        assertThat(rate.getRate()).isEqualByComparingTo("95000.00000000");
        assertThat(rate.getSource()).isEqualTo("yfinance");
    }

    @Test
    @DisplayName("Should create valid historical exchange rate")
    void shouldCreateValidHistoricalExchangeRate() {
        LocalDate historicalDate = LocalDate.of(2023, 1, 1);

        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("GBP")
                        .rate(new BigDecimal("0.79000000"))
                        .rateDate(historicalDate)
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        assertThat(violations).isEmpty();
        assertThat(rate.getRateDate()).isEqualTo(historicalDate);
    }

    @Test
    @DisplayName("Should create exchange rate with default source value")
    void shouldCreateExchangeRateWithDefaultSource() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("EUR")
                        .targetCurrency("JPY")
                        .rate(new BigDecimal("165.50000000"))
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        assertThat(violations).isEmpty();
        assertThat(rate.getSource()).isEqualTo("system");
    }

    @Test
    @DisplayName("Should handle BigDecimal with 8 decimal precision")
    void shouldHandleBigDecimalWith8DecimalPrecision() {
        BigDecimal preciseRate = new BigDecimal("0.00001053");

        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("BTC")
                        .rate(preciseRate)
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        assertThat(violations).isEmpty();
        assertThat(rate.getRate()).isEqualByComparingTo(preciseRate);
    }

    // ==================== Base Currency Validation Tests ====================

    @Test
    @DisplayName("Should fail validation when base currency is null")
    void shouldFailWhenBaseCurrencyIsNull() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency(null)
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.85"))
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Base currency cannot be null");
    }

    @Test
    @DisplayName("Should fail validation when base currency is blank")
    void shouldFailWhenBaseCurrencyIsBlank() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.85"))
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        // Multiple violations expected: @Pattern, @Size
        assertThat(violations).hasSizeGreaterThanOrEqualTo(1);
        assertThat(violations)
                .anyMatch(
                        v ->
                                v.getMessage()
                                                .equals(
                                                        "Base currency must be 3 to 10 uppercase letters")
                                        || v.getMessage()
                                                .equals(
                                                        "Base currency must be exactly 3 characters"));
    }

    @Test
    @DisplayName("Should fail validation when base currency has lowercase letters")
    void shouldFailWhenBaseCurrencyHasLowercase() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("usd")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.85"))
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Base currency must be 3 to 10 uppercase letters");
    }

    @Test
    @DisplayName("Should fail validation when base currency is not 3 characters")
    void shouldFailWhenBaseCurrencyIsNot3Characters() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("US")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.85"))
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        // Both @Pattern and @Size will fail
        assertThat(violations).hasSizeGreaterThanOrEqualTo(1);
        assertThat(violations)
                .anyMatch(
                        v ->
                                v.getMessage()
                                                .equals(
                                                        "Base currency must be 3 to 10 uppercase letters")
                                        || v.getMessage()
                                                .equals(
                                                        "Base currency must be exactly 3 characters"));
    }

    @Test
    @DisplayName("Should fail validation when base currency contains numbers")
    void shouldFailWhenBaseCurrencyContainsNumbers() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("US1")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.85"))
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Base currency must be 3 to 10 uppercase letters");
    }

    // ==================== Target Currency Validation Tests ====================

    @Test
    @DisplayName("Should fail validation when target currency is null")
    void shouldFailWhenTargetCurrencyIsNull() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency(null)
                        .rate(new BigDecimal("0.85"))
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Target currency cannot be null");
    }

    @Test
    @DisplayName("Should fail validation when target currency is blank")
    void shouldFailWhenTargetCurrencyIsBlank() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("")
                        .rate(new BigDecimal("0.85"))
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        // Multiple violations expected: @Pattern, @Size
        assertThat(violations).hasSizeGreaterThanOrEqualTo(1);
        assertThat(violations)
                .anyMatch(
                        v ->
                                v.getMessage()
                                                .equals(
                                                        "Target currency must be 3 to 10 uppercase letters")
                                        || v.getMessage()
                                                .equals(
                                                        "Target currency must be exactly 3 characters"));
    }

    @Test
    @DisplayName("Should fail validation when target currency has lowercase letters")
    void shouldFailWhenTargetCurrencyHasLowercase() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("eur")
                        .rate(new BigDecimal("0.85"))
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Target currency must be 3 to 10 uppercase letters");
    }

    @Test
    @DisplayName("Should fail validation when target currency is not 3 characters")
    void shouldFailWhenTargetCurrencyIsNot3Characters() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EU") // 2 characters - too short
                        .rate(new BigDecimal("0.85"))
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        // Both @Pattern and @Size will fail
        assertThat(violations).hasSizeGreaterThanOrEqualTo(1);
        assertThat(violations)
                .anyMatch(
                        v ->
                                v.getMessage()
                                                .equals(
                                                        "Target currency must be 3 to 10 uppercase letters")
                                        || v.getMessage()
                                                .equals(
                                                        "Target currency must be between 3 and 10 characters"));
    }

    // ==================== Rate Validation Tests ====================

    @Test
    @DisplayName("Should fail validation when rate is null")
    void shouldFailWhenRateIsNull() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EUR")
                        .rate(null)
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Exchange rate cannot be null");
    }

    @Test
    @DisplayName("Should fail validation when rate is zero")
    void shouldFailWhenRateIsZero() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EUR")
                        .rate(BigDecimal.ZERO)
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Exchange rate must be greater than 0");
    }

    @Test
    @DisplayName("Should fail validation when rate is negative")
    void shouldFailWhenRateIsNegative() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("-0.85"))
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Exchange rate must be greater than 0");
    }

    // ==================== Rate Date Validation Tests ====================

    @Test
    @DisplayName("Should fail validation when rate date is null")
    void shouldFailWhenRateDateIsNull() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.85"))
                        .rateDate(null)
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("Rate date cannot be null");
    }

    @Test
    @DisplayName("Should accept future rate date")
    void shouldAcceptFutureRateDate() {
        LocalDate futureDate = LocalDate.now().plusDays(7);

        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.85"))
                        .rateDate(futureDate)
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        // No validation constraint on future dates - allowed for scheduled rates
        assertThat(violations).isEmpty();
    }

    // ==================== Helper Method Tests: getInverseRate() ====================

    @Test
    @DisplayName("Should calculate inverse rate correctly for fiat currency")
    void shouldCalculateInverseRateForFiat() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.85000000"))
                        .rateDate(LocalDate.now())
                        .build();

        BigDecimal inverseRate = rate.getInverseRate();

        // 1 / 0.85 = 1.17647058...
        BigDecimal expected = new BigDecimal("1.17647059"); // rounded to 8 decimals with HALF_UP
        assertThat(inverseRate).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("Should calculate inverse rate correctly for crypto")
    void shouldCalculateInverseRateForCrypto() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("BTC")
                        .targetCurrency("USD")
                        .rate(new BigDecimal("95000.00000000"))
                        .rateDate(LocalDate.now())
                        .build();

        BigDecimal inverseRate = rate.getInverseRate();

        // 1 / 95000 = 0.00001052631...
        BigDecimal expected = new BigDecimal("0.00001053"); // rounded to 8 decimals with HALF_UP
        assertThat(inverseRate).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("Should calculate inverse rate for rate = 1.0")
    void shouldCalculateInverseRateForOne() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("USD")
                        .rate(BigDecimal.ONE)
                        .rateDate(LocalDate.now())
                        .build();

        BigDecimal inverseRate = rate.getInverseRate();

        assertThat(inverseRate).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("Should throw exception when calculating inverse of zero rate")
    void shouldThrowExceptionWhenCalculatingInverseOfZero() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EUR")
                        .rate(BigDecimal.ZERO)
                        .rateDate(LocalDate.now())
                        .build();

        assertThatThrownBy(rate::getInverseRate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot calculate inverse of zero or null rate");
    }

    @Test
    @DisplayName("Should throw exception when calculating inverse of null rate")
    void shouldThrowExceptionWhenCalculatingInverseOfNull() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EUR")
                        .rate(null)
                        .rateDate(LocalDate.now())
                        .build();

        assertThatThrownBy(rate::getInverseRate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot calculate inverse of zero or null rate");
    }

    // ==================== Helper Method Tests: isForCurrencyPair() ====================

    @Test
    @DisplayName("Should return true for exact currency pair match")
    void shouldReturnTrueForExactCurrencyPairMatch() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.85"))
                        .rateDate(LocalDate.now())
                        .build();

        boolean result = rate.isForCurrencyPair("USD", "EUR");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false for inverse currency pair")
    void shouldReturnFalseForInverseCurrencyPair() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.85"))
                        .rateDate(LocalDate.now())
                        .build();

        boolean result = rate.isForCurrencyPair("EUR", "USD");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false for no match")
    void shouldReturnFalseForNoMatch() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.85"))
                        .rateDate(LocalDate.now())
                        .build();

        boolean result = rate.isForCurrencyPair("GBP", "JPY");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should be case sensitive in currency pair check")
    void shouldBeCaseSensitiveInCurrencyPairCheck() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.85"))
                        .rateDate(LocalDate.now())
                        .build();

        boolean result = rate.isForCurrencyPair("usd", "eur");

        assertThat(result).isFalse();
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should validate all required fields missing")
    void shouldValidateAllRequiredFieldsMissing() {
        ExchangeRate rate = ExchangeRate.builder().build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        // baseCurrency, targetCurrency, rate, rateDate all required
        assertThat(violations).hasSize(4);
    }

    @Test
    @DisplayName("Should handle same base and target currency")
    void shouldHandleSameBaseAndTargetCurrency() {
        ExchangeRate rate =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("USD")
                        .rate(BigDecimal.ONE)
                        .rateDate(LocalDate.now())
                        .build();

        Set<ConstraintViolation<ExchangeRate>> violations = validator.validate(rate);

        // No validation prevents same currency - allowed for completeness
        assertThat(violations).isEmpty();
        assertThat(rate.getBaseCurrency()).isEqualTo(rate.getTargetCurrency());
    }
}
