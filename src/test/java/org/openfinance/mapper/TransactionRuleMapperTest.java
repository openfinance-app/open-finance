package org.openfinance.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.dto.TransactionRuleRequest;
import org.openfinance.dto.TransactionRuleResponse;
import org.openfinance.entity.RuleActionType;
import org.openfinance.entity.RuleConditionField;
import org.openfinance.entity.RuleConditionOperator;
import org.openfinance.entity.TransactionRule;

@DisplayName("Transaction rule mapping preserves ordered actions and explicit false or zero values")
class TransactionRuleMapperTest {
    private final TransactionRuleMapper mapper = new TransactionRuleMapper();

    @ParameterizedTest
    @CsvSource(
            value = {
                "NULL,NULL,NULL,NULL,0,true,AND,0",
                "0,false,OR,0,0,false,OR,0",
                "12,true,AND,3,12,true,AND,3"
            },
            nullValues = "NULL")
    void defaultsOnlyOmittedSettingsAndRoundTripsEveryActionValue(
            Integer priority,
            Boolean enabled,
            String match,
            Integer order,
            int expectedPriority,
            boolean expectedEnabled,
            String expectedMatch,
            int expectedOrder) {
        TransactionRuleRequest request =
                TransactionRuleRequest.builder()
                        .name("Income split")
                        .priority(priority)
                        .isEnabled(enabled)
                        .conditionMatch(match)
                        .conditions(
                                List.of(
                                        TransactionRuleRequest.ConditionRequest.builder()
                                                .field(RuleConditionField.AMOUNT)
                                                .operator(RuleConditionOperator.GREATER_OR_EQUAL)
                                                .value("9007199254740993.01")
                                                .sortOrder(order)
                                                .build()))
                        .actions(
                                List.of(
                                        TransactionRuleRequest.ActionRequest.builder()
                                                .actionType(RuleActionType.ADD_SPLIT)
                                                .actionValue("42")
                                                .actionValue2("0.01")
                                                .actionValue3("Travel allocation")
                                                .sortOrder(order)
                                                .build()))
                        .build();
        TransactionRule entity = mapper.toEntity(7L, request);
        assertThat(entity.getUserId()).isEqualTo(7L);
        assertThat(entity.getId()).isNull();
        TransactionRuleResponse response = mapper.toResponse(entity);
        assertThat(response.getPriority()).isEqualTo(expectedPriority);
        assertThat(response.getIsEnabled()).isEqualTo(expectedEnabled);
        assertThat(response.getConditionMatch()).isEqualTo(expectedMatch);
        assertThat(response.getConditions())
                .singleElement()
                .satisfies(
                        condition -> {
                            assertThat(condition.getField()).isEqualTo(RuleConditionField.AMOUNT);
                            assertThat(condition.getOperator())
                                    .isEqualTo(RuleConditionOperator.GREATER_OR_EQUAL);
                            assertThat(condition.getValue()).isEqualTo("9007199254740993.01");
                            assertThat(condition.getSortOrder()).isEqualTo(expectedOrder);
                        });
        assertThat(response.getActions())
                .singleElement()
                .satisfies(
                        action -> {
                            assertThat(action.getActionValue()).isEqualTo("42");
                            assertThat(action.getActionValue2()).isEqualTo("0.01");
                            assertThat(action.getActionValue3()).isEqualTo("Travel allocation");
                            assertThat(action.getSortOrder()).isEqualTo(expectedOrder);
                        });
    }

    @Test
    void incompleteDraftsHaveExplicitEmptyConditionAndActionLists() {
        TransactionRule entity =
                mapper.toEntity(7L, TransactionRuleRequest.builder().name("Draft").build());
        assertThat(entity.getConditions()).isEmpty();
        assertThat(entity.getActions()).isEmpty();
        entity.setConditions(null);
        entity.setActions(null);
        entity.setConditionMatch(null);
        TransactionRuleResponse response = mapper.toResponse(entity);
        assertThat(response.getConditions()).isEmpty();
        assertThat(response.getActions()).isEmpty();
        assertThat(response.getConditionMatch()).isEqualTo("AND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "request", "entity"})
    void refusesMappingWithoutRequiredIdentityOrInput(String missing) {
        assertThatThrownBy(
                        () -> {
                            switch (missing) {
                                case "owner" -> mapper.toEntity(null, new TransactionRuleRequest());
                                case "request" -> mapper.toEntity(7L, null);
                                case "entity" -> mapper.toResponse(null);
                                default -> throw new AssertionError(missing);
                            }
                        })
                .isInstanceOf(IllegalArgumentException.class);
    }
}
