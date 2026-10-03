package org.openfinance.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.RuleActionType;
import org.openfinance.entity.RuleConditionField;
import org.openfinance.entity.RuleConditionOperator;

class TransactionRuleRequestTest {
    @Test
    void rejectsNonnumericAmountConditionsButAcceptsDecimalComparisons() {
        try (jakarta.validation.ValidatorFactory factory =
                Validation.buildDefaultValidatorFactory()) {
            jakarta.validation.Validator validator = factory.getValidator();
            TransactionRuleRequest.ConditionRequest condition =
                    TransactionRuleRequest.ConditionRequest.builder()
                            .field(RuleConditionField.AMOUNT)
                            .operator(RuleConditionOperator.GREATER_THAN)
                            .value("twelve")
                            .build();
            for (String value : java.util.List.of("twelve", "NaN", "Infinity", "12,34")) {
                condition.setValue(value);
                assertThat(validator.validate(condition))
                        .as("amount condition %s", value)
                        .isNotEmpty();
            }
            for (String value :
                    java.util.List.of("0", "-1", " 12.34 ", "1e3", "0.000000000000000001")) {
                condition.setValue(value);
                assertThat(validator.validate(condition))
                        .as("amount condition %s", value)
                        .isEmpty();
            }
            condition.setField(RuleConditionField.DESCRIPTION);
            condition.setValue("twelve");
            assertThat(validator.validate(condition)).isEmpty();
        }
    }

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
