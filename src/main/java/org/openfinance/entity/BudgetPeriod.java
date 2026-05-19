package org.openfinance.entity;

/**
 * Budget period types for budget tracking.
 *
 * <p>Defines the frequency at which budgets are tracked and reset.
 *
 * <p><strong>Period Types:</strong>
 *
 * <ul>
 *   <li>{@link #WEEKLY} - Budget resets every week (7 days)
 *   <li>{@link #MONTHLY} - Budget resets every month (calendar month)
 *   <li>{@link #QUARTERLY} - Budget resets every quarter (3 months)
 *   <li>{@link #YEARLY} - Budget resets every year (12 months)
 * </ul>
 *
 * <p>Requirement REQ-2.9.1.1: Support multiple budget periods
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-02
 */
public enum BudgetPeriod {
    /** Weekly budget period (7 days). Resets every Sunday at midnight. */
    WEEKLY("Weekly", 7),

    /** Monthly budget period (calendar month). Resets on the 1st of each month. */
    MONTHLY("Monthly", 30),

    /** Quarterly budget period (3 months). Resets every 3 months (Jan 1, Apr 1, Jul 1, Oct 1). */
    QUARTERLY("Quarterly", 90),

    /** Yearly budget period (12 months). Resets on January 1st each year. */
    YEARLY("Yearly", 365);

    private final String displayName;
    private final int approximateDays;

    /**
     * Constructor for BudgetPeriod enum.
     *
     * @param displayName human-readable name of the period
     * @param approximateDays approximate number of days in the period
     */
    BudgetPeriod(String displayName, int approximateDays) {
        this.displayName = displayName;
        this.approximateDays = approximateDays;
    }

    /**
     * Gets the human-readable display name.
     *
     * @return the display name (e.g., "Monthly")
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the approximate number of days in this period.
     *
     * <p>Note: This is an approximation. Actual days may vary:
     *
     * <ul>
     *   <li>Monthly: 28-31 days depending on the month
     *   <li>Quarterly: 90-92 days depending on the quarter
     *   <li>Yearly: 365-366 days (leap years)
     * </ul>
     *
     * @return approximate days in the period
     */
    public int getApproximateDays() {
        return approximateDays;
    }

    /**
     * Returns a string representation of the budget period.
     *
     * @return the display name
     */
    @Override
    public String toString() {
        return displayName;
    }
}
