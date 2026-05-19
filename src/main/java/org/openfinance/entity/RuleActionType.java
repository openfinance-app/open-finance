package org.openfinance.entity;

/**
 * Enum representing the type of action applied when a transaction rule fires.
 *
 * <p>Requirement: REQ-TR-3.2
 */
public enum RuleActionType {

    /**
     * Sets the category of the imported transaction. Parameter: actionValue = category name.
     * Requirement: REQ-TR-3.2
     */
    SET_CATEGORY,

    /**
     * Sets the payee of the imported transaction. Parameter: actionValue = payee name. Requirement:
     * REQ-TR-3.2
     */
    SET_PAYEE,

    /**
     * Appends a tag to the transaction's tag list. Parameter: actionValue = tag string.
     * Requirement: REQ-TR-3.2
     */
    ADD_TAG,

    /**
     * Overrides the memo / description field of the imported transaction. Parameter: actionValue =
     * description text. Requirement: REQ-TR-3.2
     */
    SET_DESCRIPTION,

    /**
     * Overrides the amount of the imported transaction. Parameter: actionValue = BigDecimal string
     * representation of the new amount. Requirement: REQ-TR-3.2
     */
    SET_AMOUNT,

    /**
     * Adds a split entry to the imported transaction. Parameters: actionValue = category name for
     * the split actionValue2 = amount (BigDecimal string) actionValue3 = optional description for
     * the split Requirement: REQ-TR-3.2, REQ-TR-3.3
     */
    ADD_SPLIT,

    /**
     * Marks the transaction to be skipped during import. No parameters required. Requirement:
     * REQ-TR-3.2, REQ-TR-4.7
     */
    SKIP_TRANSACTION
}
