package org.openfinance.entity;

/**
 * Enum representing the field of an imported transaction that a rule condition targets.
 *
 * <p>Requirement: REQ-TR-2.1
 */
public enum RuleConditionField {

    /**
     * The raw description / payee string of the imported transaction. Combines payee and memo for
     * matching (same as AutoCategorizationService analysis string).
     */
    DESCRIPTION,

    /** The numeric amount of the imported transaction (absolute value used for comparison). */
    AMOUNT,

    /** The derived transaction type: INCOME (positive amount) or EXPENSE (negative amount). */
    TRANSACTION_TYPE
}
