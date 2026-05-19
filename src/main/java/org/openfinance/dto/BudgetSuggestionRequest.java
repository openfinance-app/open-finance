package org.openfinance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.BudgetPeriod;

/**
 * Request DTO for triggering automatic budget suggestion analysis.
 *
 * <p>The service will scan EXPENSE transactions in the specified lookback window, group them by
 * category, and compute per-period spending averages to produce budget suggestions that the user
 * can review and bulk-create.
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.9.1.5: Automatic budget creation from transaction history analysis
 * </ul>
 *
 * @see BudgetSuggestion
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-03-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetSuggestionRequest {

    /**
     * The target budget period for which to compute per-period spending averages.
     *
     * <p>Must not be null. Valid values: WEEKLY, MONTHLY, QUARTERLY, YEARLY.
     */
    @NotNull(message = "{budget.suggestion.period.required}")
    private BudgetPeriod period;

    /**
     * Number of months to look back in transaction history.
     *
     * <p>Defaults to 6 months if not specified. Must be between 1 and 24 (inclusive).
     */
    @Min(value = 1, message = "{budget.lookback.min}")
    @Max(value = 24, message = "{budget.lookback.max}")
    @Builder.Default
    private int lookbackMonths = 6;

    /**
     * Optional ISO 4217 currency code (e.g. "EUR", "USD") to use for the suggested budget amounts.
     *
     * <p>When {@code null} the service defaults to {@code "EUR"}.
     */
    private String currency;

    /**
     * Optional list of category IDs to restrict the analysis to.
     *
     * <p>When {@code null} or empty the service analyses ALL EXPENSE categories that have at least
     * one transaction in the lookback window.
     */
    private List<Long> categoryIds;
}
