package org.openfinance.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.AccountType;

/**
 * Data Transfer Object for account search criteria.
 *
 * <p>This DTO is used to build dynamic queries for searching accounts. All fields are optional - if
 * a field is null, it won't be included in the search filter. Multiple criteria can be combined
 * (AND logic).
 *
 * <p><strong>Supported Search Filters:</strong>
 *
 * <ul>
 *   <li><strong>keyword</strong> - Search in account name (case-insensitive, partial match)
 *   <li><strong>type</strong> - Filter by account type (CHECKING, SAVINGS, CREDIT_CARD, etc.)
 *   <li><strong>currency</strong> - Filter by currency code (USD, EUR, etc.)
 *   <li><strong>isActive</strong> - Filter by active status (true=active, false=closed)
 *   <li><strong>balanceMin</strong> - Filter accounts with balance greater than or equal to this
 *       value
 *   <li><strong>balanceMax</strong> - Filter accounts with balance less than or equal to this value
 *   <li><strong>institution</strong> - Filter by institution name (case-insensitive, partial match)
 * </ul>
 *
 * @see org.openfinance.entity.Account
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSearchCriteria {

    /**
     * Keyword to search in account name (case-insensitive).
     *
     * <p>Uses LIKE query with wildcards: %keyword%
     *
     * <p>Example: "chase" will match "Chase Checking", "Chase Savings", etc.
     */
    private String keyword;

    /**
     * Filter by account type.
     *
     * <p>If null, includes all account types.
     */
    private AccountType type;

    /**
     * Filter by currency code.
     *
     * <p>If null, includes accounts in all currencies.
     *
     * <p>Example: "USD", "EUR", "GBP"
     */
    private String currency;

    /**
     * Filter by active status.
     *
     * <ul>
     *   <li>true - only active accounts
     *   <li>false - only closed accounts
     *   <li>null - all accounts (both active and closed)
     * </ul>
     */
    private Boolean isActive;

    /**
     * Filter accounts with balance greater than or equal to this value.
     *
     * <p>If null, no lower balance bound.
     */
    private BigDecimal balanceMin;

    /**
     * Filter accounts with balance less than or equal to this value.
     *
     * <p>If null, no upper balance bound.
     */
    private BigDecimal balanceMax;

    /**
     * Filter by institution name (case-insensitive).
     *
     * <p>Uses LIKE query with wildcards: %institution%
     *
     * <p>Example: "chase" will match accounts from "Chase Bank"
     */
    private String institution;

    /**
     * Filter accounts with a low balance (below the notification threshold).
     *
     * <p>When true, only accounts with balance below the configured low-balance threshold (1000)
     * are returned. Mirrors the logic in NotificationService.
     */
    private Boolean lowBalance;

    /**
     * Checks if any search criteria is provided.
     *
     * @return true if at least one filter is set, false if all fields are null
     */
    public boolean hasAnyCriteria() {
        return keyword != null
                || type != null
                || currency != null
                || isActive != null
                || balanceMin != null
                || balanceMax != null
                || institution != null
                || Boolean.TRUE.equals(lowBalance);
    }
}
