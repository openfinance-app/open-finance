package org.openfinance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for system notifications.
 *
 * <p>Represents various types of notifications that require user attention:
 *
 * <ul>
 *   <li>STALE_QUOTES - Asset quotes not updated recently
 *   <li>STALE_EXCHANGE_RATES - Exchange rates not updated for more than 2 days
 *   <li>UNCATEGORIZED_TRANSACTIONS - Transactions without categories
 *   <li>UNLINKED_PAYEE - Transactions without payees
 *   <li>LOW_BALANCE - Accounts with very low balance
 *   <li>BUDGET_ALERT - Budget threshold alerts
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    /** Notification type */
    private NotificationType type;

    /** Notification title */
    private String title;

    /** Notification message */
    private String message;

    /** Count of items (e.g., number of uncategorized transactions) */
    private Integer count;

    /** Action URL or identifier for handling the notification */
    private String actionUrl;

    /** Action label (e.g., "Update Prices", "Categorize") */
    private String actionLabel;

    /** Severity level */
    private NotificationSeverity severity;

    /** Additional context data (JSON string) */
    private String metadata;

    public enum NotificationType {
        STALE_QUOTES,
        STALE_EXCHANGE_RATES,
        UNCATEGORIZED_TRANSACTIONS,
        UNLINKED_PAYEE,
        LOW_BALANCE,
        BUDGET_ALERT
    }

    public enum NotificationSeverity {
        INFO,
        WARNING,
        CRITICAL
    }
}
