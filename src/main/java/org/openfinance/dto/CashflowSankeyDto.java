package org.openfinance.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Cashflow Sankey diagram data.
 *
 * <p>Provides a structured view of money flowing from income sources through a central cash-flow
 * node out to expense categories, plus any surplus.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashflowSankeyDto {

    /** Total income for the period (sum of all incomeSources). */
    private BigDecimal totalIncome;

    /** Total expenses for the period (sum of all expenseCategories). */
    private BigDecimal totalExpenses;

    /**
     * Net surplus/deficit (totalIncome − totalExpenses). Positive = surplus, negative = deficit.
     */
    private BigDecimal surplus;

    /** Income grouped by category (or "Uncategorized" when no category is set). */
    private List<FlowNode> incomeSources;

    /** Expenses grouped by category name. */
    private List<FlowNode> expenseCategories;

    /** Analysis period in days. */
    private int period;

    /** A single source or destination node in the Sankey diagram. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowNode {
        /** Display name (category name or "Uncategorized"). */
        private String name;

        /** Amount flowing through this node. */
        private BigDecimal amount;

        /** Optional hex color for UI rendering. */
        private String color;

        /** Optional icon identifier. */
        private String icon;
    }
}
