package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Unified search result DTO for global search functionality.
 *
 * <p>This DTO represents search results across different entity types (transactions, accounts,
 * assets). The {@code resultType} field indicates which type of entity this result represents.
 *
 * <p>Task: TASK-12.4.2
 *
 * <p>Requirement: REQ-2.3.5 - Global search across financial data
 *
 * @see org.openfinance.service.SearchService
 */
@Data
@Builder
public class SearchResultDto {

    /** Type of search result (TRANSACTION, ACCOUNT, ASSET, REAL_ESTATE, LIABILITY) */
    private SearchResultType resultType;

    /** Unique identifier of the entity */
    private Long id;

    /** Primary display text (transaction description, account name, asset name, etc.) */
    private String title;

    /** Secondary display text (account name for transaction, account type, asset ticker, etc.) */
    private String subtitle;

    /** Amount value (transaction amount, account balance, asset value) */
    private BigDecimal amount;

    /** Currency code (ISO 4217) */
    private String currency;

    /**
     * Date associated with the result (transaction date, account creation date, asset purchase
     * date)
     */
    private LocalDate date;

    /** Icon name for UI display (based on type/category) */
    private String icon;

    /** Color code for UI display (category color, account type color, etc.) */
    private String color;

    /** Additional metadata as key-value pairs (tags, category, type, etc.) */
    private List<String> tags;

    /** Relevance score from FTS5 ranking (lower is better, null if not applicable) */
    private Double rank;

    /** Highlighted text snippets showing matched keywords */
    private String snippet;

    /** Timestamp when the entity was created */
    private LocalDateTime createdAt;

    /** Timestamp when the entity was last updated */
    private LocalDateTime updatedAt;

    /** Enum for search result types */
    public enum SearchResultType {
        TRANSACTION,
        ACCOUNT,
        ASSET,
        REAL_ESTATE,
        LIABILITY,
        BUDGET,
        CATEGORY,
        RECURRING_TRANSACTION
    }
}
