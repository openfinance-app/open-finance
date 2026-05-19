package org.openfinance.mapper;

import java.math.BigDecimal;
import org.openfinance.dto.BudgetAlertResponse;
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetAlert;
import org.openfinance.entity.Category;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between BudgetAlert entities and DTOs.
 *
 * <p>Handles mapping of budget alert data with additional context information such as budget name
 * and category name for UI display.
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.9.4: Budget alert system
 * </ul>
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Component
public class BudgetAlertMapper {

    /**
     * Converts BudgetAlert entity to response DTO.
     *
     * <p>Includes budget and category names from related entities if available. Does not include
     * current spending percentage or message - those should be added separately by the service
     * layer if needed.
     *
     * @param alert the budget alert entity
     * @return the alert response DTO
     */
    public BudgetAlertResponse toResponse(BudgetAlert alert) {
        if (alert == null) {
            return null;
        }

        Budget budget = alert.getBudget();
        Category category = (budget != null) ? budget.getCategory() : null;

        return BudgetAlertResponse.builder()
                .id(alert.getId())
                .budgetId(budget != null ? budget.getId() : null)
                .budgetName(category != null ? category.getName() + " Budget" : null)
                .categoryName(category != null ? category.getName() : null)
                .threshold(alert.getThreshold())
                .isEnabled(alert.isEnabled())
                .lastTriggered(alert.getLastTriggered())
                .isRead(alert.isRead())
                .createdAt(alert.getCreatedAt())
                .updatedAt(alert.getUpdatedAt())
                .build();
    }

    /**
     * Converts BudgetAlert entity to response DTO with current spending data.
     *
     * <p>Includes current spending percentage and generates alert message.
     *
     * @param alert the budget alert entity
     * @param currentSpentPercentage the current spending percentage
     * @return the alert response DTO with spending context
     */
    public BudgetAlertResponse toResponseWithProgress(
            BudgetAlert alert, BigDecimal currentSpentPercentage) {
        BudgetAlertResponse response = toResponse(alert);

        if (response != null && currentSpentPercentage != null) {
            response.setCurrentSpentPercentage(currentSpentPercentage);

            // Generate message if alert has been triggered
            if (alert.getLastTriggered() != null && response.getCategoryName() != null) {
                response.setMessage(
                        generateAlertMessage(
                                response.getCategoryName(),
                                currentSpentPercentage,
                                alert.getThreshold()));
            }
        }

        return response;
    }

    /**
     * Generates human-readable alert message.
     *
     * <p>Examples:
     *
     * <ul>
     *   <li>75% threshold, 78% spent: "Warning: You've spent 78% of your Groceries budget"
     *   <li>100% threshold, 105% spent: "Alert: Budget exceeded! You've spent 105% of your
     *       Groceries budget"
     * </ul>
     *
     * @param categoryName the category name
     * @param spentPercentage the current spending percentage
     * @param threshold the alert threshold
     * @return formatted alert message
     */
    private String generateAlertMessage(
            String categoryName, BigDecimal spentPercentage, BigDecimal threshold) {
        String prefix;
        if (threshold.compareTo(BigDecimal.valueOf(100)) >= 0
                && spentPercentage.compareTo(BigDecimal.valueOf(100)) >= 0) {
            prefix = "Alert: Budget exceeded!";
        } else if (threshold.compareTo(BigDecimal.valueOf(90)) >= 0) {
            prefix = "Critical:";
        } else {
            prefix = "Warning:";
        }

        return String.format(
                "%s You've spent %.1f%% of your %s budget", prefix, spentPercentage, categoryName);
    }
}
