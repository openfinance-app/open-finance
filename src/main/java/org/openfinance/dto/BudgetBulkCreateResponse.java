package org.openfinance.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO returned by the bulk budget-creation endpoint.
 *
 * <p>Summarises the outcome of a {@link BudgetBulkCreateRequest}: how many budgets were
 * successfully created, how many were silently skipped (duplicate category+period), and any
 * unexpected errors that occurred during processing.
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.9.1.5: Automatic budget creation from transaction history analysis
 * </ul>
 *
 * @see BudgetBulkCreateRequest
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-03-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetBulkCreateResponse {

    /**
     * The list of {@link BudgetResponse} objects for every budget that was successfully persisted.
     */
    private List<BudgetResponse> created;

    /** Number of budget requests that were successfully created. */
    private int successCount;

    /**
     * Number of budget requests that were silently skipped because a budget for the same
     * category+period combination already existed.
     */
    private int skippedCount;

    /**
     * Human-readable error messages for any requests that failed for reasons other than a
     * duplicate. An empty list indicates full success (aside from skips).
     */
    private List<String> errors;
}
