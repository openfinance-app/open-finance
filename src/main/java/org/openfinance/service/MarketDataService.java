package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.dto.MarketQuote;
import org.openfinance.dto.SymbolSearchResult;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.exception.MarketDataException;
import org.openfinance.provider.MarketDataProvider;
import org.openfinance.repository.AssetRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for fetching and updating market data for financial assets.
 *
 * <p>This service integrates with external market data providers to:
 *
 * <ul>
 *   <li>Fetch real-time quotes for individual symbols
 *   <li>Update asset prices in the database
 *   <li>Batch update all user assets
 *   <li>Search for symbols to add to portfolio
 *   <li>Retrieve historical price data
 * </ul>
 *
 * <p><strong>Caching:</strong> Quote data is cached for 15 minutes to reduce API calls. See {@link
 * org.openfinance.config.CacheConfig} for configuration.
 *
 * <p><strong>Error Handling:</strong> If market data is unavailable, methods throw {@link
 * MarketDataException}. The asset's last known price is retained.
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2024-01-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final MarketDataProvider marketDataProvider;
    private final AssetRepository assetRepository;

    /**
     * Fetches a real-time quote for a symbol without updating any assets.
     *
     * <p>Use this method for displaying current prices in the UI or for preview before adding a new
     * asset.
     *
     * <p><strong>Caching:</strong> Results are cached for 15 minutes.
     *
     * @param symbol the trading symbol (e.g., "AAPL", "BTC-USD")
     * @return the market quote with current pricing data
     * @throws MarketDataException if the symbol is invalid or the API request fails
     * @throws IllegalArgumentException if the symbol is null or empty
     */
    @Cacheable(value = "marketQuotes", key = "#symbol")
    public MarketQuote getQuote(String symbol) {
        log.debug("Fetching quote for symbol: {}", symbol);
        MarketQuote quote = marketDataProvider.getQuote(symbol);
        log.info("Successfully fetched quote for {}: price={}", symbol, quote.getPrice());
        return quote;
    }

    /**
     * Fetches real-time quotes for multiple symbols in a single batch request.
     *
     * <p>More efficient than calling {@link #getQuote(String)} multiple times.
     *
     * @param symbols list of trading symbols
     * @return list of market quotes (may be fewer than requested if some symbols are invalid)
     * @throws MarketDataException if the API request fails
     * @throws IllegalArgumentException if the symbols list is null or empty
     */
    public List<MarketQuote> getQuotes(List<String> symbols) {
        log.debug("Fetching quotes for {} symbols", symbols.size());
        List<MarketQuote> quotes = marketDataProvider.getQuotes(symbols);
        log.info(
                "Successfully fetched {} quotes out of {} requested",
                quotes.size(),
                symbols.size());
        return quotes;
    }

    /**
     * Updates the current price of a single asset from market data.
     *
     * <p>The asset must have a symbol defined. If the symbol is null or empty, this method returns
     * false without throwing an exception.
     *
     * <p>Updates the following fields:
     *
     * <ul>
     *   <li>currentPrice - from regularMarketPrice
     *   <li>lastUpdated - current timestamp
     * </ul>
     *
     * <p><strong>Cache Invalidation:</strong> Evicts the cached quote for the symbol to ensure
     * fresh data on the next request.
     *
     * @param assetId the ID of the asset to update
     * @return true if the price was successfully updated, false otherwise
     * @throws MarketDataException if the API request fails (symbol invalid, service unavailable)
     */
    @Transactional
    @CacheEvict(
            value = "marketQuotes",
            key = "#result ? @assetRepository.findById(#assetId).get().symbol : ''")
    public boolean updateAssetPrice(Long assetId) {
        log.debug("Updating price for asset ID: {}", assetId);

        Asset asset =
                assetRepository
                        .findById(assetId)
                        .orElseThrow(
                                () -> {
                                    log.error("Asset not found: {}", assetId);
                                    return new IllegalArgumentException(
                                            "Asset not found: " + assetId);
                                });

        String symbol = asset.getSymbol();
        if (symbol == null || symbol.trim().isEmpty()) {
            log.warn("Asset {} has no symbol, skipping price update", assetId);
            return false;
        }

        try {
            MarketQuote quote = marketDataProvider.getQuote(symbol);
            BigDecimal newPrice = quote.getPrice();

            if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Invalid price from market data for {}: {}", symbol, newPrice);
                return false;
            }

            BigDecimal oldPrice = asset.getCurrentPrice();
            asset.setCurrentPrice(newPrice);
            asset.setLastUpdated(LocalDateTime.now());
            assetRepository.save(asset);

            log.info("Updated asset {} ({}) price: {} -> {}", assetId, symbol, oldPrice, newPrice);
            return true;

        } catch (MarketDataException e) {
            log.error(
                    "Failed to update price for asset {} ({}): {}",
                    assetId,
                    symbol,
                    e.getMessage());
            throw e;
        }
    }

    /**
     * Updates prices for all assets owned by a user.
     *
     * <p>This method fetches quotes for all user assets that have symbols defined, then updates
     * their prices in a single transaction.
     *
     * <p>Assets without symbols are skipped. If any asset fails to update (invalid symbol, API
     * error), it is logged and skipped, but other assets are still updated.
     *
     * <p><strong>Performance:</strong> Uses batch quote fetching to minimize API calls.
     *
     * @param userId the ID of the user whose assets should be updated
     * @return the number of assets successfully updated
     */
    @Transactional
    public int updateAssetPrices(Long userId) {
        log.debug("Updating asset prices for user: {}", userId);

        List<Asset> assets = assetRepository.findByUserId(userId);
        if (assets.isEmpty()) {
            log.debug("No assets found for user {}", userId);
            return 0;
        }

        // Filter assets with symbols
        List<Asset> assetsWithSymbols =
                assets.stream()
                        .filter(
                                asset ->
                                        asset.getSymbol() != null
                                                && !asset.getSymbol().trim().isEmpty())
                        .toList();

        if (assetsWithSymbols.isEmpty()) {
            log.debug("No assets with symbols found for user {}", userId);
            return 0;
        }

        List<String> symbols =
                assetsWithSymbols.stream().map(asset -> normalizeSymbol(asset)).distinct().toList();

        try {
            // Batch fetch quotes
            List<MarketQuote> quotes = marketDataProvider.getQuotes(symbols);

            // Create a map for quick lookup
            var quoteMap =
                    quotes.stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            MarketQuote::getSymbol,
                                            quote -> quote,
                                            (q1, q2) -> q1 // Keep first if duplicates
                                            ));

            int updatedCount = 0;
            LocalDateTime now = LocalDateTime.now();

            for (Asset asset : assetsWithSymbols) {
                String normalizedSymbol = normalizeSymbol(asset);
                MarketQuote quote = quoteMap.get(normalizedSymbol);
                if (quote != null
                        && quote.getPrice() != null
                        && quote.getPrice().compareTo(BigDecimal.ZERO) > 0) {

                    asset.setCurrentPrice(quote.getPrice());
                    asset.setLastUpdated(now);
                    // Persist the normalized symbol so future lookups work correctly
                    if (!normalizedSymbol.equals(asset.getSymbol())) {
                        asset.setSymbol(normalizedSymbol);
                        log.info(
                                "Normalized symbol for asset {} from {} to {}",
                                asset.getId(),
                                asset.getSymbol(),
                                normalizedSymbol);
                    }
                    updatedCount++;
                } else {
                    log.warn(
                            "No valid quote found for asset {} ({})",
                            asset.getId(),
                            normalizedSymbol);
                }
            }

            if (updatedCount > 0) {
                assetRepository.saveAll(assetsWithSymbols);
                log.info(
                        "Updated prices for {} out of {} assets for user {}",
                        updatedCount,
                        assetsWithSymbols.size(),
                        userId);
            }

            return updatedCount;

        } catch (MarketDataException e) {
            log.error("Failed to update asset prices for user {}: {}", userId, e.getMessage());
            throw e;
        }
    }

    /**
     * Retrieves historical price data for a symbol within a date range.
     *
     * <p>Useful for charting asset performance over time.
     *
     * @param symbol the trading symbol
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return list of historical prices ordered by date (oldest first)
     * @throws MarketDataException if the symbol is invalid or the API request fails
     * @throws IllegalArgumentException if any parameter is null or dates are invalid
     */
    public List<HistoricalPrice> getHistoricalPrices(
            String symbol, LocalDate startDate, LocalDate endDate) {
        log.debug("Fetching historical prices for {} from {} to {}", symbol, startDate, endDate);
        List<HistoricalPrice> prices =
                marketDataProvider.getHistoricalPrices(symbol, startDate, endDate);
        log.info("Successfully fetched {} historical prices for {}", prices.size(), symbol);
        return prices;
    }

    /**
     * Searches for financial instruments matching a query string.
     *
     * <p>Use this for autocomplete functionality when users add new assets.
     *
     * @param query the search term (e.g., "apple", "bitcoin", "SPY")
     * @return list of matching symbols with metadata (name, type, exchange)
     * @throws MarketDataException if the API request fails
     * @throws IllegalArgumentException if the query is null or empty
     */
    public List<SymbolSearchResult> searchSymbol(String query) {
        log.debug("Searching for symbols matching: {}", query);
        List<SymbolSearchResult> results = marketDataProvider.searchSymbol(query);
        log.info("Found {} symbols matching query: {}", results.size(), query);
        return results;
    }

    private String normalizeSymbol(Asset asset) {
        String symbol = asset.getSymbol();
        if (symbol == null || symbol.isBlank()) return symbol;
        if (asset.getType() == AssetType.CRYPTO && !symbol.contains("-")) {
            return symbol.toUpperCase().trim() + "-USD";
        }
        return symbol.trim();
    }
}
