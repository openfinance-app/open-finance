package org.openfinance.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.BudgetPeriod;
import org.openfinance.validation.ValidCurrency;

/**
 * Data Transfer Object for creating or updating a budget.
 *
 * <p>This DTO is used for both POST (create) and PUT (update) operations. Validation annotations
 * ensure data integrity before processing.
 *
 * <p>Requirement REQ-2.9.1.1: Budget creation with category, period, amount, rollover
 *
 * <p>Requirement REQ-2.9.1.2: Budget tracking and management
 *
 * @see org.openfinance.entity.Budget
 * @see BudgetResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetRequest {

    /**
     * ID of the category this budget applies to.
     *
     * <p>Each budget is associated with a single category. The category must exist and belong to
     * the user.
     *
     * <p>Requirement REQ-2.9.1.1: Category-based budgets
     */
    @NotNull(message = "{budget.category.required}")
    @Positive(message = "{budget.category.positive}")
    private Long categoryId;

    /**
     * Budget amount in the user's chosen currency.
     *
     * <p>This field will be encrypted before storing in the database. Must be positive and have at
     * most 4 decimal places.
     *
     * <p>Requirement REQ-2.9.1.1: Budget amount specification
     */
    @NotNull(message = "{budget.amount.required}")
    @DecimalMin(value = "0.01", message = "{budget.amount.min}")
    @DecimalMax(value = "999999999.9999", message = "{budget.amount.max}")
    @Digits(integer = 9, fraction = 4, message = "{budget.amount.digits}")
    private BigDecimal amount;

    /**
     * Currency code in ISO 4217 format (e.g., "USD", "EUR", "GBP").
     *
     * <p>Should match the user's base currency or the category's typical currency. Defaults to
     * user's base currency if not specified.
     *
     * <p>Requirement REQ-2.8: Multi-currency support
     */
    @NotBlank(message = "{account.currency.required}")
    @ValidCurrency
    private String currency;

    /**
     * Budget period type (WEEKLY, MONTHLY, QUARTERLY, YEARLY).
     *
     * <p>Determines the time frame for this budget. Cannot be changed after budget creation.
     *
     * <p>Requirement REQ-2.9.1.1: Period type selection
     */
    @NotNull(message = "{budget.period.required}")
    private BudgetPeriod period;

    /**
     * Start date of the budget period.
     *
     * <p>Budget tracking begins on this date (inclusive). Must be before or equal to endDate.
     *
     * <p>Requirement REQ-2.9.1.2: Budget period date range
     */
    @NotNull(message = "{budget.start.required}")
    private LocalDate startDate;

    /**
     * End date of the budget period.
     *
     * <p>Budget tracking ends on this date (inclusive). Must be after or equal to startDate.
     *
     * <p>Requirement REQ-2.9.1.2: Budget period date range
     */
    @NotNull(message = "{budget.end.required}")
    private LocalDate endDate;

    /**
     * Whether to carry unused budget amount to the next period.
     *
     * <p>If true and user spends less than budgeted, the remaining amount will be added to the next
     * period's budget.
     *
     * <p>Example: Budget $500, spent $400, rollover=true → next period starts with $600
     *
     * <p>Requirement REQ-2.9.1.1: Rollover option support
     */
    @NotNull(message = "{budget.rollover.required}")
    private Boolean rollover;

    /**
     * Optional notes or description for the budget.
     *
     * <p>Can include budget goals, strategies, or reminders. Maximum 500 characters.
     */
    @Size(max = 500, message = "{budget.notes.max}")
    private String notes;

    /**
     * Validates that end date is not before start date.
     *
     * <p>Called by Jakarta Validation after field validation.
     *
     * @return true if dates are valid, false otherwise
     */
    @AssertTrue(message = "{budget.end.after.start}")
    public boolean isValidDateRange() {
        if (startDate == null || endDate == null) {
            return true; // Let @NotNull handle null validation
        }
        return !endDate.isBefore(startDate);
    }
}
