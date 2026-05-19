package org.openfinance.entity;

/**
 * Enumeration for transaction category types.
 *
 * <p>Categories are classified as either INCOME or EXPENSE to organize financial transactions and
 * facilitate budget tracking and reporting.
 *
 * <p><strong>Requirement REQ-2.10</strong>: Category management for income and expenses
 *
 * @see Category
 * @since 1.0
 */
public enum CategoryType {
    /**
     * Categories for income transactions.
     *
     * <p>Examples: Salary, Freelance, Investments, Gifts
     */
    INCOME,

    /**
     * Categories for expense transactions.
     *
     * <p>Examples: Groceries, Rent, Utilities, Entertainment
     */
    EXPENSE
}
