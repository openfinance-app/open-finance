package org.openfinance.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
            condition.setOperator(RuleConditionOperator.CONTAINS);
            condition.setValue("twelve");
            assertThat(validator.validate(condition)).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource({
        "DESCRIPTION,CONTAINS,true",
        "DESCRIPTION,NOT_CONTAINS,true",
        "DESCRIPTION,EQUALS,true",
        "DESCRIPTION,NOT_EQUALS,true",
        "DESCRIPTION,GREATER_THAN,false",
        "DESCRIPTION,LESS_THAN,false",
        "DESCRIPTION,GREATER_OR_EQUAL,false",
        "DESCRIPTION,LESS_OR_EQUAL,false",
        "AMOUNT,CONTAINS,false",
        "AMOUNT,NOT_CONTAINS,false",
        "AMOUNT,EQUALS,true",
        "AMOUNT,NOT_EQUALS,true",
        "AMOUNT,GREATER_THAN,true",
        "AMOUNT,LESS_THAN,true",
        "AMOUNT,GREATER_OR_EQUAL,true",
        "AMOUNT,LESS_OR_EQUAL,true",
        "TRANSACTION_TYPE,CONTAINS,false",
        "TRANSACTION_TYPE,NOT_CONTAINS,false",
        "TRANSACTION_TYPE,EQUALS,true",
        "TRANSACTION_TYPE,NOT_EQUALS,true",
        "TRANSACTION_TYPE,GREATER_THAN,false",
        "TRANSACTION_TYPE,LESS_THAN,false",
        "TRANSACTION_TYPE,GREATER_OR_EQUAL,false",
        "TRANSACTION_TYPE,LESS_OR_EQUAL,false"
    })
    void validatesFieldOperatorCompatibility(
            RuleConditionField field, RuleConditionOperator operator, boolean valid) {
        try (jakarta.validation.ValidatorFactory factory =
                Validation.buildDefaultValidatorFactory()) {
            TransactionRuleRequest.ConditionRequest condition =
                    TransactionRuleRequest.ConditionRequest.builder()
                            .field(field)
                            .operator(operator)
                            .value(field == RuleConditionField.TRANSACTION_TYPE ? "DEBIT" : "20")
                            .build();
            assertThat(factory.getValidator().validate(condition).isEmpty()).isEqualTo(valid);
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
