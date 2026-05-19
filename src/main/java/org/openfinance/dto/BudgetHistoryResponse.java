package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.BudgetPeriod;

/**
 * Response DTO for the budget history endpoint.
 *
 * <p>Contains the full budget metadata plus a breakdown of spending per sub-period over the
 * budget's lifetime (e.g., each month for a yearly budget).
 *
 * <p>Requirement REQ-2.9.1.4: Budget history and per-period breakdown
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-03-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetHistoryResponse {

    /** ID of the budget. */
    private Long budgetId;

    /** Name of the category the budget applies to. */
    private String categoryName;

    /** Budgeted amount per sub-period. */
    private BigDecimal amount;

    /** Currency code (ISO 4217). */
    private String currency;

    /** Budget period type (determines how sub-periods are split). */
    private BudgetPeriod period;

    /** Start of the overall budget range. */
    private LocalDate startDate;

    /** End of the overall budget range. */
    private LocalDate endDate;

    /**
     * Ordered list of sub-period entries from startDate to endDate.
     *
     * <p>For a MONTHLY budget, each entry covers a single month. For a WEEKLY budget, each entry
     * covers 7 days. Etc.
     */
    private List<BudgetHistoryEntry> history;

    /** Total spent across the entire budget lifetime. */
    private BigDecimal totalSpent;

    /** Total budgeted across the entire budget lifetime. */
    private BigDecimal totalBudgeted;
}
