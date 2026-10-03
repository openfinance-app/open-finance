package org.openfinance.entity;

/**
 * Enum representing the comparison operator for a rule condition.
 *
 * <p>String operators apply to {@link RuleConditionField#DESCRIPTION}.
 *
 * <p>Numeric operators apply to {@link RuleConditionField#AMOUNT}.
 *
 * <p>Both EQUALS and NOT_EQUALS apply to all field types.
 *
 * <p>Requirement: REQ-TR-2.2
 */
public enum RuleConditionOperator {

    // ---- String operators ----

    /** Case-insensitive substring match. Requirement: REQ-TR-2.2 */
    CONTAINS,

    /** Does not contain substring (case-insensitive). Requirement: REQ-TR-2.2 */
    NOT_CONTAINS,

    // ---- Universal operators ----

    /**
     * Exact match (case-insensitive for strings, exact BigDecimal for amounts). Requirement:
     * REQ-TR-2.2
     */
    EQUALS,

    /** Not an exact match. Requirement: REQ-TR-2.2 */
    NOT_EQUALS,

    // ---- Numeric operators ----

    /** Amount strictly greater than the condition value. Requirement: REQ-TR-2.2 */
    GREATER_THAN,

    /** Amount strictly less than the condition value. Requirement: REQ-TR-2.2 */
    LESS_THAN,

    /** Amount greater than or equal to the condition value. Requirement: REQ-TR-2.2 */
    GREATER_OR_EQUAL,

    /** Amount less than or equal to the condition value. Requirement: REQ-TR-2.2 */
    LESS_OR_EQUAL;

    /** Whether this operator is meaningful for the selected condition field. */
    public boolean supports(RuleConditionField field) {
        if (field == null) {
            return false;
        }
        return switch (this) {
            case EQUALS, NOT_EQUALS -> true;
            case CONTAINS, NOT_CONTAINS -> field == RuleConditionField.DESCRIPTION;
            case GREATER_THAN, LESS_THAN, GREATER_OR_EQUAL, LESS_OR_EQUAL -> field
                    == RuleConditionField.AMOUNT;
        };
    }
}
