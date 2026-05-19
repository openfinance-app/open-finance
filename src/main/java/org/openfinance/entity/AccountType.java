package org.openfinance.entity;

/**
 * Enumeration of supported account types.
 *
 * <p>Requirement REQ-2.2: Users can create and manage different types of financial accounts to
 * track their assets and liabilities.
 */
public enum AccountType {
    /** Checking account - typically used for daily transactions. */
    CHECKING("Checking Account"),

    /** Savings account - used for storing funds and earning interest. */
    SAVINGS("Savings Account"),

    /** Credit card account - tracks credit card balances and transactions. */
    CREDIT_CARD("Credit Card"),

    /** Investment account - tracks stocks, bonds, mutual funds, etc. */
    INVESTMENT("Investment Account"),

    /** Cash account - physical cash holdings. */
    CASH("Cash"),

    /** Other account types not covered by the above categories. */
    OTHER("Other");

    private final String displayName;

    /**
     * Constructs an AccountType with the specified display name.
     *
     * @param displayName Human-readable name for the account type
     */
    AccountType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the human-readable display name for this account type.
     *
     * @return Display name
     */
    public String getDisplayName() {
        return displayName;
    }
}
