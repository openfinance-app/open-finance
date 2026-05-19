package org.openfinance.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Account entity equals/hashCode and validation tests")
class AccountEntityTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("Should consider accounts equal when both have same non-null id")
    void shouldBeEqualWhenSameId() {
        // Given
        Account a1 =
                Account.builder()
                        .id(10L)
                        .userId(1L)
                        .name("Name A")
                        .type(AccountType.CHECKING)
                        .currency("USD")
                        .balance(new BigDecimal("10.00"))
                        .isActive(true)
                        .build();

        Account a2 =
                Account.builder()
                        .id(10L)
                        .userId(2L)
                        .name("Different Name")
                        .type(AccountType.SAVINGS)
                        .currency("EUR")
                        .balance(new BigDecimal("20.00"))
                        .isActive(false)
                        .build();

        // When / Then
        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
    }

    @Test
    @DisplayName("Should consider accounts equal by business key when id is null")
    void shouldBeEqualByBusinessKeyWhenIdNull() {
        // Given
        Account a1 =
                Account.builder()
                        .userId(5L)
                        .name("Primary")
                        .type(AccountType.CASH)
                        .currency("USD")
                        .balance(BigDecimal.ZERO)
                        .isActive(true)
                        .build();

        Account a2 =
                Account.builder()
                        .userId(5L)
                        .name("Primary")
                        .type(AccountType.CASH)
                        .currency("USD")
                        .balance(new BigDecimal("1.00"))
                        .isActive(false)
                        .build();

        // When / Then
        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());

        // Verify HashSet behaviour
        Set<Account> set = new HashSet<>();
        set.add(a1);
        set.add(a2); // should not add duplicate according to equals/hashCode
        assertThat(set).hasSize(1);
    }

    @Test
    @DisplayName("Should not be equal when business keys differ and id is null")
    void shouldNotBeEqualWhenBusinessKeyDiffers() {
        // Given
        Account a1 =
                Account.builder()
                        .userId(5L)
                        .name("Primary")
                        .type(AccountType.CASH)
                        .currency("USD")
                        .balance(BigDecimal.ZERO)
                        .isActive(true)
                        .build();

        Account a2 =
                Account.builder()
                        .userId(6L)
                        .name("Primary")
                        .type(AccountType.CASH)
                        .currency("USD")
                        .balance(BigDecimal.ZERO)
                        .isActive(true)
                        .build();

        // When / Then
        assertThat(a1).isNotEqualTo(a2);
    }

    @Test
    @DisplayName("Should validate required fields and constraints")
    void shouldValidateConstraints() {
        // Given: account with many invalid fields
        Account invalid =
                Account.builder()
                        .userId(null)
                        .name("") // too short
                        .type(null)
                        .currency("US") // wrong length
                        .balance(null)
                        .isActive(null)
                        .build();

        // When
        Set<ConstraintViolation<Account>> violations = validator.validate(invalid);

        // Then - expecting violations for userId, name, type, currency, balance, isActive
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("userId", "name", "type", "currency", "balance", "isActive");
    }

    @Test
    @DisplayName("Should enforce name max size constraint")
    void shouldEnforceNameMaxSize() {
        // Given: name exceeding 500 characters
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 501; i++) sb.append('x');

        Account invalid =
                Account.builder()
                        .userId(1L)
                        .name(sb.toString())
                        .type(AccountType.OTHER)
                        .currency("USD")
                        .balance(BigDecimal.ZERO)
                        .isActive(true)
                        .build();

        // When
        Set<ConstraintViolation<Account>> violations = validator.validate(invalid);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> "name".equals(v.getPropertyPath().toString()));
    }
}
