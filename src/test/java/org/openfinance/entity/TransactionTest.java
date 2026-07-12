package org.openfinance.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Transaction entity validation tests")
class TransactionTest {

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
    @DisplayName("Should accept crypto-scale amounts below one cent")
    void shouldAcceptCryptoScaleAmountsBelowOneCent() {
        Transaction transaction =
                Transaction.builder()
                        .userId(1L)
                        .accountId(10L)
                        .type(TransactionType.INCOME)
                        .amount(new BigDecimal("0.0045"))
                        .currency("BTC")
                        .date(LocalDate.of(2024, 1, 1))
                        .build();

        Set<ConstraintViolation<Transaction>> violations = validator.validate(transaction);

        assertThat(violations)
                .filteredOn(violation -> violation.getPropertyPath().toString().equals("amount"))
                .isEmpty();
    }
}
