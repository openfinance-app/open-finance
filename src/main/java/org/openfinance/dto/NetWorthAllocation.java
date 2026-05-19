package org.openfinance.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a single category in the net worth allocation breakdown.
 *
 * <p>Shows the distribution of the user's net worth across different asset classes (e.g., cash,
 * investments, real estate) and liabilities (e.g., loans, credit cards).
 *
 * <p>Task: Dashboard Net Worth Allocation Card
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetWorthAllocation {

    /** Category name (e.g., "Cash & Savings", "Investments", "Real Estate", "Mortgages") */
    private String category;

    /** Total value in this category Positive for assets, negative for liabilities */
    private BigDecimal value;

    /** Percentage of total net worth this category represents Can be positive or negative */
    private BigDecimal percentage;

    /** Number of items (accounts, assets, or liabilities) in this category */
    private Integer itemCount;

    /** Flag indicating if this is a liability category (true) or asset category (false) */
    private Boolean isLiability;

    /** User's base currency for the value */
    private String currency;

    /** Color code for treemap visualization (optional, can be computed on frontend) */
    private String color;
}
