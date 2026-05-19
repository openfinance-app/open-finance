package org.openfinance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.LiabilityType;
import org.openfinance.validation.ValidCurrency;

/**
 * Data Transfer Object for creating or updating a liability.
 *
 * <p>This DTO is used for both POST (create) and PUT (update) operations. Validation annotations
 * ensure data integrity before processing.
 *
 * <p>Requirement REQ-6.1: Liability creation and management
 *
 * <p>Requirement REQ-6.1.2: Liability tracking with name, type, balances, and interest rates
 *
 * @see org.openfinance.entity.Liability
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiabilityRequest {

    /**
     * Name of the liability (e.g., "Home Mortgage", "Student Loan", "Visa Credit Card").
     *
     * <p>This field will be encrypted before storing in the database.
     *
     * <p>Requirement REQ-6.1.2: Liability must have a descriptive name
     */
    @NotBlank(message = "{liability.name.required}")
    @Size(min = 1, max = 255, message = "{liability.name.between}")
    private String name;

    /**
     * Type of liability (LOAN, MORTGAGE, CREDIT_CARD, PERSONAL_LOAN, OTHER).
     *
     * <p>Requirement REQ-6.1: Liability type categorization
     */
    @NotNull(message = "{liability.type.required}")
    private LiabilityType type;

    /**
     * Original principal amount borrowed.
     *
     * <p>This field will be encrypted before storing in the database.
     *
     * <p>Examples:
     *
     * <ul>
     *   <li>$300,000 for a mortgage
     *   <li>$50,000 for a student loan
     *   <li>$5,000 for a personal loan
     * </ul>
     *
     * <p>Requirement REQ-6.1.2: Track original principal amount
     */
    @NotNull(message = "{liability.principal.required}")
    @DecimalMin(value = "0.01", message = "{liability.principal.min}")
    @Digits(integer = 15, fraction = 2, message = "{liability.principal.digits}")
    private BigDecimal principal;

    /**
     * Current outstanding balance owed.
     *
     * <p>This field will be encrypted before storing in the database.
     *
     * <p>Current balance is typically less than or equal to principal (unless interest has accrued
     * and been capitalized).
     *
     * <p>Requirement REQ-6.1.2: Track remaining balance
     */
    @NotNull(message = "{liability.balance.required}")
    @DecimalMin(value = "0.00", message = "{liability.balance.min}")
    @Digits(integer = 15, fraction = 2, message = "{liability.balance.digits}")
    private BigDecimal currentBalance;

    /**
     * Annual interest rate as a percentage (e.g., 5.25 for 5.25% APR).
     *
     * <p>This field will be encrypted before storing in the database.
     *
     * <p>Optional field. Can be null for interest-free loans or promotional periods.
     *
     * <p>Requirement REQ-6.1.3: Track interest rate for amortization calculations
     */
    @DecimalMin(value = "0.00", message = "{liability.interest.min}")
    @DecimalMax(value = "100.00", message = "{liability.interest.max}")
    @Digits(integer = 3, fraction = 4, message = "{liability.interest.digits}")
    private BigDecimal interestRate;

    /**
     * Date when the loan or debt started.
     *
     * <p>Used for calculating loan age and payment schedules.
     *
     * <p>Requirement REQ-6.1.2: Track start date
     */
    @NotNull(message = "{liability.start.required}")
    @PastOrPresent(message = "{liability.start.future}")
    private LocalDate startDate;

    /**
     * Expected date when the loan will be fully paid off.
     *
     * <p>Optional field. Can be null for revolving credit (e.g., credit cards) or liabilities
     * without a fixed payoff schedule.
     *
     * <p>Requirement REQ-6.1.2: Track expected payoff date
     */
    @Future(message = "{liability.end.past}")
    private LocalDate endDate;

    /**
     * Minimum monthly payment required.
     *
     * <p>This field will be encrypted before storing in the database.
     *
     * <p>Optional field. Can be null if there's no regular payment schedule.
     *
     * <p>Requirement REQ-6.1.3: Track minimum payment for budgeting
     */
    @DecimalMin(value = "0.00", message = "{liability.min.payment.min}")
    @Digits(integer = 15, fraction = 2, message = "{liability.min.payment.digits}")
    private BigDecimal minimumPayment;

    /**
     * Currency code in ISO 4217 format (e.g., "USD", "EUR", "GBP").
     *
     * <p>Requirement REQ-6.2: Multi-currency support for liabilities
     */
    @NotBlank(message = "{account.currency.required}")
    @ValidCurrency
    private String currency;

    /**
     * Optional notes about the liability (e.g., lender info, payment schedule, refinancing
     * history).
     *
     * <p>This field will be encrypted before storing in the database.
     */
    @Size(max = 1000, message = "{liability.notes.max}")
    private String notes;

    /** Optional financial institution ID holding the liability. */
    private Long institutionId;

    /**
     * Annual insurance rate as a percentage of the principal amount.
     *
     * <p>Example: 0.5 means 0.5% of principal per year for insurance cost.
     *
     * <p>Monthly insurance cost = principal × (insurancePercentage / 100) / 12
     *
     * <p>This field will be encrypted before storing in the database.
     *
     * <p>Requirement REQ-LIA-1: Insurance Percentage Field
     */
    @DecimalMin(value = "0.00", message = "{liability.insurance.min}")
    @DecimalMax(value = "100.00", message = "{liability.insurance.max}")
    @Digits(integer = 3, fraction = 4, message = "{liability.insurance.digits}")
    private BigDecimal insurancePercentage;

    /**
     * Additional fees associated with this liability.
     *
     * <p>Covers one-time or periodic fees such as processing fees, origination fees, or late
     * payment fees already incurred.
     *
     * <p>This field will be encrypted before storing in the database.
     *
     * <p>Requirement REQ-LIA-2: Additional Fees Field
     */
    @DecimalMin(value = "0.00", message = "{liability.fees.min}")
    @Digits(integer = 15, fraction = 2, message = "{liability.fees.digits}")
    private BigDecimal additionalFees;

    /**
     * Optional ID of the real estate property to link this mortgage to. When provided and the
     * liability type is MORTGAGE, the property's mortgageId will be set to this liability's ID
     * after creation.
     */
    private Long realEstateId;
}
