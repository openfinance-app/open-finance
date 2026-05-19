package org.openfinance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.RuleActionType;
import org.openfinance.entity.RuleConditionField;
import org.openfinance.entity.RuleConditionOperator;

/**
 * Request DTO for creating or updating a {@link org.openfinance.entity.TransactionRule}.
 *
 * <p>Used by {@code POST /api/v1/transaction-rules} and {@code PUT /api/v1/transaction-rules/{id}}.
 *
 * <p><strong>Requirement:</strong> REQ-TR-5.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRuleRequest {

    /** Human-readable name for the rule. Requirement: REQ-TR-1.1 */
    @NotBlank(message = "{rule.name.required}")
    @Size(max = 100, message = "{rule.name.max}")
    private String name;

    /**
     * Priority for rule evaluation order. Lower number = higher priority. Defaults to 0 when not
     * specified. Requirement: REQ-TR-1.1, REQ-TR-4.5
     */
    @Builder.Default private Integer priority = 0;

    /**
     * Whether the rule should be active and evaluated during import. Defaults to {@code true} when
     * not specified. Requirement: REQ-TR-1.1
     */
    @Builder.Default private Boolean isEnabled = true;

    /**
     * Condition logic: "AND" (all conditions must match) or "OR" (any condition matches). Defaults
     * to "AND" for backward compatibility. Requirement: REQ-TR-2.4
     */
    @Builder.Default private String conditionMatch = "AND";

    /**
     * Ordered list of conditions (all must match — AND logic). At least one condition is required.
     * Requirement: REQ-TR-2, REQ-TR-2.4
     */
    @NotEmpty(message = "{rule.conditions.empty}")
    @Valid
    private List<ConditionRequest> conditions;

    /**
     * Ordered list of actions to apply when conditions match. At least one action is required.
     * Requirement: REQ-TR-3
     */
    @NotEmpty(message = "{rule.actions.empty}")
    @Valid
    private List<ActionRequest> actions;

    // -----------------------------------------------------------------------
    // Nested request types
    // -----------------------------------------------------------------------

    /**
     * Request payload for a single rule condition. Requirement: REQ-TR-2.1, REQ-TR-2.2, REQ-TR-2.3
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionRequest {

        /** The field of the imported transaction to evaluate. Requirement: REQ-TR-2.1 */
        @jakarta.validation.constraints.NotNull(message = "{rule.condition.field.required}")
        private RuleConditionField field;

        /** The comparison operator. Requirement: REQ-TR-2.2 */
        @jakarta.validation.constraints.NotNull(message = "{rule.condition.operator.required}")
        private RuleConditionOperator operator;

        /** The value to compare against. Requirement: REQ-TR-2.3 */
        @NotBlank(message = "{rule.condition.value.required}")
        private String value;

        /** Display/evaluation order of this condition within the rule. */
        @Builder.Default private Integer sortOrder = 0;
    }

    /** Request payload for a single rule action. Requirement: REQ-TR-3.2 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionRequest {

        /** The type of action to perform. Requirement: REQ-TR-3.2 */
        @jakarta.validation.constraints.NotNull(message = "{rule.action.type.required}")
        private RuleActionType actionType;

        /**
         * Primary parameter for the action. Required for: SET_CATEGORY, SET_PAYEE, ADD_TAG,
         * SET_DESCRIPTION, SET_AMOUNT, ADD_SPLIT.
         */
        private String actionValue;

        /** Secondary parameter. Used by ADD_SPLIT: amount as BigDecimal string. */
        private String actionValue2;

        /** Tertiary parameter. Used by ADD_SPLIT: optional description for the split line. */
        private String actionValue3;

        /** Display/application order of this action within the rule. */
        @Builder.Default private Integer sortOrder = 0;
    }
}
