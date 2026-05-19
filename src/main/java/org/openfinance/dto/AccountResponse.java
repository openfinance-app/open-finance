package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.AccountType;

/**
 * Data Transfer Object for account responses.
 *
 * <p>This DTO is returned to clients when retrieving account information. It contains decrypted
 * values and excludes sensitive internal fields.
 *
 * <p>Requirement REQ-2.2.1: Users can view their account information
 *
 * @see org.openfinance.entity.Account
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    /** Unique identifier of the account. */
    private Long id;

    /**
     * Name of the account (decrypted).
     *
     * <p>Requirement REQ-2.2.2: Display account name
     */
    private String name;

    /**
     * Account number for matching during transaction import.
     *
     * <p>This field stores the official account number (e.g., checking account number, IBAN, or
     * other identifier) assigned by the financial institution.
     */
    private String accountNumber;

    /**
     * Type of account (CHECKING, SAVINGS, CREDIT_CARD, INVESTMENT, CASH, OTHER).
     *
     * <p>Requirement REQ-2.2.2: Account type categorization
     */
    private AccountType type;

    /**
     * Currency code in ISO 4217 format (e.g., "USD", "EUR", "GBP").
     *
     * <p>Requirement REQ-2.8: Multi-currency support
     */
    private String currency;

    /**
     * Current balance of the account.
     *
     * <p>Requirement REQ-2.2.5: Display account balance
     */
    private BigDecimal balance;

    /** Optional description of the account (decrypted). */
    private String description;

    /**
     * Flag indicating whether the account is active.
     *
     * <p>Requirement REQ-2.2.4: Indicate soft-deleted accounts
     */
    private Boolean isActive;

    /** Timestamp when the account was created. */
    private LocalDateTime createdAt;

    /** Timestamp when the account was last updated. */
    private LocalDateTime updatedAt;

    /**
     * Associated institution details (if any). Requirement REQ-2.6.1.3: Institution display on
     * accounts
     */
    private InstitutionInfo institution;

    /** Flag indicating whether interest calculation is enabled. */
    private Boolean isInterestEnabled;

    /** The period on which interest is compounded. */
    private org.openfinance.entity.InterestPeriod interestPeriod;

    // === Currency Conversion Fields (Requirement REQ-2.1) ===

    /**
     * Account balance converted to the user's base currency.
     *
     * <p>Populated only when the account currency differs from the user's base currency and a valid
     * exchange rate is available. Falls back to {@code balance} when conversion is unavailable.
     *
     * <p>Requirement REQ-2.1: Conversion metadata for base-currency display
     */
    private BigDecimal balanceInBaseCurrency;

    /**
     * The user's base currency (ISO 4217) at the time this response was built.
     *
     * <p>Requirement REQ-2.1: Base currency reference
     */
    private String baseCurrency;

    /**
     * Exchange rate used to convert {@code balance} to {@code balanceInBaseCurrency}.
     *
     * <p>Represents the rate: 1 unit of {@code currency} = {@code exchangeRate} units of {@code
     * baseCurrency}. Null when no conversion was performed.
     *
     * <p>Requirement REQ-2.6: Exchange rate used for conversion
     */
    private BigDecimal exchangeRate;

    /**
     * Whether the balance has been converted from a foreign currency to the base currency.
     *
     * <p>{@code true} only when {@code currency != baseCurrency} AND conversion succeeded. {@code
     * false} when currencies match or conversion failed (fallback to native).
     *
     * <p>Requirement REQ-3.6: isConverted flag semantics
     */
    private Boolean isConverted;

    // === Secondary Currency Conversion Fields (Requirement REQ-3.1, REQ-3.5) ===

    /**
     * Account balance converted to the user's optional secondary currency.
     *
     * <p>Populated only when the user has a secondary currency configured AND the account currency
     * differs from the secondary currency AND a valid exchange rate is available. Null otherwise.
     *
     * <p>Requirement REQ-3.1, REQ-3.5: Secondary conversion metadata
     */
    private BigDecimal balanceInSecondaryCurrency;

    /**
     * The user's secondary currency (ISO 4217) at the time this response was built. Echoed from
     * user settings. Null when no secondary currency is configured.
     *
     * <p>Requirement REQ-3.1: Secondary currency reference
     */
    private String secondaryCurrency;

    /**
     * Exchange rate used to convert {@code balance} to {@code balanceInSecondaryCurrency}.
     *
     * <p>Represents: 1 unit of {@code currency} = {@code secondaryExchangeRate} units of {@code
     * secondaryCurrency}. Null when no secondary conversion was performed.
     *
     * <p>Requirement REQ-3.7: Secondary exchange rate
     */
    private BigDecimal secondaryExchangeRate;

    /** Nested DTO for institution information in responses. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstitutionInfo {
        private Long id;
        private String name;
        private String bic;
        private String country;
        private String logo;
    }
}
