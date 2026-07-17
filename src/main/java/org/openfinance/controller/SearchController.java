package org.openfinance.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.AdvancedSearchRequest;
import org.openfinance.dto.GlobalSearchResponse;
import org.openfinance.service.SearchService;
import org.openfinance.util.ControllerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for global search functionality.
 *
 * <p>This controller provides endpoints for searching across all financial entities (transactions,
 * accounts, assets, liabilities, real estate) with both simple and advanced filtering capabilities.
 *
 * <p><strong>Features:</strong>
 *
 * <ul>
 *   <li>Simple global search with keyword query
 *   <li>Advanced search with filters (amount range, date range, accounts, categories, tags, etc.)
 *   <li>Full-text search for transactions using SQLite FTS5
 *   <li>Grouped results by entity type with counts
 *   <li>Execution time tracking for performance monitoring
 * </ul>
 *
 * <p><strong>Security:</strong>
 *
 * <ul>
 *   <li>All endpoints require JWT authentication
 *   <li>X-Encryption-Session header required for decrypting sensitive data
 *   <li>Results are automatically filtered by authenticated user
 * </ul>
 *
 * <p><strong>Example Usage:</strong>
 *
 * <pre>
 * // Simple search
 * GET /api/v1/search?q=investment&limit=20
 *
 * // Advanced search with filters
 * POST /api/v1/search/advanced
 * {
 *   "query": "grocery",
 *   "entityTypes": ["TRANSACTION"],
 *   "dateFrom": "2024-01-01",
 *   "dateTo": "2024-12-31",
 *   "minAmount": 10.00,
 *   "maxAmount": 100.00,
 *   "transactionType": "EXPENSE"
 * }
 * </pre>
 *
 * <p>Task: TASK-12.4.4
 *
 * <p>Requirement: REQ-2.3.5 - Global search functionality
 *
 * @see SearchService
 * @see GlobalSearchResponse
 * @see AdvancedSearchRequest
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Validated
@Slf4j
public class SearchController {

    private final SearchService searchService;

