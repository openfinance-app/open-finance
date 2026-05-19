package org.openfinance.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Liability entity validation. Tests Jakarta validation constraints, timestamps, and
 * field constraints.
 *
 * <p>Note: Encrypted fields (principal, currentBalance, interestRate, minimumPayment, notes) are
 * stored as Strings in the entity. The service layer handles encryption/decryption and business
 * validation.
 */
class LiabilityTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /**
     * Creates a valid Liability for testing. Encrypted fields are represented as Strings (encrypted
     * values in real use).
     */
    private Liability createValidLiability() {
        Liability liability = new Liability();
        liability.setUserId(1L);
        liability.setName("Test Loan");
        liability.setType(LiabilityType.PERSONAL_LOAN);
        liability.setPrincipal("encrypted_10000.00"); // Encrypted field
        liability.setCurrentBalance("encrypted_8500.00"); // Encrypted field
        liability.setInterestRate("encrypted_5.5"); // Encrypted field
        liability.setStartDate(LocalDate.now().minusYears(1));
        liability.setEndDate(LocalDate.now().plusYears(2));
        liability.setMinimumPayment("encrypted_250.00"); // Encrypted field
        liability.setCurrency("USD");
        liability.setNotes("encrypted_notes"); // Encrypted field
        return liability;
    }

    @Test
    void shouldPassValidation_WhenAllFieldsAreValid() {
        // Given
        Liability liability = createValidLiability();

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldPassValidation_WhenUserIdIsNull() {
        // Given
        Liability liability = createValidLiability();
        liability.setUserId(null);

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        // userId IS validated at entity level with @NotNull
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("User ID cannot be null");
    }

    @Test
    void shouldFailValidation_WhenNameIsBlank() {
        // Given
        Liability liability = createValidLiability();
        liability.setName("");

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("name");
    }

    @Test
    void shouldFailValidation_WhenTypeIsNull() {
        // Given
        Liability liability = createValidLiability();
        liability.setType(null);

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("type");
    }

    @Test
    void shouldFailValidation_WhenPrincipalIsNull() {
        // Given
        Liability liability = createValidLiability();
        liability.setPrincipal(null);

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("principal");
    }

    @Test
    void shouldPassValidation_WhenPrincipalIsAnyString() {
        // Given
        Liability liability = createValidLiability();
        liability.setPrincipal("any_encrypted_value");

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        // Numeric validation happens at service layer after decryption
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFailValidation_WhenCurrentBalanceIsNull() {
        // Given
        Liability liability = createValidLiability();
        liability.setCurrentBalance(null);

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("currentBalance");
    }

    @Test
    void shouldPassValidation_WhenCurrentBalanceIsAnyString() {
        // Given
        Liability liability = createValidLiability();
        liability.setCurrentBalance("any_encrypted_value");

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldPassValidation_WhenInterestRateIsAnyString() {
        // Given
        Liability liability = createValidLiability();
        liability.setInterestRate("any_encrypted_value");

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldPassValidation_WhenInterestRateIsNull() {
        // Given
        Liability liability = createValidLiability();
        liability.setInterestRate(null);

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFailValidation_WhenStartDateIsNull() {
        // Given
        Liability liability = createValidLiability();
        liability.setStartDate(null);

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("startDate");
    }

    @Test
    void shouldPassValidation_WhenStartDateIsInFuture() {
        // Given
        Liability liability = createValidLiability();
        liability.setStartDate(LocalDate.now().plusDays(1));

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        // No @PastOrPresent constraint on startDate at entity level
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldPassValidation_WhenStartDateIsToday() {
        // Given
        Liability liability = createValidLiability();
        liability.setStartDate(LocalDate.now());

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldPassValidation_WhenEndDateIsInPast() {
        // Given
        Liability liability = createValidLiability();
        liability.setEndDate(LocalDate.now().minusDays(1));

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        // No @Future constraint on endDate at entity level
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldPassValidation_WhenEndDateIsNull() {
        // Given
        Liability liability = createValidLiability();
        liability.setEndDate(null);

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldPassValidation_WhenMinimumPaymentIsAnyString() {
        // Given
        Liability liability = createValidLiability();
        liability.setMinimumPayment("any_encrypted_value");

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldPassValidation_WhenMinimumPaymentIsNull() {
        // Given
        Liability liability = createValidLiability();
        liability.setMinimumPayment(null);

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFailValidation_WhenCurrencyIsBlank() {
        // Given
        Liability liability = createValidLiability();
        liability.setCurrency("");

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        // Blank currency triggers both @NotBlank and @Pattern validation
        assertThat(violations).hasSizeGreaterThanOrEqualTo(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }

    @Test
    void shouldFailValidation_WhenCurrencyIsNull() {
        // Given
        Liability liability = createValidLiability();
        liability.setCurrency(null);

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("currency");
    }

    @Test
    void shouldPassValidation_WhenNotesAreNull() {
        // Given
        Liability liability = createValidLiability();
        liability.setNotes(null);

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldSetCreatedAtAndUpdatedAt_OnPrePersist() {
        // Given
        Liability liability = createValidLiability();
        assertThat(liability.getCreatedAt()).isNull();
        assertThat(liability.getUpdatedAt()).isNull();

        // When
        liability.onCreate();

        // Then
        assertThat(liability.getCreatedAt()).isNotNull();
        assertThat(liability.getUpdatedAt()).isNotNull();
        // Both timestamps should be close (within same method call)
        assertThat(liability.getCreatedAt()).isBeforeOrEqualTo(liability.getUpdatedAt());
    }

    @Test
    void shouldUpdateUpdatedAt_OnPreUpdate() throws InterruptedException {
        // Given
        Liability liability = createValidLiability();
        liability.onCreate();
        LocalDateTime originalCreatedAt = liability.getCreatedAt();
        LocalDateTime originalUpdatedAt = liability.getUpdatedAt();

        // Wait a bit to ensure timestamp difference
        Thread.sleep(10);

        // When
        liability.onUpdate();

        // Then
        assertThat(liability.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(liability.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void shouldAcceptAllLiabilityTypes() {
        // Test all enum values are valid
        for (LiabilityType type : LiabilityType.values()) {
            Liability liability = createValidLiability();
            liability.setType(type);

            Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

            assertThat(violations).isEmpty();
        }
    }

    @Test
    void shouldAcceptValidCurrencyCodes() {
        // Given
        String[] validCurrencies = {"USD", "EUR", "GBP", "JPY", "CNY"};

        for (String currency : validCurrencies) {
            Liability liability = createValidLiability();
            liability.setCurrency(currency);

            // When
            Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    @Test
    void shouldAcceptLongEncryptedStrings() {
        // Given - simulate 512-char encrypted values (max column length)
        Liability liability = createValidLiability();
        String longEncrypted = "a".repeat(512);

        liability.setName(longEncrypted);
        liability.setPrincipal(longEncrypted);
        liability.setCurrentBalance(longEncrypted);
        liability.setInterestRate(longEncrypted);
        liability.setMinimumPayment(longEncrypted);
        liability.setNotes(longEncrypted);

        // When
        Set<ConstraintViolation<Liability>> violations = validator.validate(liability);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldHaveCorrectToStringRepresentation() {
        // Given
        Liability liability = createValidLiability();
        liability.setId(123L);

        // When
        String toString = liability.toString();

        // Then
        assertThat(toString).contains("id=123");
        assertThat(toString).contains("userId=1");
        assertThat(toString).contains("Test Loan");
        assertThat(toString).contains("PERSONAL_LOAN");
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
        // Given
        Liability liability1 = createValidLiability();
        liability1.setId(123L);

        Liability liability2 = createValidLiability();
        liability2.setId(123L);

        Liability liability3 = createValidLiability();
        liability3.setId(456L);

        // Then
        assertThat(liability1).isEqualTo(liability2);
        assertThat(liability1.hashCode()).isEqualTo(liability2.hashCode());
        assertThat(liability1).isNotEqualTo(liability3);
    }
}
