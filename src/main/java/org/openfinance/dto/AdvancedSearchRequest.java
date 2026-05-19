package org.openfinance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.TransactionType;

/**
 * Data Transfer Object for advanced search requests.
 *
 * <p>This DTO provides advanced filtering capabilities for global search across multiple entity
 * types (transactions, accounts, assets, liabilities, real estate, budgets, categories). It extends
 * the basic search functionality with additional filters and constraints.
 *
 * <p><strong>Supported Features:</strong>
 *
 * <ul>
 *   <li><strong>query</strong> - Full-text search keyword (required)
 *   <li><strong>entityTypes</strong> - Filter which entity types to search (optional, default: all)
 *   <li><strong>accountIds</strong> - Filter transactions/assets by specific accounts
 *   <li><strong>categoryIds</strong> - Filter transactions by specific categories
 *   <li><strong>minAmount, maxAmount</strong> - Filter by amount range
 *   <li><strong>dateFrom, dateTo</strong> - Filter by date range
 *   <li><strong>tags</strong> - Filter transactions by tags
 *   <li><strong>isReconciled</strong> - Filter transactions by reconciliation status
 *   <li><strong>transactionType</strong> - Filter by INCOME, EXPENSE, or TRANSFER
 *   <li><strong>limit</strong> - Maximum number of results (default: 50, max: 100)
 * </ul>
 *
 * <p><strong>Example Usage:</strong>
 *
 * <pre>
 * // Search for transactions with "grocery" keyword in the last month
 * AdvancedSearchRequest request = AdvancedSearchRequest.builder()
 *     .query("grocery")
 *     .entityTypes(List.of(SearchResultType.TRANSACTION))
 *     .dateFrom(LocalDate.now().minusMonths(1))
 *     .dateTo(LocalDate.now())
 *     .transactionType(TransactionType.EXPENSE)
 *     .limit(20)
 *     .build();
 *
 * // Search for assets and accounts with keyword "investment"
 * AdvancedSearchRequest request = AdvancedSearchRequest.builder()
 *     .query("investment")
 *     .entityTypes(List.of(SearchResultType.ASSET, SearchResultType.ACCOUNT))
 *     .limit(50)
 *     .build();
 * </pre>
 *
 * <p>Requirement REQ-3.3.1: Advanced search and filtering
 *
 * @see SearchResultDto
 * @see GlobalSearchResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvancedSearchRequest {

    /**
     * Search query keyword (required).
     *
     * <p>This is used for full-text search across various fields depending on entity type:
     *
     * <ul>
     *   <li><strong>Transactions:</strong> description, notes, tags, payee (FTS5 full-text search)
     *   <li><strong>Accounts:</strong> name, description (LIKE query on decrypted fields)
     *   <li><strong>Assets:</strong> name, symbol (LIKE query on decrypted fields)
     *   <li><strong>Real Estate:</strong> name, address (LIKE query on decrypted fields)
     *   <li><strong>Liabilities:</strong> name (LIKE query on decrypted fields)
     * </ul>
     *
     * <p>Example: "investment" will search across all entity types for the keyword "investment"
     */
    @Size(min = 1, max = 200, message = "{search.query.between}")
    private String query;

    /**
     * List of entity types to search (optional).
     *
     * <p>If null or empty, searches across all entity types. Use this to narrow down search to
     * specific entity types only.
     *
     * <p>Available types:
     *
     * <ul>
     *   <li>TRANSACTION - Search transactions
     *   <li>ACCOUNT - Search accounts
     *   <li>ASSET - Search assets
     *   <li>REAL_ESTATE - Search real estate properties
     *   <li>LIABILITY - Search liabilities
     *   <li>BUDGET - Search budgets (future enhancement)
     *   <li>CATEGORY - Search categories (future enhancement)
     * </ul>
     *
     * <p>Example: List.of(SearchResultDto.SearchResultType.TRANSACTION,
     * SearchResultDto.SearchResultType.ASSET)
     */
    private List<SearchResultDto.SearchResultType> entityTypes;

    /**
     * Filter by specific account IDs.
     *
     * <p>Applies to:
     *
     * <ul>
     *   <li>Transactions: Filters by accountId or toAccountId (transfers)
     *   <li>Assets: Filters by accountId (if assets are linked to accounts)
     * </ul>
     *
     * <p>If null or empty, includes all accounts.
     *
     * <p>Example: List.of(1L, 2L, 5L)
     */
    private List<Long> accountIds;

    /**
     * Filter by specific category IDs.
     *
     * <p>Applies to transactions only. Filters transactions by categoryId.
     *
     * <p>If null or empty, includes all categories (including uncategorized).
     *
     * <p>Example: List.of(10L, 15L, 20L)
     */
    private List<Long> categoryIds;

    /**
     * Minimum amount filter (inclusive).
     *
     * <p>Applies to transactions, assets (currentPrice), and liabilities (principalBalance).
     *
     * <p>If null, no lower amount bound.
     *
     * <p>Example: BigDecimal.valueOf(100.00) - shows results >= $100.00
     */
    @Min(value = 0, message = "{search.amount.min}")
    private BigDecimal minAmount;

    /**
     * Maximum amount filter (inclusive).
     *
     * <p>Applies to transactions, assets (currentPrice), and liabilities (principalBalance).
     *
     * <p>If null, no upper amount bound.
     *
     * <p>Example: BigDecimal.valueOf(1000.00) - shows results <= $1000.00
     */
    @Min(value = 0, message = "{search.amount.max.min}")
    private BigDecimal maxAmount;

    /**
     * Filter by date from (inclusive).
     *
     * <p>Applies to transactions (transactionDate) and assets (purchaseDate).
     *
     * <p>If null, no lower date bound.
     *
     * <p>Example: LocalDate.of(2024, 1, 1) - shows results on or after Jan 1, 2024
     */
    @PastOrPresent(message = "{search.date.from.future}")
    private LocalDate dateFrom;

    /**
     * Filter by date to (inclusive).
     *
     * <p>Applies to transactions (transactionDate) and assets (purchaseDate).
     *
     * <p>If null, no upper date bound.
     *
     * <p>Example: LocalDate.of(2024, 12, 31) - shows results on or before Dec 31, 2024
     */
    @PastOrPresent(message = "{search.date.to.future}")
    private LocalDate dateTo;

    /**
     * Filter transactions by tags.
     *
     * <p>Applies to transactions only. Searches for transactions containing any of the specified
     * tags.
     *
     * <p>If null or empty, includes transactions with any tags (or no tags).
     *
     * <p>Example: List.of("business", "tax-deductible") - shows transactions tagged with "business"
     * OR "tax-deductible"
     */
    private List<String> tags;

    /**
     * Filter transactions by reconciliation status.
     *
     * <p>Applies to transactions only.
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
     * Filter transactions by type (INCOME, EXPENSE, TRANSFER).
     *
     * <p>Applies to transactions only.
     *
     * <p>If null, includes all transaction types.
     *
     * <p>Example: TransactionType.EXPENSE - shows only expense transactions
     */
    private TransactionType transactionType;

    /**
     * Maximum number of results to return.
     *
     * <p>Default: 50, Maximum: 100
     *
     * <p>This limit applies to the total number of results across all entity types.
     *
     * <p>Example: 20 - returns up to 20 results total
     */
    @Min(value = 1, message = "{search.limit.min}")
    @Max(value = 100, message = "{search.limit.max}")
    @Builder.Default
    private Integer limit = 50;

    /**
     * Checks if any advanced filters are applied (beyond basic query).
     *
     * @return true if at least one filter (other than query and limit) is set, false otherwise
     */
    public boolean hasAdvancedFilters() {
        return (entityTypes != null && !entityTypes.isEmpty())
                || (accountIds != null && !accountIds.isEmpty())
                || (categoryIds != null && !categoryIds.isEmpty())
                || minAmount != null
                || maxAmount != null
                || dateFrom != null
                || dateTo != null
                || (tags != null && !tags.isEmpty())
                || isReconciled != null
                || transactionType != null;
    }

    /**
     * Checks if transaction filters are applied.
     *
     * @return true if any transaction-specific filters are set
     */
    public boolean hasTransactionFilters() {
        return (accountIds != null && !accountIds.isEmpty())
                || (categoryIds != null && !categoryIds.isEmpty())
                || minAmount != null
                || maxAmount != null
                || dateFrom != null
                || dateTo != null
                || (tags != null && !tags.isEmpty())
                || isReconciled != null
                || transactionType != null;
    }
}
