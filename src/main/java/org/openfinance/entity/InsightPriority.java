package org.openfinance.entity;

/**
 * Enum representing the priority/urgency level of a financial insight.
 *
 * <p>Priority levels help users understand which insights require immediate attention versus
 * informational notifications.
 *
 * <ul>
 *   <li><strong>HIGH</strong>: Requires immediate attention (e.g., budget exceeded, unusual
 *       activity, low balance)
 *   <li><strong>MEDIUM</strong>: Important but not urgent (e.g., savings opportunity, trend
 *       notification, goal progress)
 *   <li><strong>LOW</strong>: Informational only (e.g., monthly summary, general tips, educational
 *       content)
 * </ul>
 *
 * @since Sprint 11 - AI Assistant Integration (Task 11.4)
 */
public enum InsightPriority {

    /**
     * High priority - requires immediate attention.
     *
     * <p><strong>Examples:</strong>
     *
     * <ul>
     *   <li>Budget exceeded by 20% or more
     *   <li>Unusual spending detected (50%+ increase)
     *   <li>Account balance critically low (below $100)
     *   <li>High-interest debt payment due soon
     * </ul>
     *
     * <p><strong>Display:</strong> Red badge, shown at top of insight list
     */
    HIGH("High Priority", "Requires immediate attention", 1),

    /**
     * Medium priority - important but not urgent.
     *
     * <p><strong>Examples:</strong>
     *
     * <ul>
     *   <li>Savings opportunity identified
     *   <li>Budget warning (75-99% spent)
     *   <li>Investment rebalancing suggestion
     *   <li>Goal progress milestone reached
     * </ul>
     *
     * <p><strong>Display:</strong> Yellow/orange badge, middle of insight list
     */
    MEDIUM("Medium Priority", "Important but not urgent", 2),

    /**
     * Low priority - informational only.
     *
     * <p><strong>Examples:</strong>
     *
     * <ul>
     *   <li>Monthly spending summary
     *   <li>General financial tips
     *   <li>Educational content
     *   <li>Positive trend notifications
     * </ul>
     *
     * <p><strong>Display:</strong> Blue/gray badge, bottom of insight list
     */
    LOW("Low Priority", "Informational only", 3);

    private final String displayName;
    private final String description;
    private final int sortOrder;

    InsightPriority(String displayName, String description, int sortOrder) {
        this.displayName = displayName;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    /**
     * Get human-readable display name for this priority level.
     *
     * @return Display name (e.g., "High Priority")
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get description explaining this priority level.
     *
     * @return Description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get sort order for priority (1 = highest, 3 = lowest).
     *
     * <p>Used for sorting insights with HIGH priority first, LOW priority last.
     *
     * @return Sort order (1-3)
     */
    public int getSortOrder() {
        return sortOrder;
    }
}
