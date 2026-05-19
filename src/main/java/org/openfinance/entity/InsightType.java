package org.openfinance.entity;

/**
 * Enum representing the type/category of financial insight.
 *
 * <p>Each type corresponds to a different aspect of financial analysis:
 *
 * <ul>
 *   <li><strong>SPENDING_ANOMALY</strong>: Unusual spending patterns detected (e.g., 50% increase
 *       in category)
 *   <li><strong>BUDGET_WARNING</strong>: Budget limits approaching or exceeded
 *   <li><strong>BUDGET_RECOMMENDATION</strong>: Suggestions for budget adjustments based on actual
 *       spending
 *   <li><strong>SAVINGS_OPPORTUNITY</strong>: Potential areas to reduce spending or optimize
 *       subscriptions
 *   <li><strong>INVESTMENT_SUGGESTION</strong>: Portfolio rebalancing or investment recommendations
 *   <li><strong>DEBT_ALERT</strong>: High-interest debt or payment opportunities
 *   <li><strong>CASH_FLOW_WARNING</strong>: Potential cash flow issues or low balance alerts
 *   <li><strong>TAX_OPTIMIZATION</strong>: Tax-advantaged savings or deduction opportunities
 *   <li><strong>GOAL_PROGRESS</strong>: Progress updates toward financial goals
 *   <li><strong>GENERAL_TIP</strong>: General financial advice or education
 * </ul>
 *
 * @since Sprint 11 - AI Assistant Integration (Task 11.4)
 */
public enum InsightType {

    /**
     * Unusual spending pattern detected (e.g., 50% increase in restaurant spending).
     *
     * <p><strong>Example:</strong>
     *
     * <pre>
     * "Your restaurant spending is 45% higher than last month ($520 vs $360)"
     * </pre>
     */
    SPENDING_ANOMALY("Spending Anomaly", "Unusual spending patterns detected"),

    /**
     * Budget limit approaching or exceeded.
     *
     * <p><strong>Example:</strong>
     *
     * <pre>
     * "You've spent 85% of your grocery budget for this month"
     * "You've exceeded your entertainment budget by $120"
     * </pre>
     */
    BUDGET_WARNING("Budget Warning", "Budget limits approaching or exceeded"),

    /**
     * Suggestion to adjust budget based on actual spending patterns.
     *
     * <p><strong>Example:</strong>
     *
     * <pre>
     * "Your utilities spending averages $150/month. Consider increasing your $100 budget."
     * </pre>
     */
    BUDGET_RECOMMENDATION("Budget Recommendation", "Suggested budget adjustments"),

    /**
     * Opportunity to save money by reducing or optimizing spending.
     *
     * <p><strong>Example:</strong>
     *
     * <pre>
     * "You have 3 streaming services costing $45/month. Consider consolidating."
     * "Your gym membership hasn't been used in 3 months. Consider canceling."
     * </pre>
     */
    SAVINGS_OPPORTUNITY("Savings Opportunity", "Potential areas to save money"),

    /**
     * Investment or portfolio recommendation.
     *
     * <p><strong>Example:</strong>
     *
     * <pre>
     * "Your portfolio is 80% stocks. Consider rebalancing to reduce risk."
     * </pre>
     */
    INVESTMENT_SUGGESTION("Investment Suggestion", "Portfolio or investment recommendations"),

    /**
     * Alert about high-interest debt or payment opportunities.
     *
     * <p><strong>Example:</strong>
     *
     * <pre>
     * "Your credit card has a $2,500 balance at 19.9% APR. Consider paying down."
     * </pre>
     */
    DEBT_ALERT("Debt Alert", "High-interest debt or payment reminders"),

    /**
     * Warning about potential cash flow issues or low balances.
     *
     * <p><strong>Example:</strong>
     *
     * <pre>
     * "Your checking account balance is lower than usual. Upcoming bills may cause overdraft."
     * </pre>
     */
    CASH_FLOW_WARNING("Cash Flow Warning", "Potential cash flow issues"),

    /**
     * Tax-advantaged savings or deduction opportunity.
     *
     * <p><strong>Example:</strong>
     *
     * <pre>
     * "You're eligible for up to $3,000 more in IRA contributions this year."
     * </pre>
     */
    TAX_OPTIMIZATION("Tax Optimization", "Tax-advantaged savings opportunities"),

    /**
     * Progress update toward a financial goal.
     *
     * <p><strong>Example:</strong>
     *
     * <pre>
     * "You're 75% toward your vacation fund goal of $3,000!"
     * </pre>
     */
    GOAL_PROGRESS("Goal Progress", "Progress toward financial goals"),

    /**
     * General financial tip or educational content.
     *
     * <p><strong>Example:</strong>
     *
     * <pre>
     * "Did you know? The 50/30/20 rule suggests 50% needs, 30% wants, 20% savings."
     * </pre>
     */
    GENERAL_TIP("General Tip", "General financial advice"),

    /**
     * An unusual or potentially fraudulent transaction detected by the daily anomaly-detection
     * scheduler.
     *
     * <p><strong>Triggers:</strong>
     *
     * <ul>
     *   <li>First-time payee — new merchant/payer never seen before for this user
     *   <li>Unusually large amount — amount exceeds mean + 2.5 × std-dev for this payee
     *   <li>Amount 3× the payee average — even without enough history to compute std-dev
     * </ul>
     *
     * <p><strong>Example:</strong>
     *
     * <pre>
     * "A transaction of 850.00 EUR to 'ACME Corp' is the first time this payee appears."
     * "A transaction of 1,200.00 USD to 'Netflix' is 420% higher than your usual amount."
     * </pre>
     */
    UNUSUAL_TRANSACTION(
            "Unusual Transaction", "Potentially unusual or suspicious transaction detected"),

    /**
     * Comparison of user's income or net worth against regional/country averages.
     *
     * <p><strong>Example:</strong>
     *
     * <pre>
     * "Your monthly income of $5,200 is 15% above the national median for the United States ($4,500)."
     * "Your net worth of $120,000 is in the top 40% for your age group in France."
     * </pre>
     */
    REGION_COMPARISON("Region Comparison", "Income or net worth compared to regional averages"),

    /**
     * Estimated annual tax liability with potential deductions and credits.
     *
     * <p><strong>Example:</strong>
     *
     * <pre>
     * "Based on your annual income of $62,400, your estimated tax liability is $9,800."
     * "You may be eligible for $1,200 in deductions from charitable donations."
     * </pre>
     */
    TAX_OBLIGATION("Tax Obligation", "Estimated tax liability and potential deductions"),

    /**
     * Analysis of recurring subscriptions and bills with competitor pricing suggestions.
     *
     * <p><strong>Example:</strong>
     *
     * <pre>
     * "You have 5 recurring subscriptions totaling $89/month. Your streaming services alone cost $45/month."
     * "Consider switching your internet plan — competitors offer similar speeds for $20/month less."
     * </pre>
     */
    RECURRING_BILLING(
            "Recurring Billing",
            "Recurring subscription and billing analysis with competitor pricing");

    private final String displayName;
    private final String description;

    InsightType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Get human-readable display name for this insight type.
     *
     * @return Display name (e.g., "Spending Anomaly")
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get description explaining this insight type.
     *
     * @return Description
     */
    public String getDescription() {
        return description;
    }
}
