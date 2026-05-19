package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.AdvancedSearchRequest;
import org.openfinance.dto.GlobalSearchResponse;
import org.openfinance.dto.SearchResultDto;
import org.openfinance.dto.SearchResultDto.SearchResultType;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Budget;
import org.openfinance.entity.Category;
import org.openfinance.entity.Liability;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.RecurringTransaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.RecurringTransactionRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.security.EncryptionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for global search functionality across all financial entities.
 *
 * <p>This service provides unified search across:
 *
 * <ul>
 *   <li>Transactions - using FTS5 full-text search on descriptions and notes
 *   <li>Accounts - searching by name
 *   <li>Assets - searching by name and ticker symbol
 *   <li>Real Estate - searching by name and address
 *   <li>Liabilities - searching by name
 * </ul>
 *
 * <p><strong>Full-Text Search:</strong> Uses SQLite FTS5 extension for efficient keyword search.
 * Supports boolean operators (AND, OR, NOT), phrase queries, and prefix matching.
 *
 * <p><strong>Security:</strong> All searches are scoped to the authenticated user. FTS table stores
 * decrypted text for search functionality, but database file encryption (SQLCipher) protects data
 * at rest.
 *
 * <p>Task: TASK-12.4.2
 *
 * <p>Requirement: REQ-2.3.5 - Global search functionality
 *
 * @see org.openfinance.dto.GlobalSearchResponse
 * @see org.openfinance.dto.SearchResultDto
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AssetRepository assetRepository;
    private final RealEstateRepository realEstateRepository;
    private final LiabilityRepository liabilityRepository;
    private final BudgetRepository budgetRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final CategoryRepository categoryRepository;
    private final EncryptionService encryptionService;
    private final JdbcTemplate jdbcTemplate;

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    /**
     * Performs a global search across all financial entities.
     *
     * <p>Searches transactions using FTS5 full-text search and other entities using LIKE queries.
     * Results are ranked by relevance and grouped by entity type.
     *
     * @param userId User ID performing the search
     * @param query Search query string (supports FTS5 syntax for transactions)
     * @param encryptionKey User's encryption key for decrypting sensitive fields
     * @param limit Maximum number of results to return (default 50, max 100)
     * @return GlobalSearchResponse containing grouped search results with metadata
     * @throws IllegalArgumentException if query is empty or limit is invalid
     */
    public GlobalSearchResponse globalSearch(
            Long userId, String query, SecretKey encryptionKey, Integer limit) {
        long startTime = System.currentTimeMillis();

        // Validate input
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Search query cannot be empty");
        }

        // Apply limits
        int effectiveLimit =
                (limit != null && limit > 0 && limit <= MAX_LIMIT) ? limit : DEFAULT_LIMIT;

        log.debug("Performing global search for user {} with query: {}", userId, query);

        // Search across all entity types in parallel
        CompletableFuture<List<SearchResultDto>> transactionsFuture =
                CompletableFuture.supplyAsync(
                        () -> searchTransactions(userId, query, encryptionKey, effectiveLimit));
        CompletableFuture<List<SearchResultDto>> accountsFuture =
                CompletableFuture.supplyAsync(
                        () -> searchAccounts(userId, query, encryptionKey, effectiveLimit));
        CompletableFuture<List<SearchResultDto>> assetsFuture =
                CompletableFuture.supplyAsync(
                        () -> searchAssets(userId, query, encryptionKey, effectiveLimit));
        CompletableFuture<List<SearchResultDto>> realEstateFuture =
                CompletableFuture.supplyAsync(
                        () -> searchRealEstate(userId, query, encryptionKey, effectiveLimit));
        CompletableFuture<List<SearchResultDto>> liabilitiesFuture =
                CompletableFuture.supplyAsync(
                        () -> searchLiabilities(userId, query, encryptionKey, effectiveLimit));
        CompletableFuture<List<SearchResultDto>> budgetsFuture =
                CompletableFuture.supplyAsync(
                        () -> searchBudgets(userId, query, encryptionKey, effectiveLimit));
        CompletableFuture<List<SearchResultDto>> recurringFuture =
                CompletableFuture.supplyAsync(
                        () ->
                                searchRecurringTransactions(
                                        userId, query, encryptionKey, effectiveLimit));
        CompletableFuture<List<SearchResultDto>> categoriesFuture =
                CompletableFuture.supplyAsync(
                        () -> searchCategories(userId, query, encryptionKey, effectiveLimit));

        // Wait for all to complete
        CompletableFuture.allOf(
                        transactionsFuture,
                        accountsFuture,
                        assetsFuture,
                        realEstateFuture,
                        liabilitiesFuture,
                        budgetsFuture,
                        recurringFuture,
                        categoriesFuture)
                .join();

        // Combine results
        List<SearchResultDto> allResults = new ArrayList<>();
        try {
            allResults.addAll(transactionsFuture.get());
            allResults.addAll(accountsFuture.get());
            allResults.addAll(assetsFuture.get());
            allResults.addAll(realEstateFuture.get());
            allResults.addAll(liabilitiesFuture.get());
            allResults.addAll(budgetsFuture.get());
            allResults.addAll(recurringFuture.get());
            allResults.addAll(categoriesFuture.get());
        } catch (Exception e) {
            log.error("Error gathering search results", e);
        }

        // Group results by type
        Map<String, List<SearchResultDto>> resultsByType =
                allResults.stream()
                        .collect(Collectors.groupingBy(result -> result.getResultType().name()));

        // Count results per type
        Map<String, Integer> countsPerType =
                resultsByType.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey, entry -> entry.getValue().size()));

        long executionTime = System.currentTimeMillis() - startTime;

        log.info(
                "Global search completed for user {} in {}ms. Found {} results across {} types",
                userId,
                executionTime,
                allResults.size(),
                resultsByType.size());

        return GlobalSearchResponse.builder()
                .query(query)
                .totalResults(allResults.size())
                .resultsByType(resultsByType)
                .countsPerType(countsPerType)
                .executionTimeMs(executionTime)
                .hasMore(allResults.size() >= effectiveLimit)
                .limit(effectiveLimit)
                .build();
    }

    /**
     * Performs an advanced search with filters across financial entities.
     *
     * <p>This method provides advanced filtering capabilities beyond basic keyword search:
     *
     * <ul>
     *   <li>Filter by entity types (transactions, accounts, assets, etc.)
     *   <li>Filter transactions by account, category, date range, amount range, tags,
     *       reconciliation status, and type
     *   <li>Filter assets by account
     *   <li>All results limited by the specified limit
     * </ul>
     *
     * <p>If no advanced filters are specified, behaves identically to globalSearch.
     *
     * @param userId User ID performing the search
     * @param request Advanced search request with query and filters
     * @param encryptionKey User's encryption key for decrypting sensitive fields
     * @return GlobalSearchResponse containing filtered search results
     * @throws IllegalArgumentException if request or query is invalid
     */
    public GlobalSearchResponse advancedSearch(
            Long userId, AdvancedSearchRequest request, SecretKey encryptionKey) {
        long startTime = System.currentTimeMillis();

        // Validate input
        if (request == null || request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            throw new IllegalArgumentException("Search query cannot be empty");
        }

        // Apply limits
        int effectiveLimit =
                (request.getLimit() != null
                                && request.getLimit() > 0
                                && request.getLimit() <= MAX_LIMIT)
                        ? request.getLimit()
                        : DEFAULT_LIMIT;

        log.debug(
                "Performing advanced search for user {} with query: {} and filters: {}",
                userId,
                request.getQuery(),
                request.hasAdvancedFilters());

        // Determine which entity types to search
        List<SearchResultType> entityTypesToSearch =
                request.getEntityTypes() != null && !request.getEntityTypes().isEmpty()
                        ? request.getEntityTypes()
                        : Arrays.asList(SearchResultType.values());

        // Search across specified entity types
        List<SearchResultDto> allResults = new ArrayList<>();

        // Search transactions with filters
        if (entityTypesToSearch.contains(SearchResultType.TRANSACTION)) {
            List<SearchResultDto> transactionResults =
                    searchTransactionsWithFilters(userId, request, encryptionKey, effectiveLimit);
            allResults.addAll(transactionResults);
        }

        // Search accounts with filters
        if (entityTypesToSearch.contains(SearchResultType.ACCOUNT)) {
            List<SearchResultDto> accountResults =
                    searchAccountsWithFilters(userId, request, encryptionKey, effectiveLimit);
            allResults.addAll(accountResults);
        }

        // Search assets with filters
        if (entityTypesToSearch.contains(SearchResultType.ASSET)) {
            List<SearchResultDto> assetResults =
                    searchAssetsWithFilters(userId, request, encryptionKey, effectiveLimit);
            allResults.addAll(assetResults);
        }

        // Search real estate (no additional filters)
        if (entityTypesToSearch.contains(SearchResultType.REAL_ESTATE)) {
            List<SearchResultDto> realEstateResults =
                    searchRealEstate(userId, request.getQuery(), encryptionKey, effectiveLimit);
            allResults.addAll(realEstateResults);
        }

        // Search liabilities (no additional filters)
        if (entityTypesToSearch.contains(SearchResultType.LIABILITY)) {
            List<SearchResultDto> liabilityResults =
                    searchLiabilities(userId, request.getQuery(), encryptionKey, effectiveLimit);
            allResults.addAll(liabilityResults);
        }

        // Search budgets
        if (entityTypesToSearch.contains(SearchResultType.BUDGET)) {
            List<SearchResultDto> budgetResults =
                    searchBudgets(userId, request.getQuery(), encryptionKey, effectiveLimit);
            allResults.addAll(budgetResults);
        }

        // Search recurring transactions
        if (entityTypesToSearch.contains(SearchResultType.RECURRING_TRANSACTION)) {
            List<SearchResultDto> recurringResults =
                    searchRecurringTransactions(
                            userId, request.getQuery(), encryptionKey, effectiveLimit);
            allResults.addAll(recurringResults);
        }

        // Search categories
        if (entityTypesToSearch.contains(SearchResultType.CATEGORY)) {
            List<SearchResultDto> categoryResults =
                    searchCategories(userId, request.getQuery(), encryptionKey, effectiveLimit);
            allResults.addAll(categoryResults);
        }

        // Limit total results
        if (allResults.size() > effectiveLimit) {
            allResults = allResults.stream().limit(effectiveLimit).collect(Collectors.toList());
        }

        // Group results by type
        Map<String, List<SearchResultDto>> resultsByType =
                allResults.stream()
                        .collect(Collectors.groupingBy(result -> result.getResultType().name()));

        // Count results per type
        Map<String, Integer> countsPerType =
                resultsByType.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));

        long executionTime = System.currentTimeMillis() - startTime;

        log.debug(
                "Advanced search completed in {}ms, found {} results",
                executionTime,
                allResults.size());

        return GlobalSearchResponse.builder()
                .query(request.getQuery())
                .totalResults(allResults.size())
                .resultsByType(resultsByType)
                .countsPerType(countsPerType)
                .executionTimeMs(executionTime)
                .hasMore(allResults.size() >= effectiveLimit)
                .limit(effectiveLimit)
                .build();
    }

    /** Searches transactions with advanced filters applied. */
    private List<SearchResultDto> searchTransactionsWithFilters(
            Long userId, AdvancedSearchRequest request, SecretKey encryptionKey, int limit) {
        try {
            // Build dynamic SQL query based on filters
            StringBuilder sql =
                    new StringBuilder(
                            """
                    SELECT t.id, t.amount, t.currency, t.transaction_date, t.transaction_type,
                           t.description, t.notes, t.tags, t.payee, t.created_at, t.updated_at,
                           a.name as account_name, c.name as category_name, c.icon, c.color,
                           fts.rank
                    FROM transactions t
                    INNER JOIN transactions_fts fts ON t.id = fts.transaction_id
                    LEFT JOIN accounts a ON t.account_id = a.id
                    LEFT JOIN categories c ON t.category_id = c.id
                    WHERE fts.transactions_fts MATCH ?
                      AND fts.user_id = ?
                      AND t.is_deleted = 0
                    """);

            List<Object> params = new ArrayList<>();
            params.add(request.getQuery());
            params.add(userId);

            // Apply filters
            if (request.getAccountIds() != null && !request.getAccountIds().isEmpty()) {
                String placeholders =
                        String.join(",", Collections.nCopies(request.getAccountIds().size(), "?"));
                sql.append(" AND (t.account_id IN (").append(placeholders).append(")");
                sql.append(" OR t.to_account_id IN (").append(placeholders).append("))");
                params.addAll(request.getAccountIds());
                params.addAll(request.getAccountIds());
            }

            if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
                String placeholders =
                        String.join(",", Collections.nCopies(request.getCategoryIds().size(), "?"));
                sql.append(" AND t.category_id IN (").append(placeholders).append(")");
                params.addAll(request.getCategoryIds());
            }

            if (request.getMinAmount() != null) {
                sql.append(" AND t.amount >= ?");
                params.add(request.getMinAmount());
            }

            if (request.getMaxAmount() != null) {
                sql.append(" AND t.amount <= ?");
                params.add(request.getMaxAmount());
            }

            if (request.getDateFrom() != null) {
                sql.append(" AND t.transaction_date >= ?");
                params.add(request.getDateFrom().toString());
            }

            if (request.getDateTo() != null) {
                sql.append(" AND t.transaction_date <= ?");
                params.add(request.getDateTo().toString());
            }

            if (request.getTags() != null && !request.getTags().isEmpty()) {
                sql.append(" AND (");
                for (int i = 0; i < request.getTags().size(); i++) {
                    if (i > 0) sql.append(" OR ");
                    sql.append("t.tags LIKE ?");
                    params.add("%" + request.getTags().get(i) + "%");
                }
                sql.append(")");
            }

            if (request.getIsReconciled() != null) {
                sql.append(" AND t.is_reconciled = ?");
                params.add(request.getIsReconciled() ? 1 : 0);
            }

            if (request.getTransactionType() != null) {
                sql.append(" AND t.transaction_type = ?");
                params.add(request.getTransactionType().name());
            }

            sql.append(" ORDER BY fts.rank LIMIT ?");
            params.add(limit);

            return jdbcTemplate
                    .query(
                            sql.toString(),
                            (rs, rowNum) -> {
                                try {
                                    String encryptedDesc = rs.getString("description");
                                    String encryptedNotes = rs.getString("notes");

                                    String decryptedDesc =
                                            encryptedDesc != null
                                                    ? encryptionService.decrypt(
                                                            encryptedDesc, encryptionKey)
                                                    : null;
                                    String decryptedNotes =
                                            encryptedNotes != null
                                                    ? encryptionService.decrypt(
                                                            encryptedNotes, encryptionKey)
                                                    : null;

                                    // Parse tags
                                    String tagsStr = rs.getString("tags");
                                    List<String> tagList =
                                            (tagsStr != null && !tagsStr.isEmpty())
                                                    ? Arrays.asList(tagsStr.split(","))
                                                    : Collections.emptyList();

                                    String snippet =
                                            createSnippet(
                                                    decryptedDesc,
                                                    decryptedNotes,
                                                    request.getQuery(),
                                                    150);

                                    return SearchResultDto.builder()
                                            .resultType(SearchResultType.TRANSACTION)
                                            .id(rs.getLong("id"))
                                            .title(decryptedDesc)
                                            .subtitle(rs.getString("account_name"))
                                            .amount(rs.getBigDecimal("amount"))
                                            .currency(rs.getString("currency"))
                                            .date(rs.getDate("transaction_date").toLocalDate())
                                            .icon(
                                                    rs.getString("icon") != null
                                                            ? rs.getString("icon")
                                                            : "Receipt")
                                            .color(rs.getString("color"))
                                            .tags(tagList)
                                            .rank(rs.getDouble("rank"))
                                            .snippet(snippet)
                                            .createdAt(
                                                    rs.getTimestamp("created_at").toLocalDateTime())
                                            .updatedAt(
                                                    rs.getTimestamp("updated_at").toLocalDateTime())
                                            .build();
                                } catch (Exception e) {
                                    log.error("Error processing transaction search result", e);
                                    return null;
                                }
                            },
                            params.toArray())
                    .stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching transactions with filters for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    /** Searches accounts with advanced filters applied. */
    private List<SearchResultDto> searchAccountsWithFilters(
            Long userId, AdvancedSearchRequest request, SecretKey encryptionKey, int limit) {
        try {
            // Get all accounts and filter programmatically (since we need to decrypt first)
            List<Account> accounts = accountRepository.findByUserIdAndIsActive(userId, true);

            return accounts.stream()
                    .filter(
                            account -> {
                                // Apply account ID filter
                                if (request.getAccountIds() != null
                                        && !request.getAccountIds().isEmpty()) {
                                    if (!request.getAccountIds().contains(account.getId())) {
                                        return false;
                                    }
                                }

                                // Decrypt and match query
                                try {
                                    String name =
                                            account.getName() != null
                                                    ? encryptionService.decrypt(
                                                            account.getName(), encryptionKey)
                                                    : "";
                                    String description =
                                            account.getDescription() != null
                                                    ? encryptionService.decrypt(
                                                            account.getDescription(), encryptionKey)
                                                    : "";
                                    String searchText = (name + " " + description).toLowerCase();
                                    return searchText.contains(request.getQuery().toLowerCase());
                                } catch (Exception e) {
                                    log.warn("Failed to decrypt account for search", e);
                                    return false;
                                }
                            })
                    .map(
                            account -> {
                                try {
                                    String name =
                                            account.getName() != null
                                                    ? encryptionService.decrypt(
                                                            account.getName(), encryptionKey)
                                                    : "";
                                    String description =
                                            account.getDescription() != null
                                                    ? encryptionService.decrypt(
                                                            account.getDescription(), encryptionKey)
                                                    : "";
                                    String snippet =
                                            createSnippet(
                                                    name, description, request.getQuery(), 150);

                                    return SearchResultDto.builder()
                                            .resultType(SearchResultType.ACCOUNT)
                                            .id(account.getId())
                                            .title(name)
                                            .subtitle(account.getType().name())
                                            .amount(account.getBalance())
                                            .currency(account.getCurrency())
                                            .icon(getAccountTypeIcon(account.getType()))
                                            .color(getAccountTypeColor(account.getType()))
                                            .snippet(snippet)
                                            .createdAt(account.getCreatedAt())
                                            .updatedAt(account.getUpdatedAt())
                                            .build();
                                } catch (Exception e) {
                                    log.error("Error processing account search result", e);
                                    return null;
                                }
                            })
                    .filter(Objects::nonNull)
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching accounts with filters for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    /** Searches assets with advanced filters applied. */
    private List<SearchResultDto> searchAssetsWithFilters(
            Long userId, AdvancedSearchRequest request, SecretKey encryptionKey, int limit) {
        try {
            // Get all assets and filter programmatically
            List<Asset> assets = assetRepository.findByUserId(userId);

            return assets.stream()
                    .filter(
                            asset -> {
                                // Apply account ID filter
                                if (request.getAccountIds() != null
                                        && !request.getAccountIds().isEmpty()) {
                                    if (asset.getAccountId() == null
                                            || !request.getAccountIds()
                                                    .contains(asset.getAccountId())) {
                                        return false;
                                    }
                                }

                                // Apply amount filter (currentPrice)
                                if (request.getMinAmount() != null
                                        && asset.getCurrentPrice() != null) {
                                    if (asset.getCurrentPrice().compareTo(request.getMinAmount())
                                            < 0) {
                                        return false;
                                    }
                                }

                                if (request.getMaxAmount() != null
                                        && asset.getCurrentPrice() != null) {
                                    if (asset.getCurrentPrice().compareTo(request.getMaxAmount())
                                            > 0) {
                                        return false;
                                    }
                                }

                                // Apply date filter (purchaseDate)
                                if (request.getDateFrom() != null
                                        && asset.getPurchaseDate() != null) {
                                    if (asset.getPurchaseDate().isBefore(request.getDateFrom())) {
                                        return false;
                                    }
                                }

                                if (request.getDateTo() != null
                                        && asset.getPurchaseDate() != null) {
                                    if (asset.getPurchaseDate().isAfter(request.getDateTo())) {
                                        return false;
                                    }
                                }

                                // Decrypt and match query
                                try {
                                    String name =
                                            asset.getName() != null
                                                    ? encryptionService.decrypt(
                                                            asset.getName(), encryptionKey)
                                                    : "";
                                    String symbol =
                                            asset.getSymbol() != null ? asset.getSymbol() : "";
                                    String searchText = (name + " " + symbol).toLowerCase();
                                    return searchText.contains(request.getQuery().toLowerCase());
                                } catch (Exception e) {
                                    log.warn("Failed to decrypt asset for search", e);
                                    return false;
                                }
                            })
                    .map(
                            asset -> {
                                try {
                                    String name =
                                            asset.getName() != null
                                                    ? encryptionService.decrypt(
                                                            asset.getName(), encryptionKey)
                                                    : "";
                                    String symbol =
                                            asset.getSymbol() != null ? asset.getSymbol() : "";
                                    String snippet =
                                            createSnippet(name, symbol, request.getQuery(), 150);

                                    BigDecimal currentValue =
                                            asset.getCurrentPrice() != null
                                                            && asset.getQuantity() != null
                                                    ? asset.getCurrentPrice()
                                                            .multiply(asset.getQuantity())
                                                    : BigDecimal.ZERO;

                                    return SearchResultDto.builder()
                                            .resultType(SearchResultType.ASSET)
                                            .id(asset.getId())
                                            .title(name)
                                            .subtitle(symbol + " - " + asset.getType().name())
                                            .amount(currentValue)
                                            .currency(asset.getCurrency())
                                            .date(asset.getPurchaseDate())
                                            .icon(getAssetTypeIcon(asset.getType()))
                                            .color("#8b5cf6")
                                            .snippet(snippet)
                                            .createdAt(asset.getCreatedAt())
                                            .updatedAt(asset.getUpdatedAt())
                                            .build();
                                } catch (Exception e) {
                                    log.error("Error processing asset search result", e);
                                    return null;
                                }
                            })
                    .filter(Objects::nonNull)
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching assets with filters for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Searches transactions using FTS5 full-text search.
     *
     * <p>Queries the transactions_fts virtual table for matches in description, notes, tags, and
     * payee.
     *
     * @param userId User ID to filter results
     * @param query FTS5 search query
     * @param encryptionKey User's encryption key
     * @param limit Maximum results
     * @return List of transaction search results
     */
    private List<SearchResultDto> searchTransactions(
            Long userId, String query, SecretKey encryptionKey, int limit) {
        try {
            // Query FTS5 table for matching transactions
            String sql =
                    """
                    SELECT t.id, t.amount, t.currency, t.transaction_date, t.transaction_type,
                           t.description, t.notes, t.tags, t.payee, t.created_at, t.updated_at,
                           a.name as account_name, c.name as category_name, c.icon, c.color,
                           fts.rank
                    FROM transactions t
                    INNER JOIN transactions_fts fts ON t.id = fts.transaction_id
                    LEFT JOIN accounts a ON t.account_id = a.id
                    LEFT JOIN categories c ON t.category_id = c.id
                    WHERE fts.transactions_fts MATCH ?
                      AND fts.user_id = ?
                      AND t.is_deleted = 0
                    ORDER BY fts.rank
                    LIMIT ?
                    """;

            return jdbcTemplate
                    .query(
                            sql,
                            (rs, rowNum) -> {
                                try {
                                    String encryptedDesc = rs.getString("description");
                                    String encryptedNotes = rs.getString("notes");

                                    String decryptedDesc = null;
                                    if (encryptedDesc != null) {
                                        try {
                                            decryptedDesc =
                                                    encryptionService.decrypt(
                                                            encryptedDesc, encryptionKey);
                                        } catch (Exception decryptEx) {
                                            log.debug(
                                                    "Description not encrypted for transaction, using raw value");
                                            decryptedDesc = encryptedDesc;
                                        }
                                    }
                                    String decryptedNotes = null;
                                    if (encryptedNotes != null) {
                                        try {
                                            decryptedNotes =
                                                    encryptionService.decrypt(
                                                            encryptedNotes, encryptionKey);
                                        } catch (Exception decryptEx) {
                                            log.debug(
                                                    "Notes not encrypted for transaction, using raw value");
                                            decryptedNotes = encryptedNotes;
                                        }
                                    }

                                    // Parse tags from comma-separated string
                                    String tagsStr = rs.getString("tags");
                                    List<String> tags =
                                            tagsStr != null && !tagsStr.isEmpty()
                                                    ? Arrays.asList(tagsStr.split(","))
                                                    : Collections.emptyList();

                                    return SearchResultDto.builder()
                                            .resultType(SearchResultType.TRANSACTION)
                                            .id(rs.getLong("id"))
                                            .title(
                                                    decryptedDesc != null
                                                            ? decryptedDesc
                                                            : "No description")
                                            .subtitle(rs.getString("account_name"))
                                            .amount(new BigDecimal(rs.getString("amount")))
                                            .currency(rs.getString("currency"))
                                            .date(LocalDate.parse(rs.getString("transaction_date")))
                                            .icon(rs.getString("icon"))
                                            .color(rs.getString("color"))
                                            .tags(tags)
                                            .rank(rs.getDouble("rank"))
                                            .snippet(
                                                    createSnippet(
                                                            decryptedDesc,
                                                            decryptedNotes,
                                                            query,
                                                            100))
                                            .createdAt(
                                                    parseSqliteTimestamp(
                                                            rs.getString("created_at")))
                                            .updatedAt(
                                                    rs.getString("updated_at") != null
                                                            ? parseSqliteTimestamp(
                                                                    rs.getString("updated_at"))
                                                            : null)
                                            .build();
                                } catch (Exception e) {
                                    log.error("Error decrypting transaction data", e);
                                    return null;
                                }
                            },
                            query,
                            userId,
                            limit)
                    .stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching transactions for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    /** Searches accounts by name. */
    private List<SearchResultDto> searchAccounts(
            Long userId, String query, SecretKey encryptionKey, int limit) {
        try {
            List<Account> accounts = accountRepository.findByUserId(userId);

            return accounts.parallelStream()
                    .map(
                            account -> {
                                try {
                                    String decryptedName =
                                            encryptionService.decrypt(
                                                    account.getName(), encryptionKey);
                                    String decryptedDesc =
                                            account.getDescription() != null
                                                    ? encryptionService.decrypt(
                                                            account.getDescription(), encryptionKey)
                                                    : null;

                                    // Check if query matches name or description
                                    String lowerQuery = query.toLowerCase();
                                    if (decryptedName.toLowerCase().contains(lowerQuery)
                                            || (decryptedDesc != null
                                                    && decryptedDesc
                                                            .toLowerCase()
                                                            .contains(lowerQuery))) {

                                        return SearchResultDto.builder()
                                                .resultType(SearchResultType.ACCOUNT)
                                                .id(account.getId())
                                                .title(decryptedName)
                                                .subtitle(account.getType().name())
                                                .amount(account.getBalance())
                                                .currency(account.getCurrency())
                                                .icon(getAccountTypeIcon(account.getType()))
                                                .color(getAccountTypeColor(account.getType()))
                                                .createdAt(account.getCreatedAt())
                                                .updatedAt(account.getUpdatedAt())
                                                .build();
                                    }
                                    return null;
                                } catch (Exception e) {
                                    log.error("Error decrypting account data", e);
                                    return null;
                                }
                            })
                    .filter(Objects::nonNull)
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching accounts for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    /** Searches assets by name and ticker symbol. */
    private List<SearchResultDto> searchAssets(
            Long userId, String query, SecretKey encryptionKey, int limit) {
        try {
            List<Asset> assets = assetRepository.findByUserId(userId);
            String lowerQuery = query.toLowerCase();

            return assets.parallelStream()
                    .map(
                            asset -> {
                                try {
                                    String decryptedName =
                                            encryptionService.decrypt(
                                                    asset.getName(), encryptionKey);

                                    // Check if query matches name or symbol
                                    if (decryptedName.toLowerCase().contains(lowerQuery)
                                            || (asset.getSymbol() != null
                                                    && asset.getSymbol()
                                                            .toLowerCase()
                                                            .contains(lowerQuery))) {

                                        BigDecimal value =
                                                asset.getQuantity()
                                                        .multiply(asset.getCurrentPrice());

                                        return SearchResultDto.builder()
                                                .resultType(SearchResultType.ASSET)
                                                .id(asset.getId())
                                                .title(decryptedName)
                                                .subtitle(
                                                        asset.getSymbol() != null
                                                                ? asset.getSymbol()
                                                                : asset.getType().name())
                                                .amount(value)
                                                .currency(asset.getCurrency())
                                                .date(asset.getPurchaseDate())
                                                .icon(getAssetTypeIcon(asset.getType()))
                                                .color("#10b981")
                                                .createdAt(asset.getCreatedAt())
                                                .updatedAt(asset.getUpdatedAt())
                                                .build();
                                    }
                                    return null;
                                } catch (Exception e) {
                                    log.error("Error decrypting asset data", e);
                                    return null;
                                }
                            })
                    .filter(Objects::nonNull)
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching assets for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    /** Searches real estate properties by name and address. */
    private List<SearchResultDto> searchRealEstate(
            Long userId, String query, SecretKey encryptionKey, int limit) {
        try {
            List<RealEstateProperty> properties = realEstateRepository.findByUserId(userId);
            String lowerQuery = query.toLowerCase();

            return properties.parallelStream()
                    .filter(RealEstateProperty::isActive)
                    .map(
                            property -> {
                                try {
                                    String decryptedName =
                                            encryptionService.decrypt(
                                                    property.getName(), encryptionKey);
                                    String decryptedAddress =
                                            encryptionService.decrypt(
                                                    property.getAddress(), encryptionKey);

                                    // Check if query matches name or address
                                    if (decryptedName.toLowerCase().contains(lowerQuery)
                                            || decryptedAddress
                                                    .toLowerCase()
                                                    .contains(lowerQuery)) {

                                        String currentValueStr =
                                                encryptionService.decrypt(
                                                        property.getCurrentValue(), encryptionKey);
                                        BigDecimal currentValue = new BigDecimal(currentValueStr);

                                        return SearchResultDto.builder()
                                                .resultType(SearchResultType.REAL_ESTATE)
                                                .id(property.getId())
                                                .title(decryptedName)
                                                .subtitle(decryptedAddress)
                                                .amount(currentValue)
                                                .currency(property.getCurrency())
                                                .date(property.getPurchaseDate())
                                                .icon("Home")
                                                .color("#8b5cf6")
                                                .createdAt(property.getCreatedAt())
                                                .updatedAt(property.getUpdatedAt())
                                                .build();
                                    }
                                    return null;
                                } catch (Exception e) {
                                    log.error("Error decrypting real estate data", e);
                                    return null;
                                }
                            })
                    .filter(Objects::nonNull)
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching real estate for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    /** Searches liabilities by name. */
    private List<SearchResultDto> searchLiabilities(
            Long userId, String query, SecretKey encryptionKey, int limit) {
        try {
            List<Liability> liabilities =
                    liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId);
            String lowerQuery = query.toLowerCase();

            return liabilities.parallelStream()
                    .map(
                            liability -> {
                                try {
                                    String decryptedName =
                                            encryptionService.decrypt(
                                                    liability.getName(), encryptionKey);

                                    // Check if query matches name
                                    if (decryptedName.toLowerCase().contains(lowerQuery)) {

                                        String currentBalanceStr =
                                                encryptionService.decrypt(
                                                        liability.getCurrentBalance(),
                                                        encryptionKey);
                                        BigDecimal currentBalance =
                                                new BigDecimal(currentBalanceStr);

                                        return SearchResultDto.builder()
                                                .resultType(SearchResultType.LIABILITY)
                                                .id(liability.getId())
                                                .title(decryptedName)
                                                .subtitle(liability.getType().name())
                                                .amount(currentBalance)
                                                .currency(liability.getCurrency())
                                                .date(liability.getStartDate())
                                                .icon("CreditCard")
                                                .color("#ef4444")
                                                .createdAt(liability.getCreatedAt())
                                                .updatedAt(liability.getUpdatedAt())
                                                .build();
                                    }
                                    return null;
                                } catch (Exception e) {
                                    log.error("Error decrypting liability data", e);
                                    return null;
                                }
                            })
                    .filter(Objects::nonNull)
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching liabilities for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    /** Searches budgets by notes or category name. */
    private List<SearchResultDto> searchBudgets(
            Long userId, String query, SecretKey encryptionKey, int limit) {
        try {
            List<Budget> budgets = budgetRepository.findByUserId(userId);
            String lowerQuery = query.toLowerCase();

            return budgets.parallelStream()
                    .map(
                            budget -> {
                                try {
                                    String notes = budget.getNotes();
                                    // Category name handling
                                    Category category = budget.getCategory();
                                    String categoryName;
                                    if (category.getIsSystem()) {
                                        categoryName = category.getName();
                                    } else {
                                        categoryName =
                                                encryptionService.decrypt(
                                                        category.getName(), encryptionKey);
                                    }
                                    String period = budget.getPeriod().name();

                                    if ((notes != null && notes.toLowerCase().contains(lowerQuery))
                                            || categoryName.toLowerCase().contains(lowerQuery)
                                            || period.toLowerCase().contains(lowerQuery)) {

                                        BigDecimal amount =
                                                new BigDecimal(
                                                        encryptionService.decrypt(
                                                                budget.getAmount(), encryptionKey));

                                        return SearchResultDto.builder()
                                                .resultType(SearchResultType.BUDGET)
                                                .id(budget.getId())
                                                .title(categoryName)
                                                .subtitle(period)
                                                .amount(amount)
                                                .currency(budget.getCurrency())
                                                .icon("PieChart")
                                                .color(budget.getCategory().getColor())
                                                .snippet(
                                                        createSnippet(
                                                                categoryName, notes, query, 100))
                                                .createdAt(budget.getCreatedAt())
                                                .updatedAt(budget.getUpdatedAt())
                                                .build();
                                    }
                                    return null;
                                } catch (Exception e) {
                                    log.error("Error decrypting budget data", e);
                                    return null;
                                }
                            })
                    .filter(Objects::nonNull)
                    .limit(limit)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error searching budgets for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    /** Searches recurring transactions by description or notes. */
    private List<SearchResultDto> searchRecurringTransactions(
            Long userId, String query, SecretKey encryptionKey, int limit) {
        try {
            List<RecurringTransaction> recurring =
                    recurringTransactionRepository.findByUserId(userId);
            String lowerQuery = query.toLowerCase();

            return recurring.stream()
                    .map(
                            rt -> {
                                try {
                                    String description =
                                            encryptionService.decrypt(
                                                    rt.getDescription(), encryptionKey);
                                    String notes =
                                            rt.getNotes() != null
                                                    ? encryptionService.decrypt(
                                                            rt.getNotes(), encryptionKey)
                                                    : null;

                                    if (description.toLowerCase().contains(lowerQuery)
                                            || (notes != null
                                                    && notes.toLowerCase().contains(lowerQuery))) {

                                        return SearchResultDto.builder()
                                                .resultType(SearchResultType.RECURRING_TRANSACTION)
                                                .id(rt.getId())
                                                .title(description)
                                                .subtitle(
                                                        rt.getFrequency().name()
                                                                + " Recurring "
                                                                + rt.getType().name())
                                                .amount(rt.getAmount())
                                                .currency(rt.getCurrency())
                                                .date(rt.getNextOccurrence())
                                                .icon("RefreshCw")
                                                .color(
                                                        rt.getType() == TransactionType.INCOME
                                                                ? "#10b981"
                                                                : "#ef4444")
                                                .snippet(
                                                        createSnippet(
                                                                description, notes, query, 100))
                                                .createdAt(rt.getCreatedAt())
                                                .updatedAt(rt.getUpdatedAt())
                                                .build();
                                    }
                                    return null;
                                } catch (Exception e) {
                                    log.error("Error decrypting recurring transaction data", e);
                                    return null;
                                }
                            })
                    .filter(Objects::nonNull)
                    .limit(limit)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error searching recurring transactions for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    /** Searches categories by name. */
    private List<SearchResultDto> searchCategories(
            Long userId, String query, SecretKey encryptionKey, int limit) {
        try {
            List<Category> categories = categoryRepository.findByUserId(userId);
            String lowerQuery = query.toLowerCase();

            return categories.parallelStream()
                    .map(
                            category -> {
                                try {
                                    String name;
                                    if (category.getIsSystem()) {
                                        name = category.getName();
                                    } else {
                                        name =
                                                encryptionService.decrypt(
                                                        category.getName(), encryptionKey);
                                    }

                                    if (name.toLowerCase().contains(lowerQuery)) {
                                        return SearchResultDto.builder()
                                                .resultType(SearchResultType.CATEGORY)
                                                .id(category.getId())
                                                .title(name)
                                                .subtitle(category.getType().name())
                                                .icon(
                                                        category.getIcon() != null
                                                                ? category.getIcon()
                                                                : "Tag")
                                                .color(category.getColor())
                                                .createdAt(category.getCreatedAt())
                                                .updatedAt(category.getUpdatedAt())
                                                .build();
                                    }
                                    return null;
                                } catch (Exception e) {
                                    log.error("Error decrypting category data", e);
                                    return null;
                                }
                            })
                    .filter(Objects::nonNull)
                    .limit(limit)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error searching categories for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Parses a SQLite timestamp string that may have nanosecond precision (e.g.
     * "2026-04-04T22:34:36.916729129") which the JDBC getTimestamp() cannot handle.
     */
    private static final DateTimeFormatter SQLITE_TS_FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
                    .optionalStart()
                    .appendLiteral('.')
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, false)
                    .optionalEnd()
                    .optionalStart()
                    .appendPattern(" HH:mm:ss")
                    .optionalEnd()
                    .toFormatter();

    private LocalDateTime parseSqliteTimestamp(String ts) {
        if (ts == null) return null;
        try {
            // Handle both "yyyy-MM-ddTHH:mm:ss..." and "yyyy-MM-dd HH:mm:ss..." formats
            String normalised = ts.replace(' ', 'T');
            return LocalDateTime.parse(normalised, SQLITE_TS_FORMATTER);
        } catch (Exception e) {
            log.debug("Could not parse timestamp '{}', using current time as fallback", ts);
            return LocalDateTime.now();
        }
    }

    /** Creates a snippet of text showing matched keywords. */
    private String createSnippet(String description, String notes, String query, int maxLength) {
        String text = description != null ? description : "";
        if (notes != null && !notes.isEmpty()) {
            text += (text.isEmpty() ? "" : " - ") + notes;
        }

        if (text.length() <= maxLength) {
            return text;
        }

        // Try to find query in text and show context
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase().split("\\s+")[0]; // Use first word
        int index = lowerText.indexOf(lowerQuery);

        if (index >= 0) {
            int start = Math.max(0, index - 30);
            int end = Math.min(text.length(), index + lowerQuery.length() + 70);
            String snippet = text.substring(start, end);
            return (start > 0 ? "..." : "") + snippet + (end < text.length() ? "..." : "");
        }

        // No match found, just return start of text
        return text.substring(0, Math.min(maxLength, text.length())) + "...";
    }

    /** Gets icon name for account type. */
    private String getAccountTypeIcon(AccountType type) {
        return switch (type) {
            case CHECKING -> "Banknote";
            case SAVINGS -> "PiggyBank";
            case INVESTMENT -> "TrendingUp";
            case CREDIT_CARD -> "CreditCard";
            case CASH -> "Wallet";
            case OTHER -> "Briefcase";
        };
    }

    /** Gets color code for account type. */
    private String getAccountTypeColor(AccountType type) {
        return switch (type) {
            case CHECKING -> "#3b82f6";
            case SAVINGS -> "#10b981";
            case INVESTMENT -> "#8b5cf6";
            case CREDIT_CARD -> "#ef4444";
            case CASH -> "#6b7280";
            case OTHER -> "#9ca3af";
        };
    }

    /** Gets icon name for asset type. */
    private String getAssetTypeIcon(AssetType type) {
        return switch (type) {
            case STOCK -> "TrendingUp";
            case BOND -> "FileText";
            case CRYPTO -> "Bitcoin";
            case MUTUAL_FUND -> "PieChart";
            case ETF -> "BarChart";
            case COMMODITY -> "Package";
            case REAL_ESTATE -> "Home";
            case VEHICLE -> "Car";
            case JEWELRY -> "Gem";
            case COLLECTIBLE -> "Star";
            case ELECTRONICS -> "Laptop";
            case FURNITURE -> "Armchair";
            case OTHER -> "DollarSign";
        };
    }
}
