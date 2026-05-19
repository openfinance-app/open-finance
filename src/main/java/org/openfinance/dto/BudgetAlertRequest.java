package org.openfinance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating or updating a budget alert.
 *
 * <p>Budget alerts notify users when spending approaches or exceeds budgeted amounts. Common
 * threshold values:
 *
 * <ul>
 *   <li>50.00 - Early warning at 50% spent
 *   <li>75.00 - Standard warning at 75% spent
 *   <li>90.00 - Critical warning at 90% spent
 *   <li>100.00 - Budget exceeded alert
 * </ul>
 *
 * <p><strong>Example JSON:</strong>
 *
 * <pre>
 * {
 *   "threshold": 75.00,
 *   "isEnabled": true
 * }
 * </pre>
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.9.4: Budget alert configuration
 *   <li>REQ-2.9.4.2: Enable/disable alerts per budget
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
public class BudgetAlertRequest {

    /**
     * Alert threshold as percentage of budget amount.
     *
     * <p>When spending reaches this percentage, the alert triggers. Valid range: 1.00 to 150.00
     * (allows alerts for overspending)
     *
     * <p>Example: 75.00 means "alert when 75% of budget is spent"
     */
    @NotNull(message = "{budget.alert.threshold.required}")
    @DecimalMin(value = "1.00", message = "{budget.alert.threshold.min}")
    @DecimalMax(value = "150.00", message = "{budget.alert.threshold.max}")
    private BigDecimal threshold;

    /**
     * Whether this alert is currently enabled.
     *
     * <p>When false, alert will not trigger even if threshold is exceeded. Defaults to true if not
     * specified.
     */
    @Builder.Default private Boolean isEnabled = true;
}
