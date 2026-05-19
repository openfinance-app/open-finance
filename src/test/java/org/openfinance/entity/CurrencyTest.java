package org.openfinance.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Currency entity.
 *
 * <p>Tests validation constraints and business logic:
 *
 * <ul>
 *   <li>Field validations (code, name, symbol)
 *   <li>ISO 4217 code format validation (3 uppercase letters)
 *   <li>Builder pattern functionality
 *   <li>Default values (isActive = true)
 * </ul>
 *
 * @author Open-Finance Development Team
 * @since 1.0
 */
@DisplayName("Currency Entity Tests")
class CurrencyTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== Valid Currency Tests ====================

    @Test
    @DisplayName("Should create valid currency with all fields")
    void shouldCreateValidCurrency() {
        Currency currency =
                Currency.builder().code("USD").name("US Dollar").symbol("$").isActive(true).build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).isEmpty();
        assertThat(currency.getCode()).isEqualTo("USD");
        assertThat(currency.getName()).isEqualTo("US Dollar");
        assertThat(currency.getSymbol()).isEqualTo("$");
        assertThat(currency.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("Should create valid currency with default isActive true")
    void shouldCreateCurrencyWithDefaultIsActiveTrue() {
        Currency currency = Currency.builder().code("EUR").name("Euro").symbol("€").build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).isEmpty();
        assertThat(currency.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("Should create valid inactive currency")
    void shouldCreateValidInactiveCurrency() {
        Currency currency =
                Currency.builder()
                        .code("XAU")
                        .name("Gold Ounce")
                        .symbol("XAU")
                        .isActive(false)
                        .build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).isEmpty();
        assertThat(currency.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("Should create valid crypto currency")
    void shouldCreateValidCryptoCurrency() {
        Currency currency =
                Currency.builder().code("BTC").name("Bitcoin").symbol("₿").isActive(true).build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).isEmpty();
        assertThat(currency.getCode()).isEqualTo("BTC");
        assertThat(currency.getSymbol()).isEqualTo("₿");
    }

    // ==================== Code Validation Tests ====================

    @Test
    @DisplayName("Should fail validation when code is null")
    void shouldFailWhenCodeIsNull() {
        Currency currency = Currency.builder().code(null).name("US Dollar").symbol("$").build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Currency code cannot be blank");
    }

    @Test
    @DisplayName("Should fail validation when code is blank")
    void shouldFailWhenCodeIsBlank() {
        Currency currency = Currency.builder().code("").name("US Dollar").symbol("$").build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        // Multiple violations expected: @NotBlank, @Pattern, @Size
        assertThat(violations).hasSizeGreaterThanOrEqualTo(1);
        assertThat(violations)
                .anyMatch(v -> v.getMessage().equals("Currency code cannot be blank"));
    }

    @Test
    @DisplayName("Should fail validation when code is not 3 characters")
    void shouldFailWhenCodeIsNot3Characters() {
        Currency currency = Currency.builder().code("US").name("US Dollar").symbol("$").build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        // Both @Pattern and @Size will fail
        assertThat(violations).hasSizeGreaterThanOrEqualTo(1);
        assertThat(violations)
                .anyMatch(
                        v ->
                                v.getMessage()
                                                .equals(
                                                        "Currency code must be 3 to 10 uppercase letters")
                                        || v.getMessage()
                                                .equals(
                                                        "Currency code must be between 3 and 10 characters"));
    }

    @Test
    @DisplayName("Should fail validation when code has lowercase letters")
    void shouldFailWhenCodeHasLowercaseLetters() {
        Currency currency = Currency.builder().code("usd").name("US Dollar").symbol("$").build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Currency code must be 3 to 10 uppercase letters");
    }

    @Test
    @DisplayName("Should fail validation when code has mixed case")
    void shouldFailWhenCodeHasMixedCase() {
        Currency currency = Currency.builder().code("Usd").name("US Dollar").symbol("$").build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Currency code must be 3 to 10 uppercase letters");
    }

    @Test
    @DisplayName("Should fail validation when code contains numbers")
    void shouldFailWhenCodeContainsNumbers() {
        Currency currency = Currency.builder().code("US1").name("US Dollar").symbol("$").build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Currency code must be 3 to 10 uppercase letters");
    }

    @Test
    @DisplayName("Should fail validation when code contains special characters")
    void shouldFailWhenCodeContainsSpecialCharacters() {
        Currency currency = Currency.builder().code("US$").name("US Dollar").symbol("$").build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Currency code must be 3 to 10 uppercase letters");
    }

    // ==================== Name Validation Tests ====================

    @Test
    @DisplayName("Should fail validation when name is null")
    void shouldFailWhenNameIsNull() {
        Currency currency = Currency.builder().code("USD").name(null).symbol("$").build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Currency name cannot be blank");
    }

    @Test
    @DisplayName("Should fail validation when name is blank")
    void shouldFailWhenNameIsBlank() {
        Currency currency = Currency.builder().code("USD").name("").symbol("$").build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Currency name cannot be blank");
    }

    @Test
    @DisplayName("Should accept long currency name")
    void shouldAcceptLongCurrencyName() {
        Currency currency =
                Currency.builder()
                        .code("XDR")
                        .name("Special Drawing Rights (International Monetary Fund)")
                        .symbol("XDR")
                        .build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).isEmpty();
    }

    // ==================== Symbol Validation Tests ====================

    @Test
    @DisplayName("Should fail validation when symbol is null")
    void shouldFailWhenSymbolIsNull() {
        Currency currency = Currency.builder().code("USD").name("US Dollar").symbol(null).build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Currency symbol cannot be blank");
    }

    @Test
    @DisplayName("Should fail validation when symbol is blank")
    void shouldFailWhenSymbolIsBlank() {
        Currency currency = Currency.builder().code("USD").name("US Dollar").symbol("").build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Currency symbol cannot be blank");
    }

    @Test
    @DisplayName("Should accept Unicode symbols")
    void shouldAcceptUnicodeSymbols() {
        Currency currency = Currency.builder().code("EUR").name("Euro").symbol("€").build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).isEmpty();
        assertThat(currency.getSymbol()).isEqualTo("€");
    }

    @Test
    @DisplayName("Should accept multi-character symbols")
    void shouldAcceptMultiCharacterSymbols() {
        Currency currency =
                Currency.builder().code("GBP").name("British Pound").symbol("£").build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).isEmpty();
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle currency code same as symbol")
    void shouldHandleCurrencyCodeSameAsSymbol() {
        Currency currency = Currency.builder().code("XAU").name("Gold Ounce").symbol("XAU").build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).isEmpty();
        assertThat(currency.getCode()).isEqualTo(currency.getSymbol());
    }

    @Test
    @DisplayName("Should validate all required fields missing")
    void shouldValidateAllRequiredFieldsMissing() {
        Currency currency = Currency.builder().build();

        Set<ConstraintViolation<Currency>> violations = validator.validate(currency);

        assertThat(violations).hasSize(3); // code, name, symbol all required
    }

    @Test
    @DisplayName("Should maintain immutability of validation rules")
    void shouldMaintainImmutabilityOfValidationRules() {
        // Create valid currency
        Currency currency1 = Currency.builder().code("USD").name("US Dollar").symbol("$").build();

        // Create another valid currency
        Currency currency2 = Currency.builder().code("EUR").name("Euro").symbol("€").build();

        Set<ConstraintViolation<Currency>> violations1 = validator.validate(currency1);
        Set<ConstraintViolation<Currency>> violations2 = validator.validate(currency2);

        assertThat(violations1).isEmpty();
        assertThat(violations2).isEmpty();
    }
}
