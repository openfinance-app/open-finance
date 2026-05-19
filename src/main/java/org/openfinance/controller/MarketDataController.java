package org.openfinance.controller;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.dto.MarketQuote;
import org.openfinance.dto.SymbolSearchResult;
import org.openfinance.entity.User;
import org.openfinance.exception.MarketDataException;
import org.openfinance.service.MarketDataService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for market data operations. Provides endpoints for fetching real-time quotes,
 * historical prices, and searching symbols.
 *
 * <p><strong>Base URL:</strong> /api/v1/market
 *
 * <p><strong>Authentication:</strong> All endpoints require JWT authentication.
 *
 * <p><strong>Common Error Responses:</strong>
 *
 * <ul>
 *   <li>400 Bad Request - Invalid parameters
 *   <li>401 Unauthorized - Missing or invalid JWT token
 *   <li>404 Not Found - Symbol not found
 *   <li>503 Service Unavailable - Market data provider is down
 * </ul>
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2024-01-20
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataService marketDataService;

    /**
     * Get a real-time quote for a financial instrument.
     *
     * <p><strong>Endpoint:</strong> GET /api/v1/market/quote?symbol=AAPL
     *
     * <p><strong>Request:</strong>
     *
     * <pre>
     * GET /api/v1/market/quote?symbol=AAPL
     * Authorization: Bearer {jwt_token}
     * </pre>
     *
     * <p><strong>Response (200 OK):</strong>
     *
     * <pre>
     * {
     *   "symbol": "AAPL",
     *   "name": "Apple Inc.",
     *   "price": 175.50,
     *   "change": 2.30,
     *   "changePercent": 1.33,
     *   "previousClose": 173.20,
     *   "open": 174.00,
     *   "dayHigh": 176.00,
     *   "dayLow": 173.50,
     *   "volume": 45678900,
     *   "marketCap": 2750000000000,
     *   "currency": "USD",
     *   "exchange": "NASDAQ",
     *   "timestamp": "2024-01-20T15:30:00",
     *   "marketState": "REGULAR",
     *   "quoteType": "EQUITY"
     * }
     * </pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>400 - Symbol parameter is missing or empty
     *   <li>404 - Symbol not found in market data
     *   <li>503 - Market data provider is unavailable
     * </ul>
     *
     * <p><strong>Supported Symbols:</strong>
     *
     * <ul>
     *   <li>Stocks: AAPL, MSFT, GOOGL, TSLA, etc.
     *   <li>ETFs: SPY, QQQ, VOO, etc.
     *   <li>Crypto: BTC-USD, ETH-USD, etc.
     *   <li>Indices: ^GSPC (S&P 500), ^DJI (Dow Jones), etc.
     * </ul>
     *
     * @param symbol the trading symbol (required, not blank)
     * @param authentication the authenticated user
     * @return the market quote
     * @throws IllegalArgumentException if symbol is null or empty
     * @throws MarketDataException if the symbol is not found or API fails
     */
    @GetMapping("/quote")
    public ResponseEntity<MarketQuote> getQuote(
            @RequestParam @NotBlank(message = "Symbol is required") String symbol,
            Authentication authentication) {

        log.info("User {} fetching quote for symbol: {}", authentication.getName(), symbol);

        try {
            MarketQuote quote = marketDataService.getQuote(symbol.toUpperCase());
            log.debug("Successfully fetched quote for {}: price={}", symbol, quote.getPrice());
            return ResponseEntity.ok(quote);

        } catch (MarketDataException e) {
            log.error("Failed to fetch quote for {}: {}", symbol, e.getMessage());
            throw e;
        }
    }

    /**
     * Search for financial instruments matching a query.
     *
     * <p><strong>Endpoint:</strong> GET /api/v1/market/search?q=apple
     *
     * <p><strong>Request:</strong>
     *
     * <pre>
     * GET /api/v1/market/search?q=apple
     * Authorization: Bearer {jwt_token}
     * </pre>
     *
     * <p><strong>Response (200 OK):</strong>
     *
     * <pre>
     * [
     *   {
     *     "symbol": "AAPL",
     *     "name": "Apple Inc.",
     *     "type": "EQUITY",
     *     "exchange": "NASDAQ",
     *     "exchangeDisplay": "NASDAQ Stock Market",
     *     "sector": "Technology",
     *     "industry": "Consumer Electronics"
     *   },
     *   {
     *     "symbol": "APLE",
     *     "name": "Apple Hospitality REIT Inc.",
     *     "type": "EQUITY",
     *     "exchange": "NYSE",
     *     "exchangeDisplay": "New York Stock Exchange",
     *     "sector": "Real Estate",
     *     "industry": "REIT - Hotel & Motel"
     *   }
     * ]
     * </pre>
     *
     * <p><strong>Use Cases:</strong>
     *
     * <ul>
     *   <li>Autocomplete when adding assets
     *   <li>Symbol discovery
     *   <li>Validating user input
     * </ul>
     *
     * @param query the search term (required, not blank)
     * @param authentication the authenticated user
     * @return list of matching symbols
     * @throws IllegalArgumentException if query is null or empty
     * @throws MarketDataException if the API request fails
     */
    @GetMapping("/search")
    public ResponseEntity<List<SymbolSearchResult>> searchSymbol(
            @RequestParam("q") @NotBlank(message = "Query is required") String query,
            Authentication authentication) {

        log.info("User {} searching symbols for query: {}", authentication.getName(), query);

        try {
            List<SymbolSearchResult> results = marketDataService.searchSymbol(query);
            log.debug("Found {} symbols matching query: {}", results.size(), query);
            return ResponseEntity.ok(results);

        } catch (MarketDataException e) {
            log.error("Failed to search symbols for {}: {}", query, e.getMessage());
            throw e;
        }
    }

    /**
     * Get historical prices for a symbol within a date range.
     *
     * <p><strong>Endpoint:</strong> GET
     * /api/v1/market/history?symbol=AAPL&startDate=2024-01-01&endDate=2024-01-31
     *
     * <p><strong>Request:</strong>
     *
     * <pre>
     * GET /api/v1/market/history?symbol=AAPL&startDate=2024-01-01&endDate=2024-01-31
     * Authorization: Bearer {jwt_token}
     * </pre>
     *
     * <p><strong>Response (200 OK):</strong>
     *
     * <pre>
     * [
     *   {
     *     "symbol": "AAPL",
     *     "date": "2024-01-02",
     *     "open": 185.30,
     *     "high": 186.50,
     *     "low": 184.00,
     *     "close": 185.50,
     *     "adjustedClose": 185.50,
     *     "volume": 52000000
     *   },
     *   {
     *     "symbol": "AAPL",
     *     "date": "2024-01-03",
     *     "open": 185.50,
     *     "high": 187.00,
     *     "low": 185.00,
     *     "close": 186.20,
     *     "adjustedClose": 186.20,
     *     "volume": 48000000
     *   }
     * ]
     * </pre>
     *
     * <p><strong>Date Format:</strong> yyyy-MM-dd (ISO 8601)
     *
     * <p><strong>Notes:</strong>
     *
     * <ul>
     *   <li>Data is returned in ascending date order (oldest first)
     *   <li>Weekends and market holidays have no data
     *   <li>Maximum date range: 5 years
     *   <li>Adjusted close accounts for splits and dividends
     * </ul>
     *
     * @param symbol the trading symbol (required, not blank)
     * @param startDate the start date (required, ISO format: yyyy-MM-dd)
     * @param endDate the end date (required, ISO format: yyyy-MM-dd)
     * @param authentication the authenticated user
     * @return list of historical prices ordered by date
     * @throws IllegalArgumentException if parameters are invalid or endDate < startDate
     * @throws MarketDataException if the symbol is not found or API fails
     */
    @GetMapping("/history")
    public ResponseEntity<List<HistoricalPrice>> getHistoricalPrices(
            @RequestParam @NotBlank(message = "Symbol is required") String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {

        log.info(
                "User {} fetching historical prices for {} from {} to {}",
                authentication.getName(),
                symbol,
                startDate,
                endDate);

        if (endDate.isBefore(startDate)) {
            log.error("Invalid date range: startDate={}, endDate={}", startDate, endDate);
            throw new IllegalArgumentException("End date must be after start date");
        }

        try {
            List<HistoricalPrice> prices =
                    marketDataService.getHistoricalPrices(symbol.toUpperCase(), startDate, endDate);
            log.debug("Successfully fetched {} historical prices for {}", prices.size(), symbol);
            return ResponseEntity.ok(prices);

        } catch (MarketDataException e) {
            log.error("Failed to fetch historical prices for {}: {}", symbol, e.getMessage());
            throw e;
        }
    }

    /**
     * Manually update the price of an asset from market data.
     *
     * <p><strong>Endpoint:</strong> POST /api/v1/market/assets/{assetId}/update-price
     *
     * <p><strong>Request:</strong>
     *
     * <pre>
     * POST /api/v1/market/assets/123/update-price
     * Authorization: Bearer {jwt_token}
     * </pre>
     *
     * <p><strong>Response (200 OK):</strong>
     *
     * <pre>
     * {
     *   "message": "Asset price updated successfully",
     *   "assetId": 123,
     *   "updated": true
     * }
     * </pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>400 - Asset has no symbol defined
     *   <li>404 - Asset not found
     *   <li>503 - Market data provider is unavailable
     * </ul>
     *
     * <p><strong>Notes:</strong>
     *
     * <ul>
     *   <li>Updates currentPrice and lastUpdated fields
     *   <li>Requires asset to have a symbol
     *   <li>Invalidates cached quote
     *   <li>Automatic updates run hourly during market hours
     * </ul>
     *
     * @param assetId the ID of the asset to update
     * @param authentication the authenticated user
     * @return response indicating success
     * @throws IllegalArgumentException if asset not found or has no symbol
     * @throws MarketDataException if the market data fetch fails
     */
    @PostMapping("/assets/{assetId}/update-price")
    public ResponseEntity<UpdatePriceResponse> updateAssetPrice(
            @PathVariable Long assetId, Authentication authentication) {

        log.info(
                "User {} manually updating price for asset: {}", authentication.getName(), assetId);

        try {
            boolean updated = marketDataService.updateAssetPrice(assetId);

            if (updated) {
                log.info("Successfully updated price for asset: {}", assetId);
                return ResponseEntity.ok(
                        new UpdatePriceResponse("Asset price updated successfully", assetId, true));
            } else {
                log.warn("Asset {} has no symbol, price not updated", assetId);
                return ResponseEntity.badRequest()
                        .body(
                                new UpdatePriceResponse(
                                        "Asset has no symbol defined", assetId, false));
            }

        } catch (IllegalArgumentException e) {
            log.error("Asset not found: {}", assetId);
            throw e;
        } catch (MarketDataException e) {
            log.error("Failed to update price for asset {}: {}", assetId, e.getMessage());
            throw e;
        }
    }

    /**
     * Refresh prices for all user assets in a single sequential transaction. This avoids SQLite
     * concurrent-write conflicts that occur when the browser fires one request per asset in
     * parallel.
     *
     * <p><strong>Endpoint:</strong> POST /api/v1/market/assets/refresh-all
     *
     * @param authentication the authenticated user
     * @return response indicating how many assets were updated
     */
    @PostMapping("/assets/refresh-all")
    public ResponseEntity<RefreshAllResponse> refreshAllAssetPrices(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        log.info("User {} refreshing all asset prices", user.getUsername());

        int updated = marketDataService.updateAssetPrices(user.getId());

        log.info("Refreshed {} asset prices for user {}", updated, user.getUsername());
        return ResponseEntity.ok(new RefreshAllResponse("Asset prices refreshed", updated));
    }

    /** Response DTO for update price endpoint. */
    public record UpdatePriceResponse(String message, Long assetId, boolean updated) {}

    /** Response DTO for bulk refresh endpoint. */
    public record RefreshAllResponse(String message, int updated) {}
}