    /**
     * Performs a simple global search with a keyword query.
     *
     * <p>This endpoint searches across all financial entities (transactions, accounts, assets,
     * liabilities, real estate) using the provided keyword. For transactions, full-text search with
     * FTS5 is used. For other entities, LIKE queries on decrypted fields are used.
     *
     * <p><strong>Request Parameters:</strong>
     *
     * <ul>
     *   <li><strong>q</strong> (required) - Search query keyword (1-200 characters)
     *   <li><strong>limit</strong> (optional) - Maximum number of results (default: 50, max: 100)
     * </ul>
     *
     * <p><strong>Headers:</strong>
     *
     * <ul>
     *   <li><strong>Authorization</strong> - Bearer JWT token
     *   <li><strong>X-Encryption-Session</strong> - Base64-encoded AES-256 encryption key
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>
     * GET /api/v1/search?q=investment&limit=20
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * X-Encryption-Session: SGVsbG9Xb3JsZEhlbGxvV29ybGRIZWxsb1dvcmxkSGVsbG9Xb3JsZA==
     * </pre>
     *
     * <p><strong>Example Response:</strong>
     *
     * <pre>
     * {
     *   "query": "investment",
     *   "totalResults": 15,
     *   "resultsByType": {
     *     "TRANSACTION": [
     *       {
     *         "resultType": "TRANSACTION",
     *         "id": 123,
     *         "title": "Investment portfolio rebalance",
     *         "subtitle": "Investment Account",
     *         "amount": 5000.00,
     *         "currency": "USD",
     *         "date": "2024-01-15",
     *         "icon": "TrendingUp",
     *         "color": "#10b981",
     *         "tags": ["investment", "portfolio"],
     *         "rank": 0.002,
     *         "snippet": "Investment portfolio rebalance - Quarterly adjustment",
     *         "createdAt": "2024-01-15T10:30:00",
     *         "updatedAt": "2024-01-15T10:30:00"
     *       }
     *     ],
     *     "ACCOUNT": [...],
     *     "ASSET": [...]
     *   },
     *   "countsPerType": {
     *     "TRANSACTION": 8,
     *     "ACCOUNT": 2,
     *     "ASSET": 5
     *   },
     *   "executionTimeMs": 45,
     *   "hasMore": false,
     *   "limit": 50
     * }
     * </pre>
     *
     * @param query Search query keyword (1-200 characters)
     * @param limit Maximum number of results to return (default: 50, max: 100)
     * @param encryptionKeyHeader Base64-encoded encryption key from request header
     * @param authentication Spring Security authentication principal
     * @return ResponseEntity containing GlobalSearchResponse with grouped results
     * @throws IllegalArgumentException if query is empty or limit is invalid
     */
    @GetMapping
    public ResponseEntity<GlobalSearchResponse> globalSearch(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", required = false, defaultValue = "50")
                    @Min(value = 1, message = "Limit must be at least 1")
                    @Max(value = 100, message = "Limit cannot exceed 100")
                    Integer limit,
            Authentication authentication) {

        // Extract user ID from authenticated principal
        Long userId = ControllerUtil.extractUserId(authentication);

        log.info(
                "Global search request from user {} with query: '{}' and limit: {}",
                userId,
                query,
                limit);

        try {
            // Decode encryption key
            // Perform search
            GlobalSearchResponse response = searchService.globalSearch(userId, query, limit);

            log.info(
                    "Global search completed for user {} in {}ms: {} results found",
                    userId,
                    response.getExecutionTimeMs(),
                    response.getTotalResults());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid search request from user {}: {}", userId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error performing global search for user {}", userId, e);
            throw new RuntimeException("Failed to perform global search", e);
        }
    }

    /**
     * Performs an advanced search with filters.
     *
     * <p>This endpoint provides advanced filtering capabilities beyond simple keyword search:
     *
     * <ul>
     *   <li>Filter by entity types (search only specific entity types)
     *   <li>Filter transactions by account, category, date range, amount range, tags,
     *       reconciliation status, and type
     *   <li>Filter assets by account and amount range
     *   <li>All results are grouped by entity type with counts and execution time
     * </ul>
     *
     * <p><strong>Headers:</strong>
     *
     * <ul>
     *   <li><strong>Authorization</strong> - Bearer JWT token
     *   <li><strong>X-Encryption-Session</strong> - Base64-encoded AES-256 encryption key
     * </ul>
     *
     * <p><strong>Example Request Body:</strong>
     *
     * <pre>
     * {
     *   "query": "grocery",
     *   "entityTypes": ["TRANSACTION"],
     *   "categoryIds": [5, 10],
     *   "dateFrom": "2024-01-01",
     *   "dateTo": "2024-12-31",
     *   "minAmount": 10.00,
     *   "maxAmount": 100.00,
     *   "tags": ["essential", "food"],
     *   "transactionType": "EXPENSE",
     *   "limit": 30
     * }
     * </pre>
     *
     * <p><strong>Example Response:</strong>
     *
     * <pre>
     * {
     *   "query": "grocery",
     *   "totalResults": 25,
     *   "resultsByType": {
     *     "TRANSACTION": [
     *       {
     *         "resultType": "TRANSACTION",
     *         "id": 456,
     *         "title": "Weekly grocery shopping",
     *         "subtitle": "Checking Account",
     *         "amount": 85.50,
     *         "currency": "USD",
     *         "date": "2024-03-15",
     *         "icon": "ShoppingCart",
     *         "color": "#ef4444",
     *         "tags": ["essential", "food", "groceries"],
     *         "rank": 0.001,
     *         "snippet": "Weekly grocery shopping - Whole Foods Market",
     *         "createdAt": "2024-03-15T18:30:00",
     *         "updatedAt": "2024-03-15T18:30:00"
     *       }
     *     ]
     *   },
     *   "countsPerType": {
     *     "TRANSACTION": 25
     *   },
     *   "executionTimeMs": 38,
     *   "hasMore": false,
     *   "limit": 30
     * }
     * </pre>
     *
     * @param request Advanced search request with query and filters
     * @param authentication Spring Security authentication principal
     * @return ResponseEntity containing GlobalSearchResponse with filtered results
     * @throws IllegalArgumentException if request validation fails
     */
    @PostMapping("/advanced")
    public ResponseEntity<GlobalSearchResponse> advancedSearch(
            @Valid @RequestBody AdvancedSearchRequest request, Authentication authentication) {

        // Extract user ID from authenticated principal
        Long userId = ControllerUtil.extractUserId(authentication);

        log.info(
                "Advanced search request from user {} with query: '{}' and {} filters",
                userId,
                request.getQuery(),
                request.hasAdvancedFilters() ? "advanced" : "no");

        try {
            // Decode encryption key
            // Perform advanced search
            GlobalSearchResponse response = searchService.advancedSearch(userId, request);

            log.info(
                    "Advanced search completed for user {} in {}ms: {} results found",
                    userId,
                    response.getExecutionTimeMs(),
                    response.getTotalResults());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid advanced search request from user {}: {}", userId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error performing advanced search for user {}", userId, e);
            throw new RuntimeException("Failed to perform advanced search", e);
        }
    }
}
