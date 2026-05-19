package org.openfinance.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.AccountType;

/**
 * DTO representing a summary of an account for dashboard display.
 *
 * <p>This DTO provides a concise view of an account including its current balance, type, and basic
 * identification. Used in dashboard summaries to show the user's account portfolio at a glance.
 *
 * <p><b>Note:</b> Sensitive fields (name, description) are decrypted before being included in this
 * DTO.
 *
 * <p><b>Requirements:</b>
 *
 * <ul>
 *   <li>REQ-2.8.1.1: Dashboard Metrics - Display account summaries
 * </ul>
 *
 * @see DashboardSummary
 * @see AccountType
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSummary {

    /** The unique identifier of the account. */
    private Long id;

    /**
     * The account name (decrypted).
     *
     * <p>Example: "Chase Checking", "Savings - Emergency Fund"
     */
    private String name;

    /**
     * The type of account.
     *
     * <p>Valid values: CHECKING, SAVINGS, CREDIT_CARD, INVESTMENT, CASH, OTHER
     */
    private AccountType type;

    /**
     * The current balance of the account in the account's currency. Can be negative (e.g., for
     * credit cards with outstanding balance).
     *
     * <p>Example: 5000.00 (for checking account), -1200.00 (for credit card debt)
     */
    private BigDecimal balance;

    /** The currency code for this account (e.g., "EUR", "USD", "GBP"). */
    private String currency;

    /**
     * Whether this account is currently active. Inactive accounts are typically hidden from the
     * main view but retained for historical data.
     */
    private Boolean isActive;

    /**
     * Optional description of the account (decrypted).
     *
     * <p>Example: "Primary checking account for daily expenses"
     */
    private String description;
}
