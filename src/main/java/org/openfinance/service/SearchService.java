package org.openfinance.service;

import java.math.BigDecimal;
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
    private final SearchTokenService searchTokenService;

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
    public GlobalSearchResponse globalSearch(Long userId, String query, Integer limit) {
        long startTime = System.currentTimeMillis();

        // Validate input
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Search query cannot be empty");
        }

        // Apply limits
        int effectiveLimit =
                (limit != null && limit > 0 && limit <= MAX_LIMIT) ? limit : DEFAULT_LIMIT;

        log.debug("Performing global search for user {} with query: {}", userId, query);

        // Capture encryption key from the request thread so async tasks can use it
        SecretKey encKey = org.openfinance.security.EncryptionContext.getKey();

        // Search across all entity types in parallel
        CompletableFuture<List<SearchResultDto>> transactionsFuture =
                CompletableFuture.supplyAsync(
                        () ->
                                withEncryptionKey(
                                        encKey,
                                        () -> searchTransactions(userId, query, effectiveLimit)));
        CompletableFuture<List<SearchResultDto>> accountsFuture =
                CompletableFuture.supplyAsync(
                        () ->
                                withEncryptionKey(
                                        encKey,
                                        () -> searchAccounts(userId, query, effectiveLimit)));
        CompletableFuture<List<SearchResultDto>> assetsFuture =
                CompletableFuture.supplyAsync(
                        () ->
                                withEncryptionKey(
                                        encKey, () -> searchAssets(userId, query, effectiveLimit)));
        CompletableFuture<List<SearchResultDto>> realEstateFuture =
                CompletableFuture.supplyAsync(
                        () ->
                                withEncryptionKey(
                                        encKey,
                                        () -> searchRealEstate(userId, query, effectiveLimit)));
        CompletableFuture<List<SearchResultDto>> liabilitiesFuture =
                CompletableFuture.supplyAsync(
                        () ->
                                withEncryptionKey(
                                        encKey,
                                        () -> searchLiabilities(userId, query, effectiveLimit)));
        CompletableFuture<List<SearchResultDto>> budgetsFuture =
                CompletableFuture.supplyAsync(
                        () ->
                                withEncryptionKey(
                                        encKey,
                                        () -> searchBudgets(userId, query, effectiveLimit)));
        CompletableFuture<List<SearchResultDto>> recurringFuture =
                CompletableFuture.supplyAsync(
                        () ->
                                withEncryptionKey(
                                        encKey,
                                        () ->
                                                searchRecurringTransactions(
                                                        userId, query, effectiveLimit)));
        CompletableFuture<List<SearchResultDto>> categoriesFuture =
                CompletableFuture.supplyAsync(
                        () ->
                                withEncryptionKey(
                                        encKey,
                                        () -> searchCategories(userId, query, effectiveLimit)));

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
    public GlobalSearchResponse advancedSearch(Long userId, AdvancedSearchRequest request) {
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
                    searchTransactionsWithFilters(userId, request, effectiveLimit);
            allResults.addAll(transactionResults);
        }

        // Search accounts with filters
        if (entityTypesToSearch.contains(SearchResultType.ACCOUNT)) {
            List<SearchResultDto> accountResults =
                    searchAccountsWithFilters(userId, request, effectiveLimit);
            allResults.addAll(accountResults);
        }

        // Search assets with filters
        if (entityTypesToSearch.contains(SearchResultType.ASSET)) {
            List<SearchResultDto> assetResults =
                    searchAssetsWithFilters(userId, request, effectiveLimit);
            allResults.addAll(assetResults);
        }

        // Search real estate (no additional filters)
        if (entityTypesToSearch.contains(SearchResultType.REAL_ESTATE)) {
            List<SearchResultDto> realEstateResults =
                    searchRealEstate(userId, request.getQuery(), effectiveLimit);
            allResults.addAll(realEstateResults);
        }

        // Search liabilities (no additional filters)
        if (entityTypesToSearch.contains(SearchResultType.LIABILITY)) {
            List<SearchResultDto> liabilityResults =
                    searchLiabilities(userId, request.getQuery(), effectiveLimit);
            allResults.addAll(liabilityResults);
        }

        // Search budgets
        if (entityTypesToSearch.contains(SearchResultType.BUDGET)) {
            List<SearchResultDto> budgetResults =
                    searchBudgets(userId, request.getQuery(), effectiveLimit);
            allResults.addAll(budgetResults);
        }

        // Search recurring transactions
        if (entityTypesToSearch.contains(SearchResultType.RECURRING_TRANSACTION)) {
            List<SearchResultDto> recurringResults =
                    searchRecurringTransactions(userId, request.getQuery(), effectiveLimit);
            allResults.addAll(recurringResults);
        }

        // Search categories
        if (entityTypesToSearch.contains(SearchResultType.CATEGORY)) {
            List<SearchResultDto> categoryResults =
                    searchCategories(userId, request.getQuery(), effectiveLimit);
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
            Long userId, AdvancedSearchRequest request, int limit) {
        try {
            // Encrypted fields prevent SQL-level FTS/LIKE search.
            // Fetch via JPA (which decrypts transparently) and filter in Java.
            List<org.openfinance.entity.Transaction> transactions =
                    transactionRepository.findByUserId(userId).stream()
                            .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                            .toList();

            String lowerQuery = request.getQuery().toLowerCase();

            return transactions.stream()
                    .filter(t -> matchesTextQuery(t, lowerQuery))
                    .filter(
                            t -> {
                                if (request.getAccountIds() != null
                                        && !request.getAccountIds().isEmpty()) {
                                    return request.getAccountIds().contains(t.getAccountId())
                                            || request.getAccountIds().contains(t.getToAccountId());
                                }
                                return true;
                            })
                    .filter(
                            t -> {
                                if (request.getCategoryIds() != null
                                        && !request.getCategoryIds().isEmpty()) {
                                    return t.getCategoryId() != null
                                            && request.getCategoryIds().contains(t.getCategoryId());
                                }
                                return true;
                            })
                    .filter(
                            t -> {
                                if (request.getMinAmount() != null && t.getAmount() != null) {
                                    return t.getAmount().compareTo(request.getMinAmount()) >= 0;
                                }
                                return true;
                            })
                    .filter(
                            t -> {
                                if (request.getMaxAmount() != null && t.getAmount() != null) {
                                    return t.getAmount().compareTo(request.getMaxAmount()) <= 0;
                                }
                                return true;
                            })
                    .filter(
                            t -> {
                                if (request.getDateFrom() != null && t.getDate() != null) {
                                    return !t.getDate().isBefore(request.getDateFrom());
                                }
                                return true;
                            })
                    .filter(
                            t -> {
                                if (request.getDateTo() != null && t.getDate() != null) {
                                    return !t.getDate().isAfter(request.getDateTo());
                                }
                                return true;
                            })
                    .filter(
                            t -> {
                                if (request.getTags() != null && !request.getTags().isEmpty()) {
                                    String tags = t.getTags();
                                    if (tags == null) return false;
                                    return request.getTags().stream()
                                            .anyMatch(
                                                    tag ->
                                                            tags.toLowerCase()
                                                                    .contains(tag.toLowerCase()));
                                }
                                return true;
                            })
                    .filter(
                            t -> {
                                if (request.getIsReconciled() != null) {
                                    return request.getIsReconciled().equals(t.getIsReconciled());
                                }
                                return true;
                            })
                    .filter(
                            t -> {
                                if (request.getTransactionType() != null) {
                                    return request.getTransactionType().equals(t.getType());
                                }
                                return true;
                            })
                    .limit(limit)
                    .map(t -> buildTransactionSearchResult(t, lowerQuery))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching transactions with filters for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    /** Searches accounts with advanced filters applied. */
    private List<SearchResultDto> searchAccountsWithFilters(
            Long userId, AdvancedSearchRequest request, int limit) {
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

                                // Match query
                                String name = account.getName() != null ? account.getName() : "";
                                String description =
                                        account.getDescription() != null
                                                ? account.getDescription()
                                                : "";
                                String searchText = (name + " " + description).toLowerCase();
                                return searchText.contains(request.getQuery().toLowerCase());
                            })
                    .map(
                            account -> {
                                try {
                                    String name =
                                            account.getName() != null ? account.getName() : "";
                                    String description =
                                            account.getDescription() != null
                                                    ? account.getDescription()
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
            Long userId, AdvancedSearchRequest request, int limit) {
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

                                // Match query
                                String name = asset.getName() != null ? asset.getName() : "";
                                String symbol = asset.getSymbol() != null ? asset.getSymbol() : "";
                                String searchText = (name + " " + symbol).toLowerCase();
                                return searchText.contains(request.getQuery().toLowerCase());
                            })
                    .map(
                            asset -> {
                                try {
                                    String name = asset.getName() != null ? asset.getName() : "";
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
     * Searches transactions by keyword matching on decrypted fields.
     *
     * <p>Since description, notes, tags, and payee are encrypted, FTS5 cannot be used. Fetches all
     * user transactions via JPA (which decrypts transparently) and filters in Java.
     *
     * @param userId User ID to filter results
     * @param query Search query
     * @param encryptionKey User's encryption key (unused — kept for API compat)
     * @param limit Maximum results
     * @return List of transaction search results
     */
    private List<SearchResultDto> searchTransactions(Long userId, String query, int limit) {
        try {
            String lowerQuery = query.toLowerCase();

            // Use token-based search if encryption key is available
            SecretKey contextKey = org.openfinance.security.EncryptionContext.getKey();
            if (contextKey != null) {
                SecretKey searchKey = searchTokenService.deriveSearchKey(contextKey);
                List<Long> matchingIds =
                        searchTokenService.search(userId, "TRANSACTION", query, searchKey, limit);
                if (!matchingIds.isEmpty()) {
                    return matchingIds.stream()
                            .map(id -> transactionRepository.findById(id).orElse(null))
                            .filter(Objects::nonNull)
                            .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                            .map(t -> buildTransactionSearchResult(t, lowerQuery))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                }
            }

            // Fallback: in-memory search (for entities not yet indexed)
            List<org.openfinance.entity.Transaction> transactions =
                    transactionRepository.findByUserId(userId).stream()
                            .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                            .toList();

            return transactions.stream()
                    .filter(t -> matchesTextQuery(t, lowerQuery))
                    .limit(limit)
                    .map(t -> buildTransactionSearchResult(t, lowerQuery))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching transactions for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    /** Searches accounts by name. */
    private List<SearchResultDto> searchAccounts(Long userId, String query, int limit) {
        try {
            List<Account> accounts = accountRepository.findByUserId(userId);

            return accounts.parallelStream()
                    .map(
                            account -> {
                                try {
                                    String decryptedName = account.getName();
                                    String decryptedDesc = account.getDescription();

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
    private List<SearchResultDto> searchAssets(Long userId, String query, int limit) {
        try {
            List<Asset> assets = assetRepository.findByUserId(userId);
            String lowerQuery = query.toLowerCase();

            return assets.parallelStream()
                    .map(
                            asset -> {
                                try {
                                    String decryptedName = asset.getName();

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
    private List<SearchResultDto> searchRealEstate(Long userId, String query, int limit) {
        try {
            List<RealEstateProperty> properties = realEstateRepository.findByUserId(userId);
            String lowerQuery = query.toLowerCase();

            return properties.parallelStream()
                    .filter(RealEstateProperty::isActive)
                    .map(
                            property -> {
                                try {
                                    String decryptedName = property.getName();
                                    String decryptedAddress = property.getAddress();

                                    // Check if query matches name or address
                                    if (decryptedName.toLowerCase().contains(lowerQuery)
                                            || decryptedAddress
                                                    .toLowerCase()
                                                    .contains(lowerQuery)) {

                                        BigDecimal currentValue =
                                                new BigDecimal(property.getCurrentValue());

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
    private List<SearchResultDto> searchLiabilities(Long userId, String query, int limit) {
        try {
            List<Liability> liabilities =
                    liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId);
            String lowerQuery = query.toLowerCase();

            return liabilities.parallelStream()
                    .map(
                            liability -> {
                                try {
                                    String decryptedName = liability.getName();

                                    // Check if query matches name
                                    if (decryptedName.toLowerCase().contains(lowerQuery)) {

                                        BigDecimal currentBalance =
                                                new BigDecimal(liability.getCurrentBalance());

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
    private List<SearchResultDto> searchBudgets(Long userId, String query, int limit) {
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
                                    categoryName = category.getName();
                                    String period = budget.getPeriod().name();

                                    if ((notes != null && notes.toLowerCase().contains(lowerQuery))
                                            || categoryName.toLowerCase().contains(lowerQuery)
                                            || period.toLowerCase().contains(lowerQuery)) {

                                        BigDecimal amount = new BigDecimal(budget.getAmount());

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
            Long userId, String query, int limit) {
        try {
            List<RecurringTransaction> recurring =
                    recurringTransactionRepository.findByUserId(userId);
            String lowerQuery = query.toLowerCase();

            return recurring.stream()
                    .map(
                            rt -> {
                                try {
                                    String description = rt.getDescription();
                                    String notes = rt.getNotes();

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
    private List<SearchResultDto> searchCategories(Long userId, String query, int limit) {
        try {
            List<Category> categories = categoryRepository.findByUserId(userId);
            String lowerQuery = query.toLowerCase();

            return categories.parallelStream()
                    .map(
                            category -> {
                                try {
                                    String name;
                                    name = category.getName();

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

    /** Checks if a transaction's text fields match the search query. */
    private boolean matchesTextQuery(org.openfinance.entity.Transaction t, String lowerQuery) {
        if (t.getDescription() != null && t.getDescription().toLowerCase().contains(lowerQuery))
            return true;
        if (t.getNotes() != null && t.getNotes().toLowerCase().contains(lowerQuery)) return true;
        if (t.getPayee() != null && t.getPayee().toLowerCase().contains(lowerQuery)) return true;
        if (t.getTags() != null && t.getTags().toLowerCase().contains(lowerQuery)) return true;
        return false;
    }

    /** Builds a SearchResultDto from a Transaction entity. */
    private SearchResultDto buildTransactionSearchResult(
            org.openfinance.entity.Transaction t, String query) {
        try {
            String desc = t.getDescription() != null ? t.getDescription() : "No description";
            String notes = t.getNotes();

            List<String> tags = Collections.emptyList();
            if (t.getTags() != null && !t.getTags().isEmpty()) {
                tags = Arrays.asList(t.getTags().split(","));
            }

            return SearchResultDto.builder()
                    .resultType(SearchResultType.TRANSACTION)
                    .id(t.getId())
                    .title(desc)
                    .subtitle(null) // Account name not eagerly loaded
                    .amount(t.getAmount())
                    .currency(t.getCurrency())
                    .date(t.getDate())
                    .icon("Receipt")
                    .tags(tags)
                    .snippet(createSnippet(desc, notes, query, 100))
                    .createdAt(t.getCreatedAt())
                    .updatedAt(t.getUpdatedAt())
                    .build();
        } catch (Exception e) {
            log.error("Error building transaction search result", e);
            return null;
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

    /**
     * Runs a supplier with the given encryption key set in EncryptionContext, ensuring cleanup
     * afterwards. Used to propagate the request-thread key to ForkJoinPool worker threads.
     */
    private <T> T withEncryptionKey(SecretKey key, java.util.function.Supplier<T> supplier) {
        if (key != null) {
            org.openfinance.security.EncryptionContext.setKey(key);
        }
        try {
            return supplier.get();
        } finally {
            org.openfinance.security.EncryptionContext.clear();
        }
    }
}
