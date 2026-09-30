package org.openfinance.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.RuleActionType;

class TransactionRuleRequestTest {
    @Test
    void validatesSplitParametersAtTheRequestBoundary() {
        try (jakarta.validation.ValidatorFactory factory =
                Validation.buildDefaultValidatorFactory()) {
            jakarta.validation.Validator validator = factory.getValidator();
            TransactionRuleRequest.ActionRequest action =
                    TransactionRuleRequest.ActionRequest.builder()
                            .actionType(RuleActionType.ADD_SPLIT)
                            .actionValue("Groceries")
                            .build();
            for (String amount :
                    java.util.Arrays.asList(
                            null, "", " ", "0", "-1", "NaN", "1e100", "0.0000000000000000001")) {
                action.setActionValue2(amount);
                assertThat(validator.validate(action)).as("split amount %s", amount).isNotEmpty();
            }
            action.setActionValue2("12.34");
            assertThat(validator.validate(action)).isEmpty();
            action.setActionValue(" ");
            assertThat(validator.validate(action)).isNotEmpty();
            action.setActionType(RuleActionType.SKIP_TRANSACTION);
            assertThat(validator.validate(action)).isEmpty();
        }
    }
}
