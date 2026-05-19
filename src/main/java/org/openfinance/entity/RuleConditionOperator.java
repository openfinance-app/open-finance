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
    LESS_OR_EQUAL
}
