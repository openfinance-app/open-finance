package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.TransactionType;

/**
 * Data Transfer Object for transaction search criteria.
 *
 * <p>This DTO is used to build dynamic queries for searching transactions. All fields are optional
 * - if a field is null, it won't be included in the search filter. Multiple criteria can be
 * combined (AND logic).
 *
 * <p><strong>Supported Search Filters:</strong>
 *
 * <ul>
 *   <li><strong>keyword</strong> - Search in description, notes, payee (case-insensitive, partial
 *       match)
 *   <li><strong>accountId</strong> - Filter by specific account
 *   <li><strong>categoryId</strong> - Filter by specific category
 *   <li><strong>type</strong> - Filter by transaction type (INCOME, EXPENSE, TRANSFER)
 *   <li><strong>dateFrom, dateTo</strong> - Filter by date range (inclusive)
 *   <li><strong>amountMin, amountMax</strong> - Filter by amount range
 *   <li><strong>tags</strong> - Search transactions containing specific tags
 *   <li><strong>isReconciled</strong> - Filter by reconciliation status
 * </ul>
 *
 * <p>Requirement REQ-2.3.5: Transaction search and filtering
 *
 * @see org.openfinance.entity.Transaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSearchCriteria {

    /**
     * Keyword to search in description, notes, and payee fields (case-insensitive).
     *
     * <p>Uses LIKE query with wildcards: %keyword%
     *
     * <p>Example: "grocery" will match "Grocery store", "Buy groceries", etc.
     */
    private String keyword;

    /**
     * Filter by specific account ID.
     *
     * <p>If null, includes transactions from all user accounts.
     */
    private Long accountId;

    /**
     * Filter by specific category ID.
     *
     * <p>If null, includes transactions from all categories (including uncategorized).
     */
    private Long categoryId;

    /**
     * Filter by transaction type (INCOME, EXPENSE, TRANSFER).
     *
     * <p>If null, includes all transaction types.
     */
    private TransactionType type;

    /**
     * Filter transactions on or after this date (inclusive).
     *
     * <p>If null, no lower date bound.
     */
    private LocalDate dateFrom;

    /**
     * Filter transactions on or before this date (inclusive).
     *
     * <p>If null, no upper date bound.
     */
    private LocalDate dateTo;

    /**
     * Filter transactions with amount greater than or equal to this value.
     *
     * <p>If null, no lower amount bound.
     */
    private BigDecimal amountMin;

    /**
     * Filter transactions with amount less than or equal to this value.
     *
     * <p>If null, no upper amount bound.
     */
    private BigDecimal amountMax;

    /**
     * Search for transactions containing specific tags (comma-separated).
     *
     * <p>Uses LIKE query: tags field contains this value.
     *
     * <p>Example: "business" will match transactions with tags "business,travel" or "business"
     */
    private String tags;

    /**
     * Filter by payee name (case-insensitive, exact or partial match depending on specification).
     *
     * <p>If null, includes all payees.
     */
    private String payee;

    /**
     * Filter by reconciliation status.
     *
     * <p>If null, includes both reconciled and unreconciled transactions.
     *
     * <ul>
     *   <li>true - only reconciled transactions
     *   <li>false - only unreconciled transactions
     * </ul>
     */
    private Boolean isReconciled;

    /**
     * Filter for transactions with no category assigned.
     *
     * <p>When true, only transactions where categoryId IS NULL are returned. Mirrors the
     * notification logic for uncategorized transactions.
     */
    private Boolean noCategory;

    /**
     * Filter for transactions with no payee assigned.
     *
     * <p>When true, only transactions where payee IS NULL or empty are returned. Mirrors the
     * notification logic for transactions without payees.
     */
    private Boolean noPayee;

    /**
     * Checks if any search criteria is provided.
     *
     * @return true if at least one filter is set, false if all fields are null
     */
    public boolean hasAnyCriteria() {
        return keyword != null
                || accountId != null
                || categoryId != null
                || type != null
                || dateFrom != null
                || dateTo != null
                || amountMin != null
                || amountMax != null
                || tags != null
                || payee != null
                || isReconciled != null
                || Boolean.TRUE.equals(noCategory)
                || Boolean.TRUE.equals(noPayee);
    }
}
