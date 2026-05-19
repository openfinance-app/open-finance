package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for budget alert response.
 *
 * <p>Contains complete alert information including trigger history and budget context.
 *
 * <p><strong>Example JSON:</strong>
 *
 * <pre>
 * {
 *   "id": "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d",
 *   "budgetId": 123,
 *   "budgetName": "Groceries",
 *   "threshold": 75.00,
 *   "isEnabled": true,
 *   "lastTriggered": "2026-02-01T14:30:00",
 *   "isRead": false,
 *   "currentSpentPercentage": 78.50,
 *   "message": "Warning: You've spent 78.5% of your Groceries budget",
 *   "createdAt": "2026-01-01T10:00:00",
 *   "updatedAt": "2026-02-01T14:30:00"
 * }
 * </pre>
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.9.4: Budget alert system
 *   <li>REQ-2.9.4.1: Alert notification and tracking
 * </ul>
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetAlertResponse {

    /** Unique identifier for the alert. */
    private UUID id;

    /** ID of the budget this alert monitors. */
    private Long budgetId;

    /** Name of the budget (from category name). Included for convenience in UI display. */
    private String budgetName;

    /** Name of the category associated with the budget. Included for convenience in UI display. */
    private String categoryName;

    /** Alert threshold as percentage (e.g., 75.00 = 75%). */
    private BigDecimal threshold;

    /** Whether this alert is currently enabled. */
    private boolean isEnabled;

    /** Timestamp when alert was last triggered. Null if never triggered. */
    private LocalDateTime lastTriggered;

    /** Whether user has read/acknowledged this alert. */
    private boolean isRead;

    /**
     * Current spending percentage for the budget. Included to show how close to threshold. Null if
     * budget data not available.
     */
    private BigDecimal currentSpentPercentage;

    /**
     * Human-readable alert message. Example: "Warning: You've spent 78.5% of your Groceries budget"
     * Null if not triggered or budget data not available.
     */
    private String message;

    /** Timestamp when alert configuration was created. */
    private LocalDateTime createdAt;

    /** Timestamp when alert configuration was last updated. */
    private LocalDateTime updatedAt;
}
