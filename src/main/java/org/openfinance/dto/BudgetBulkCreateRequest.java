package org.openfinance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for bulk-creating multiple budgets in a single operation.
 *
 * <p>The list of {@link BudgetRequest} items is typically constructed from the suggestions that the
 * user selected in the {@code BudgetWizard} (Step 2 → Step 3 flow). Each item is validated
 * independently; items that would create a duplicate budget (same category + period) are silently
 * skipped and counted in {@link BudgetBulkCreateResponse#getSkippedCount()}.
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.9.1.5: Automatic budget creation from transaction history analysis
 * </ul>
 *
 * @see BudgetBulkCreateResponse
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-03-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetBulkCreateRequest {

    /**
     * The list of budget creation requests to process.
     *
     * <p>Must not be {@code null} and must contain at least one element.
     */
    @NotNull(message = "{budget.list.required}")
    @NotEmpty(message = "{budget.list.empty}")
    private List<@Valid BudgetRequest> budgets;
}
