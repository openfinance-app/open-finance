package org.openfinance.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.RuleActionType;
import org.openfinance.entity.RuleConditionField;
import org.openfinance.entity.RuleConditionOperator;

/**
 * Response DTO returned by the transaction-rules REST API.
 *
 * <p>Used by all read operations: {@code GET /api/v1/transaction-rules} and {@code GET
 * /api/v1/transaction-rules/{id}}.
 *
 * <p><strong>Requirement:</strong> REQ-TR-5.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRuleResponse {

    /** Unique identifier of the rule. */
    private Long id;

    /** Human-readable name of the rule. Requirement: REQ-TR-1.1 */
    private String name;

    /** Priority — lower number means evaluated first. Requirement: REQ-TR-1.1, REQ-TR-4.5 */
    private Integer priority;

    /** Whether the rule is currently active. Requirement: REQ-TR-1.1, REQ-TR-4.4 */
    private Boolean isEnabled;

    /**
     * Condition match logic: "AND" (all must match) or "OR" (any matches). Requirement: REQ-TR-2.4
     */
    private String conditionMatch;

    /** Ordered list of conditions that must all match. Requirement: REQ-TR-2 */
    private List<ConditionResponse> conditions;

    /** Ordered list of actions applied when all conditions match. Requirement: REQ-TR-3 */
    private List<ActionResponse> actions;

    /** Timestamp when the rule was created. */
    private LocalDateTime createdAt;

    /** Timestamp of the most recent update. */
    private LocalDateTime updatedAt;

    // -----------------------------------------------------------------------
    // Nested response types
    // -----------------------------------------------------------------------

    /**
     * Response payload for a single rule condition. Requirement: REQ-TR-2.1, REQ-TR-2.2, REQ-TR-2.3
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionResponse {

        /** Unique identifier of the condition. */
        private Long id;

        /** The field being evaluated. Requirement: REQ-TR-2.1 */
        private RuleConditionField field;

        /** The comparison operator. Requirement: REQ-TR-2.2 */
        private RuleConditionOperator operator;

        /** The value to compare against. Requirement: REQ-TR-2.3 */
        private String value;

        /** Display order within the rule. */
        private Integer sortOrder;
    }

    /** Response payload for a single rule action. Requirement: REQ-TR-3.2 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionResponse {

        /** Unique identifier of the action. */
        private Long id;

        /** The type of action. Requirement: REQ-TR-3.2 */
        private RuleActionType actionType;

        /** Primary action parameter. */
        private String actionValue;

        /** Secondary action parameter (ADD_SPLIT: amount). */
        private String actionValue2;

        /** Tertiary action parameter (ADD_SPLIT: description). */
        private String actionValue3;

        /** Application order within the rule. */
        private Integer sortOrder;
    }
}
