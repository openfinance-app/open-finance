package org.openfinance.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a comprehensive dashboard summary for a user.
 *
 * <p>This DTO aggregates key financial metrics including net worth, account summaries, recent
 * transactions, and monthly change statistics. It is used by the Dashboard API to provide a
 * complete overview of the user's financial position.
 *
 * <p><b>Requirements:</b>
 *
 * <ul>
 *   <li>REQ-2.8.1.1: Dashboard Metrics - Display gross wealth, liabilities, net worth
 *   <li>REQ-2.8.1.2: Trend Visualization - Provide monthly change data
 * </ul>
 *
 * @see NetWorthSummary
 * @see AccountSummary
 * @see TransactionResponse
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummary {

    /**
     * The user's current net worth information including total assets, liabilities, and
     * month-over-month change.
     */
    private NetWorthSummary netWorth;

    /**
     * List of account summaries showing balances and account details. Sorted by balance descending
     * (highest balance first).
     */
    private List<AccountSummary> accounts;

    /**
     * List of recent transactions (typically last 10). Sorted by transaction date descending (most
     * recent first).
     */
    private List<TransactionResponse> recentTransactions;

    /**
     * The date when this dashboard summary was generated. Used to indicate data freshness to the
     * user.
     */
    private LocalDate snapshotDate;

    /** Total number of active accounts for the user. */
    private Integer totalAccounts;

    /** Total number of transactions for the user (all time). */
    private Long totalTransactions;

    /**
     * The user's base currency (e.g., "EUR", "USD"). All monetary amounts in this DTO are expressed
     * in this currency.
     */
    private String baseCurrency;
}
