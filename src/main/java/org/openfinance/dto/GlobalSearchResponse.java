package org.openfinance.dto;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for global search operations.
 *
 * <p>Contains search results grouped by type with metadata about the search operation.
 *
 * <p>Task: TASK-12.4.2
 *
 * <p>Requirement: REQ-2.3.5 - Global search response with grouped results
 *
 * @see org.openfinance.service.SearchService
 */
@Data
@Builder
public class GlobalSearchResponse {

    /** Search query that was executed */
    private String query;

    /** Total number of results across all types */
    private Integer totalResults;

    /** Results grouped by type (TRANSACTION, ACCOUNT, ASSET, etc.) */
    private Map<String, List<SearchResultDto>> resultsByType;

    /** Number of results per type */
    private Map<String, Integer> countsPerType;

    /** Time taken to execute search (in milliseconds) */
    private Long executionTimeMs;

    /** Flag indicating if results were truncated due to limit */
    private Boolean hasMore;

    /** Maximum number of results returned */
    private Integer limit;
}
