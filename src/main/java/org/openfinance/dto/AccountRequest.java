package org.openfinance.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.AccountType;
import org.openfinance.validation.ValidCurrency;

/**
 * Data Transfer Object for creating or updating an account.
 *
 * <p>This DTO is used for both POST (create) and PUT (update) operations. Validation annotations
 * ensure data integrity before processing.
 *
 * <p>Requirement REQ-2.2.1: Account creation with name, type, currency, and initial balance
 *
 * <p>Requirement REQ-2.2.3: Account updates
 *
 * @see org.openfinance.entity.Account
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountRequest {

    /**
     * Name of the account (e.g., "Chase Checking", "401k Retirement").
     *
     * <p>This field will be encrypted before storing in the database.
     *
     * <p>Requirement REQ-2.2.2: Account must have a descriptive name
     */
    @NotBlank(message = "{account.name.required}")
    @Size(min = 1, max = 255, message = "{account.name.between}")
    private String name;

    /**
     * Type of account (CHECKING, SAVINGS, CREDIT_CARD, INVESTMENT, CASH, OTHER).
     *
     * <p>Requirement REQ-2.2.2: Account type categorization
     */
    @NotNull(message = "{account.type.required}")
    private AccountType type;

    /**
     * Currency code in ISO 4217 format (e.g., "USD", "EUR", "GBP").
     *
     * <p>Requirement REQ-2.8: Multi-currency support
     */
    @NotBlank(message = "{account.currency.required}")
    @ValidCurrency
    private String currency;

    /**
     * Initial balance when creating an account, or current balance when updating.
     *
     * <p>May be negative for liability accounts (e.g., credit cards with balance owed).
     *
     * <p>Requirement REQ-2.2.5: Account balance management
     */
    @NotNull(message = "{account.initial.balance.required}")
    @Digits(integer = 15, fraction = 4, message = "{account.balance.digits}")
    private BigDecimal initialBalance;

    /**
     * Optional description of the account (e.g., institution details, account purpose).
     *
     * <p>This field will be encrypted before storing in the database.
     */
    @Size(max = 500, message = "{account.description.max}")
    private String description;

    /**
     * Date when the account was opened. Required for balance history calculations. Defaults to
     * current date if not provided.
     */
    private java.time.LocalDate openingDate;

    /**
     * Account number for matching during transaction import.
     *
     * <p>This field stores the official account number (e.g., checking account number, IBAN, or
     * other identifier) assigned by the financial institution. It is used to automatically match
     * imported transactions to the correct account.
     *
     * <p>Requirement: Account Number field for transaction import matching
     */
    @Size(max = 50, message = "{account.number.max}")
    private String accountNumber;

    /**
     * Optional ID of the financial institution associated with this account.
     *
     * <p>Requirement REQ-2.6.1.3: Institution association
     */
    private Long institutionId;

    /** Flag indicating whether interest calculation is enabled. */
    private Boolean isInterestEnabled;

    /** The period on which interest is compounded. */
    private org.openfinance.entity.InterestPeriod interestPeriod;

    /**
     * Initial interest rate when creating an account with interest enabled. Ignored during update,
     * to update rate use the InterestRateVariation endpoints.
     */
    private BigDecimal interestRate;

    /** Initial tax rate when creating an account with interest enabled. */
    private BigDecimal taxRate;
}
