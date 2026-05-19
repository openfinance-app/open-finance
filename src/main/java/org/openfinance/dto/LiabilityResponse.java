package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.LiabilityType;

/**
 * Data Transfer Object for liability responses.
 *
 * <p>This DTO is returned to clients when retrieving liability information. It contains decrypted
 * values, excludes sensitive internal fields, and includes calculated fields for financial
 * analysis.
 *
 * <p>Requirement REQ-6.1.1: Users can view their liability information
 *
 * <p>Requirement REQ-6.1.3: Display total debt, interest, and payment schedules
 *
 * @see org.openfinance.entity.Liability
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiabilityResponse {

    /** Unique identifier of the liability. */
    private Long id;

    /** ID of the user who owns this liability. */
    private Long userId;

    /**
     * Name of the liability (decrypted).
     *
     * <p>Requirement REQ-6.1.2: Display liability name
     */
    private String name;

    /**
     * Type of liability (LOAN, MORTGAGE, CREDIT_CARD, PERSONAL_LOAN, OTHER).
     *
     * <p>Requirement REQ-6.1: Liability type categorization
     */
    private LiabilityType type;

    /**
     * Original principal amount borrowed (decrypted).
     *
     * <p>Requirement REQ-6.1.2: Display original principal
     */
    private BigDecimal principal;

    /**
     * Current outstanding balance owed (decrypted).
     *
     * <p>Requirement REQ-6.1.2: Display remaining balance
     */
    private BigDecimal currentBalance;

    /**
     * Annual interest rate as a percentage (decrypted).
     *
     * <p>Example: 5.25 represents 5.25% APR
     *
     * <p>Requirement REQ-6.1.3: Display interest rate
     */
    private BigDecimal interestRate;

    /** Date when the loan or debt started. */
    private LocalDate startDate;

    /**
     * Expected date when the loan will be fully paid off.
     *
     * <p>Can be null for revolving credit or liabilities without fixed payoff dates.
     */
    private LocalDate endDate;

    /**
     * Minimum monthly payment required (decrypted).
     *
     * <p>Requirement REQ-6.1.3: Display minimum payment for budgeting
     */
    private BigDecimal minimumPayment;

    /**
     * Currency code in ISO 4217 format.
     *
     * <p>Requirement REQ-6.2: Multi-currency support
     */
    private String currency;

    /** Optional notes about the liability (decrypted). */
    private String notes;

    /** Associated institution details (if any). */
    private AccountResponse.InstitutionInfo institution;

    /** ID of the linked Real Estate property (if this is a mortgage). */
    private Long linkedPropertyId;

    /** Name of the linked Real Estate property (decrypted). */
    private String linkedPropertyName;

    /**
     * Annual insurance rate as a percentage of the principal amount (decrypted).
     *
     * <p>Example: 0.5 represents 0.5% of principal per year.
     *
     * <p>Requirement REQ-LIA-1: Insurance Percentage Field
     */
    private BigDecimal insurancePercentage;

    /**
     * Additional fees associated with this liability (decrypted).
     *
     * <p>Covers processing fees, origination fees, or late payment fees.
     *
     * <p>Requirement REQ-LIA-2: Additional Fees Field
     */
    private BigDecimal additionalFees;

    /**
     * Monthly insurance cost based on principal and insurance percentage.
     *
     * <p><strong>Calculated field</strong> - principal × (insurancePercentage / 100) / 12.
     *
     * <p>Null if insurancePercentage is not specified.
     *
     * <p>Requirement REQ-LIA-3.2: Display insurance cost
     */
    private BigDecimal monthlyInsuranceCost;

    /**
     * Total projected insurance cost over remaining loan term.
     *
     * <p><strong>Calculated field</strong> - monthlyInsuranceCost × monthsRemaining.
     *
     * <p>Null if insurancePercentage or endDate is not specified.
     *
     * <p>Requirement REQ-LIA-3.2: Display insurance cost
     */
    private BigDecimal totalInsuranceCost;

    /**
     * Total cost of the liability including remaining balance, projected interest, projected
     * insurance, and additional fees.
     *
     * <p><strong>Calculated field</strong>: currentBalance + projectedTotalInterest +
     * totalInsuranceCost + additionalFees
     *
     * <p>Provides users a clear understanding of the full financial impact.
     *
     * <p>Requirement REQ-LIA-3.3: Display total cost of liability
     */
    private BigDecimal totalCost;

    /**
     * Amount of principal already paid off (principal - currentBalance).
     *
     * <p><strong>Calculated field</strong>
     *
     * <p>Requirement REQ-LIA-3.4: Breakdown of principal paid
     */
    private BigDecimal principalPaid;

    /** Timestamp when the liability record was created. */
    private LocalDateTime createdAt;

    /** Timestamp when the liability record was last modified. */
    private LocalDateTime updatedAt;

    // === Calculated Fields ===

    /**
     * Total amount paid so far (principal - currentBalance).
     *
     * <p><strong>Calculated field</strong> - computed from principal and currentBalance.
     *
     * <p>Represents progress toward paying off the debt.
     *
     * <p>Requirement REQ-6.1.3: Display payoff progress
     */
    private BigDecimal totalPaid;

    /**
     * Percentage of the debt paid off ((totalPaid / principal) * 100).
     *
     * <p><strong>Calculated field</strong> - expressed as percentage (0-100).
     *
     * <p>Example: 35.5 represents 35.5% paid off
     */
    private BigDecimal payoffPercentage;

    /**
     * Number of months remaining until end date.
     *
     * <p><strong>Calculated field</strong> - computed from current date to end date.
     *
     * <p>Null if end date is not specified.
     *
     * <p>Requirement REQ-6.1.3: Display time remaining
     */
    private Integer monthsRemaining;

    /**
     * Number of days since the liability started.
     *
     * <p><strong>Calculated field</strong> - computed from start date to current date.
     *
     * <p>Useful for tracking how long the debt has been active.
     */
    private Long liabilityAgeDays;

    /**
     * Projected total interest to be paid over the life of the loan.
     *
     * <p><strong>Calculated field</strong> - computed using amortization formulas.
     *
     * <p>Requires: interestRate, currentBalance, minimumPayment, and endDate.
     *
     * <p>Null if insufficient data for calculation.
     *
     * <p>Requirement REQ-6.1.3: Display projected total interest
     */
    private BigDecimal projectedTotalInterest;

    /**
     * Monthly interest cost based on current balance.
     *
     * <p><strong>Calculated field</strong> - (currentBalance * (interestRate / 100) / 12).
     *
     * <p>Represents approximate monthly interest charge at current balance.
     *
     * <p>Null if interest rate is not specified.
     */
    private BigDecimal monthlyInterestCost;

    // === Currency Conversion Fields (Requirement REQ-2.3) ===

    /**
     * Current liability balance ({@code currentBalance}) converted to the user's base currency.
     *
     * <p>Populated only when the liability currency differs from the user's base currency and a
     * valid exchange rate is available. Falls back to {@code currentBalance} otherwise.
     *
     * <p>Requirement REQ-2.3: Conversion metadata for base-currency display
     */
    private BigDecimal balanceInBaseCurrency;

    /**
     * The user's base currency (ISO 4217) at the time this response was built.
     *
     * <p>Requirement REQ-2.3: Base currency reference
     */
    private String baseCurrency;

    /**
     * Exchange rate used to convert {@code currentBalance} to {@code balanceInBaseCurrency}.
     *
     * <p>Represents: 1 unit of {@code currency} = {@code exchangeRate} units of {@code
     * baseCurrency}. Null when no conversion was performed.
     *
     * <p>Requirement REQ-2.6: Exchange rate used for conversion
     */
    private BigDecimal exchangeRate;

    /**
     * Whether the balance has been converted from a foreign currency to the base currency.
     *
     * <p>{@code true} only when {@code currency != baseCurrency} AND conversion succeeded.
     *
     * <p>Requirement REQ-3.6: isConverted flag semantics
     */
    private Boolean isConverted;

    // === Secondary Currency Conversion Fields (Requirement REQ-3.3, REQ-3.5) ===

    /**
     * Current liability balance converted to the user's optional secondary currency.
     *
     * <p>Populated only when the user has a secondary currency configured AND the liability
     * currency differs from the secondary currency AND a valid exchange rate is available. Null
     * otherwise.
     *
     * <p>Requirement REQ-3.3, REQ-3.5: Secondary conversion metadata
     */
    private BigDecimal balanceInSecondaryCurrency;

    /**
     * The user's secondary currency (ISO 4217) at the time this response was built. Echoed from
     * user settings. Null when no secondary currency is configured.
     *
     * <p>Requirement REQ-3.3: Secondary currency reference
     */
    private String secondaryCurrency;

    /**
     * Exchange rate used to convert {@code currentBalance} to {@code balanceInSecondaryCurrency}.
     *
     * <p>Represents: 1 unit of {@code currency} = {@code secondaryExchangeRate} units of {@code
     * secondaryCurrency}. Null when no secondary conversion was performed.
     *
     * <p>Requirement REQ-3.7: Secondary exchange rate
     */
    private BigDecimal secondaryExchangeRate;
}
