package org.openfinance.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing budget alert configuration and tracking.
 *
 * <p>Budget alerts notify users when their spending approaches or exceeds budget limits. Each alert
 * is associated with a specific budget and can be configured with a custom threshold percentage
 * (e.g., 75%, 90%, 100%).
 *
 * <p><strong>Alert Lifecycle:</strong>
 *
 * <ul>
 *   <li>Alert is created when user sets up budget monitoring
 *   <li>System checks budget progress after each transaction
 *   <li>Alert is triggered when spending exceeds threshold
 *   <li>lastTriggered timestamp tracks most recent alert
 *   <li>isEnabled allows temporary alert disabling without deletion
 * </ul>
 *
 * <p><strong>Example Use Cases:</strong>
 *
 * <ul>
 *   <li>Warning at 75% spent: "You've used 75% of your Groceries budget"
 *   <li>Critical at 90% spent: "Only €50 remaining in Entertainment budget"
 *   <li>Exceeded at 100%: "Budget exceeded! You've spent €520 of €500"
 * </ul>
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.9.4: Budget alert system with configurable thresholds
 *   <li>REQ-2.9.4.1: Trigger alerts when spending exceeds percentage
 *   <li>REQ-2.9.4.2: Enable/disable alerts per budget
 * </ul>
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(
        name = "budget_alerts",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_budget_threshold",
                    columnNames = {"budget_id", "threshold"})
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class BudgetAlert {

    /** Unique identifier for the budget alert. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The budget this alert is associated with.
     *
     * <p>When budget is deleted, associated alerts are also deleted (cascade).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "budget_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_alert_budget"))
    @NotNull(message = "Budget is required")
    private Budget budget;

    /**
     * Alert threshold as percentage of budget amount.
     *
     * <p>When spending reaches this percentage, alert is triggered. Common values: 50.00, 75.00,
     * 90.00, 100.00
     *
     * <p>Valid range: 1.00 to 150.00 (allows alerts for overspending)
     *
     * <p>Example: threshold=75.00 means alert when spent ≥ 75% of budget
     */
    @Column(name = "threshold", nullable = false, precision = 5, scale = 2)
    @NotNull(message = "Threshold is required")
    @DecimalMin(value = "1.00", message = "Threshold must be at least 1%")
    @DecimalMax(value = "150.00", message = "Threshold must not exceed 150%")
    private BigDecimal threshold;

    /**
     * Whether this alert is currently enabled.
     *
     * <p>When disabled (false), alert will not trigger even if threshold is exceeded. Allows
     * temporary disabling without deleting alert configuration.
     */
    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean isEnabled = true;

    /**
     * Timestamp when alert was last triggered.
     *
     * <p>Null if alert has never been triggered. Used to prevent duplicate notifications and track
     * alert frequency.
     *
     * <p>Reset to null when budget period resets (e.g., start of new month).
     */
    @Column(name = "last_triggered")
    private LocalDateTime lastTriggered;

    /**
     * Whether the user has read/acknowledged this alert.
     *
     * <p>Used to show unread alert count in UI. Set to false when alert triggers, true when user
     * views it.
     */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = true;

    /** Timestamp when the alert configuration was created. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp when the alert configuration was last updated. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Custom toString that avoids recursion and sensitive data exposure.
     *
     * @return String representation of the BudgetAlert
     */
    @Override
    public String toString() {
        Long budgetId = null;
        if (budget != null) {
            try {
                budgetId = budget.getId();
            } catch (Exception e) {
                // Ignore if budget proxy not initialized
            }
        }
        return "BudgetAlert{"
                + "id="
                + id
                + ", budgetId="
                + budgetId
                + ", threshold="
                + threshold
                + ", isEnabled="
                + isEnabled
                + ", lastTriggered="
                + lastTriggered
                + ", isRead="
                + isRead
                + ", createdAt="
                + createdAt
                + ", updatedAt="
                + updatedAt
                + '}';
    }

    /** Marks the alert as triggered with current timestamp. Also marks as unread to notify user. */
    public void trigger() {
        this.lastTriggered = LocalDateTime.now();
        this.isRead = false;
    }

    /** Marks the alert as read/acknowledged by user. */
    public void markAsRead() {
        this.isRead = true;
    }

    /** Resets the alert for a new budget period. Clears lastTriggered and marks as read. */
    public void reset() {
        this.lastTriggered = null;
        this.isRead = true;
    }
}
