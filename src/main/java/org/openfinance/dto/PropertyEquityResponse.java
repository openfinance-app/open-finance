package org.openfinance.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for property equity calculation response.
 *
 * <p>This DTO provides detailed equity breakdown for a real estate property, including property
 * value, mortgage balance, and equity calculations.
 *
 * <p>Requirement REQ-2.16.2: Calculate and display property equity
 *
 * @see org.openfinance.entity.RealEstateProperty
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyEquityResponse {

    /** Property ID. */
    private Long propertyId;

    /** Property name (decrypted). */
    private String propertyName;

    /**
     * Current market value of the property (decrypted).
     *
     * <p>Requirement REQ-2.16.1: Current property value
     */
    private BigDecimal currentValue;

    /**
     * Outstanding mortgage balance (decrypted from linked liability).
     *
     * <p>Zero if no mortgage is linked to this property.
     *
     * <p>Requirement REQ-2.16.2: Mortgage balance for equity calculation
     */
    private BigDecimal mortgageBalance;

    /**
     * Calculated equity (currentValue - mortgageBalance).
     *
     * <p><strong>Calculated field</strong> - represents the portion of the property owned outright.
     *
     * <p>If no mortgage, equity equals current value.
     *
     * <p>Requirement REQ-2.16.2: Property equity calculation
     */
    private BigDecimal equity;

    /**
     * Equity percentage ((equity / currentValue) * 100).
     *
     * <p><strong>Calculated field</strong> - percentage of property value owned.
     *
     * <p>Example: 40.0 represents 40% equity (60% financed by mortgage)
     *
     * <p>Requirement REQ-2.16.2: Equity percentage for visualization
     */
    private BigDecimal equityPercentage;

    /**
     * Loan-to-value ratio ((mortgageBalance / currentValue) * 100).
     *
     * <p><strong>Calculated field</strong> - inverse of equity percentage.
     *
     * <p>Example: 60.0 represents 60% LTV (40% equity)
     *
     * <p>Used by lenders to assess refinancing eligibility.
     */
    private BigDecimal loanToValueRatio;

    /** Currency code in ISO 4217 format. */
    private String currency;

    /** ID of the linked mortgage (if any). */
    private Long mortgageId;

    /** Whether this property has a linked mortgage. */
    private boolean hasMortgage;
}
