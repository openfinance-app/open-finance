package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.MarketQuote;
import org.openfinance.entity.Currency;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.exception.MarketDataException;
import org.openfinance.provider.MarketDataProvider;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.ExchangeRateRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing currency exchange rates and performing currency conversions.
 *
 * <p>This service integrates with Yahoo Finance to fetch real-time exchange rates for both fiat
 * currencies (USD, EUR, GBP) and cryptocurrencies (BTC, ETH).
 *
 * <p><strong>Key Features:</strong>
 *
 * <ul>
 *   <li>Fetch and store exchange rates from Yahoo Finance
 *   <li>Convert amounts between any supported currencies
 *   <li>Support historical exchange rates for date-specific conversions
 *   <li>Automatic inverse rate calculation (EUR/USD from USD/EUR)
 *   <li>Caching for frequently accessed rates (15 minutes TTL)
 * </ul>
 *
 * <p><strong>Yahoo Finance Currency Symbols:</strong>
 *
 * <ul>
 *   <li>Fiat pairs: EURUSD=X, GBPUSD=X, USDJPY=X
 *   <li>Crypto pairs: BTC-USD, ETH-USD, ADA-USD
 * </ul>
 *
 * <p><strong>Caching:</strong> Exchange rates are cached for 15 minutes to reduce API calls. See
 * {@link org.openfinance.config.CacheConfig} for configuration.
 *
 * <p><strong>Example Usage:</strong>
 *
 * <pre>
 * // Convert $100 to EUR
 * BigDecimal euros = exchangeRateService.convert(new BigDecimal("100"), "USD", "EUR");
 *
 * // Get current exchange rate
 * BigDecimal rate = exchangeRateService.getExchangeRate("USD", "EUR", null);
 *
 * // Update all rates from Yahoo Finance
 * exchangeRateService.updateExchangeRates();
 * </pre>
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2026-02-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyRepository currencyRepository;
    private final MarketDataProvider marketDataProvider;

    /**
     * Retrieves the exchange rate between two currencies for a specific date.
     *
     * <p>If date is null, returns the latest available rate. If no exact match for the date is
     * found, returns the most recent rate before that date.
     *
     * <p><strong>Lookup Strategy:</strong>
     *
     * <ol>
     *   <li>Check if source and target are the same (return 1.0)
     *   <li>Query database for direct rate (base → target)
     *   <li>Query database for inverse rate (target → base) and calculate inverse
     *   <li>Throw exception if no rate found
     * </ol>
     *
     * @param fromCurrency the source currency code (e.g., "USD")
     * @param toCurrency the target currency code (e.g., "EUR")
     * @param date the date for the rate (null for latest)
     * @return the exchange rate (1 fromCurrency = X toCurrency)
     * @throws IllegalArgumentException if currencies are invalid or not found
     * @throws IllegalStateException if no exchange rate is available
     */
    @Cacheable(
            value = "exchangeRates",
            key = "#fromCurrency + '-' + #toCurrency + '-' + (#date != null ? #date : 'latest')")
    public BigDecimal getExchangeRate(String fromCurrency, String toCurrency, LocalDate date) {
        log.debug("Getting exchange rate: {} → {} for date: {}", fromCurrency, toCurrency, date);

        // Validate currencies exist
        validateCurrency(fromCurrency);
        validateCurrency(toCurrency);

        // Same currency conversion
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            log.debug("Same currency conversion, returning 1.0");
            return BigDecimal.ONE;
        }

        // Try direct rate (from → to)
        Optional<ExchangeRate> directRate = findRate(fromCurrency, toCurrency, date);
        if (directRate.isPresent()) {
            BigDecimal rate = directRate.get().getRate();
            log.debug("Found direct rate: {} → {} = {}", fromCurrency, toCurrency, rate);
            return rate;
        }

        // Try inverse rate (to → from) and calculate inverse
        Optional<ExchangeRate> inverseRate = findRate(toCurrency, fromCurrency, date);
        if (inverseRate.isPresent()) {
            BigDecimal rate = inverseRate.get().getInverseRate();
            log.debug(
                    "Found inverse rate: {} → {} = {} (calculated from inverse)",
                    fromCurrency,
                    toCurrency,
                    rate);
            return rate;
        }

        // No cached rate found — attempt on-demand fetch from Yahoo Finance.
        // Trigger for "latest" requests: date is null, or date is today/future
        // (callers like CurrencyController pass LocalDate.now() for current-rate
        // lookups).
        boolean isLatestRequest = date == null || !date.isBefore(LocalDate.now());
        if (isLatestRequest) {
            log.info(
                    "No rate found for {} → {}, attempting on-demand fetch from Yahoo Finance",
                    fromCurrency,
                    toCurrency);
            boolean fetched = fetchAndStorePairRate(fromCurrency, toCurrency);
            if (fetched) {
                // Retry using null (latest) since the fetch stored today's rate
                Optional<ExchangeRate> refetchedDirect = findRate(fromCurrency, toCurrency, null);
                if (refetchedDirect.isPresent()) {
                    BigDecimal rate = refetchedDirect.get().getRate();
                    log.info(
                            "On-demand fetch succeeded for {} → {} = {}",
                            fromCurrency,
                            toCurrency,
                            rate);
                    return rate;
                }
                Optional<ExchangeRate> refetchedInverse = findRate(toCurrency, fromCurrency, null);
                if (refetchedInverse.isPresent()) {
                    BigDecimal rate = refetchedInverse.get().getInverseRate();
                    log.info(
                            "On-demand fetch succeeded (inverse) for {} → {} = {}",
                            fromCurrency,
                            toCurrency,
                            rate);
                    return rate;
                }
                // Cross-pair via USD: fetchAndStorePairRate stores FROM→USD and TO→USD.
                // Compute: FROM→TO = (FROM→USD) / (TO→USD).
                // We look for FROM→USD (direct) or USD→FROM (inverse), and same for TO.
                if (!"USD".equalsIgnoreCase(fromCurrency) && !"USD".equalsIgnoreCase(toCurrency)) {
                    BigDecimal fromUsd = getRateViaUsd(fromCurrency); // 1 FROM = X USD
                    BigDecimal toUsd = getRateViaUsd(toCurrency); // 1 TO = X USD
                    if (fromUsd != null && toUsd != null && toUsd.compareTo(BigDecimal.ZERO) != 0) {
                        BigDecimal crossRate = fromUsd.divide(toUsd, 8, RoundingMode.HALF_UP);
                        log.info(
                                "On-demand cross-rate via USD for {} → {} = {} (fromUsd={}, toUsd={})",
                                fromCurrency,
                                toCurrency,
                                crossRate,
                                fromUsd,
                                toUsd);
                        return crossRate;
                    }
                }
            }
        }

        // No rate found
        String dateStr = date != null ? date.toString() : "latest";
        log.error("No exchange rate found for {} → {} on {}", fromCurrency, toCurrency, dateStr);
        throw new IllegalStateException(
                String.format(
                        "No exchange rate available for %s → %s on %s",
                        fromCurrency, toCurrency, dateStr));
    }

    /**
     * Converts an amount from one currency to another using the latest exchange rate.
     *
     * @param amount the amount to convert
     * @param fromCurrency the source currency code (e.g., "USD")
     * @param toCurrency the target currency code (e.g., "EUR")
     * @return the converted amount
     * @throws IllegalArgumentException if amount is negative or currencies are invalid
     */
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        return convert(amount, fromCurrency, toCurrency, null);
    }

    /**
     * Converts an amount from one currency to another using a historical exchange rate.
     *
     * <p>The result is rounded to 8 decimal places using HALF_UP rounding mode, which is sufficient
     * for both fiat currencies (2-4 decimals) and cryptocurrencies.
     *
     * @param amount the amount to convert
     * @param fromCurrency the source currency code (e.g., "USD")
     * @param toCurrency the target currency code (e.g., "EUR")
     * @param date the date for the exchange rate (null for latest)
     * @return the converted amount rounded to 8 decimal places
     * @throws IllegalArgumentException if amount is negative or currencies are invalid
     */
    public BigDecimal convert(
            BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }

        log.debug("Converting {} {} to {} on {}", amount, fromCurrency, toCurrency, date);

        BigDecimal rate = getExchangeRate(fromCurrency, toCurrency, date);
        BigDecimal converted = amount.multiply(rate).setScale(8, RoundingMode.HALF_UP);

        log.debug(
                "Converted {} {} = {} {} (rate: {})",
                amount,
                fromCurrency,
                converted,
                toCurrency,
                rate);
        return converted;
    }

    /**
     * Fetches and stores the latest exchange rates from Yahoo Finance.
     *
     * <p>This method fetches rates for all active currencies in the database relative to USD. It
     * also fetches cross-rates for major currencies (EUR, GBP, JPY).
     *
     * <p><strong>Process:</strong>
     *
     * <ol>
     *   <li>Query all active currencies from database
     *   <li>Build Yahoo Finance symbols (e.g., EURUSD=X, BTC-USD)
     *   <li>Fetch quotes from Yahoo Finance API
     *   <li>Store rates in database with today's date
     *   <li>Clear exchange rate cache
     * </ol>
     *
     * <p><strong>Rate Limits:</strong> Yahoo Finance has no official rate limits, but this method
     * should be called at most once per day via scheduled job.
     *
     * @return the number of exchange rates successfully updated
     * @throws MarketDataException if the Yahoo Finance API is unavailable
     */
    @Transactional
    @CacheEvict(value = "exchangeRates", allEntries = true)
    public int updateExchangeRates() {
        log.info("Starting exchange rate update from Yahoo Finance");

        List<Currency> activeCurrencies = currencyRepository.findByIsActiveTrueOrderByCodeAsc();
        if (activeCurrencies.isEmpty()) {
            log.warn("No active currencies found, skipping update");
            return 0;
        }

        LocalDate today = LocalDate.now();
        List<ExchangeRate> newRates = new ArrayList<>();

        // Fetch USD-based rates for all currencies except USD
        List<String> symbols = buildYahooFinanceSymbols(activeCurrencies);
        log.debug("Fetching {} exchange rate symbols from Yahoo Finance", symbols.size());

        try {
            List<MarketQuote> quotes = marketDataProvider.getQuotes(symbols);
            log.info("Fetched {} exchange rate quotes from Yahoo Finance", quotes.size());

            // Process each quote and create ExchangeRate entities
            for (MarketQuote quote : quotes) {
                try {
                    ExchangeRate rate = parseQuoteToExchangeRate(quote, today);
                    if (rate != null) {
                        newRates.add(rate);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse exchange rate from quote: {}", quote.getSymbol(), e);
                }
            }

            // Also persist inverse rates so any A→B lookup also has B→A
            List<ExchangeRate> inverseRates =
                    newRates.stream()
                            .filter(
                                    r ->
                                            r.getRate() != null
                                                    && r.getRate().compareTo(BigDecimal.ZERO) > 0)
                            .map(
                                    r ->
                                            ExchangeRate.builder()
                                                    .baseCurrency(r.getTargetCurrency())
                                                    .targetCurrency(r.getBaseCurrency())
                                                    .rate(
                                                            BigDecimal.ONE.divide(
                                                                    r.getRate(),
                                                                    8,
                                                                    java.math.RoundingMode.HALF_UP))
                                                    .rateDate(r.getRateDate())
                                                    .build())
                            .collect(java.util.stream.Collectors.toList());
            newRates.addAll(inverseRates);

            // Save all rates to database
            if (!newRates.isEmpty()) {
                exchangeRateRepository.saveAll(newRates);
                log.info(
                        "Successfully updated {} exchange rates for {} (including {} inverse rates)",
                        newRates.size(),
                        today,
                        inverseRates.size());
            } else {
                log.warn("No exchange rates were parsed from {} quotes", quotes.size());
            }

            return newRates.size();

        } catch (MarketDataException e) {
            log.error("Failed to fetch exchange rates from Yahoo Finance: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Fetches and stores exchange rates for a specific historical date.
     *
     * <p>This method is useful for backfilling historical exchange rates. Note: Yahoo Finance may
     * not support historical exchange rates for all currency pairs.
     *
     * @param date the date to fetch rates for
     * @return the number of exchange rates successfully stored
     * @throws IllegalArgumentException if date is in the future
     */
    @Transactional
    public int updateExchangeRatesForDate(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Cannot fetch exchange rates for future date: " + date);
        }

        log.info("Updating exchange rates for historical date: {}", date);

        // For now, Yahoo Finance quote API doesn't support historical dates
        // This would require using the chart API with specific date ranges
        // TODO: Implement historical rate fetching using Yahoo Finance chart API

        log.warn("Historical exchange rate fetching not yet implemented");
        return 0;
    }

    /**
     * Clears all cached exchange rates.
     *
     * <p>Useful for testing or when manual rate updates are performed.
     */
    @CacheEvict(value = "exchangeRates", allEntries = true)
    public void clearCache() {
        log.info("Clearing exchange rate cache");
    }

    // ==================== Private Helper Methods ====================

    /**
     * Attempts to fetch and store the exchange rate for a specific currency pair on-demand.
     *
     * <p>Builds the appropriate Yahoo Finance symbol for the given pair and calls the market data
     * provider. If the pair involves USD, a single symbol is fetched. For cross pairs (e.g., XOF →
     * EUR), both legs against USD are fetched so the inverse-rate lookup in {@link
     * #getExchangeRate} can resolve the cross rate.
     *
     * @param fromCurrency the source currency code
     * @param toCurrency the target currency code
     * @return {@code true} if at least one rate was successfully stored; {@code false} otherwise
     */
    @Transactional
    @CacheEvict(value = "exchangeRates", allEntries = true)
    boolean fetchAndStorePairRate(String fromCurrency, String toCurrency) {
        List<String> symbols = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // Build symbols for each non-USD leg so we can resolve any cross pair
        addSymbolForCurrency(symbols, fromCurrency);
        addSymbolForCurrency(symbols, toCurrency);

        if (symbols.isEmpty()) {
            log.debug("Both currencies are USD, no fetch needed");
            return false;
        }

        // Use the chart endpoint (v8/finance/chart) via getHistoricalPrices — it
        // supports
        // currency pairs like XOFUSD=X that the quote endpoint (v7/finance/quote)
        // rejects
        // with 401 Unauthorized.
        log.debug("On-demand fetch: requesting symbols {} via chart endpoint", symbols);
        List<ExchangeRate> newRates = new ArrayList<>();
        LocalDate from = today.minusDays(5); // look back a few days in case today has no data yet

        for (String symbol : symbols) {
            try {
                List<org.openfinance.dto.HistoricalPrice> prices =
                        marketDataProvider.getHistoricalPrices(symbol, from, today);
                if (prices.isEmpty()) {
                    log.warn("On-demand chart fetch returned no data for symbol {}", symbol);
                    continue;
                }
                // Use the most recent close price
                org.openfinance.dto.HistoricalPrice latest = prices.get(prices.size() - 1);
                BigDecimal price = latest.getClose();
                if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("Invalid close price for symbol {}: {}", symbol, price);
                    continue;
                }
                // Reuse parseQuoteToExchangeRate by synthesising a MarketQuote
                MarketQuote syntheticQuote =
                        MarketQuote.builder().symbol(symbol).price(price).build();
                ExchangeRate rate = parseQuoteToExchangeRate(syntheticQuote, today);
                if (rate != null) {
                    newRates.add(rate);
                    log.debug("On-demand chart fetch: {} close={}", symbol, price);
                }
            } catch (Exception e) {
                log.warn("On-demand chart fetch failed for symbol {}: {}", symbol, e.getMessage());
            }
        }

        if (!newRates.isEmpty()) {
            try {
                // Filter out rates that already exist for (base, target, today) to avoid UNIQUE
                // constraint violations.
                // This can happen when two concurrent requests both trigger on-demand fetch for
                // the same pair.
                List<ExchangeRate> ratesToSave =
                        newRates.stream()
                                .filter(
                                        r ->
                                                exchangeRateRepository
                                                        .findByBaseCurrencyAndTargetCurrencyAndRateDate(
                                                                r.getBaseCurrency(),
                                                                r.getTargetCurrency(),
                                                                r.getRateDate())
                                                        .isEmpty())
                                .collect(java.util.stream.Collectors.toList());

                if (ratesToSave.isEmpty()) {
                    log.info(
                            "On-demand fetch: rates for pair {} → {} already exist for {}, skipping save",
                            fromCurrency,
                            toCurrency,
                            today);
                    return true; // rates are already present — lookup will succeed
                }

                exchangeRateRepository.saveAll(ratesToSave);
                log.info(
                        "On-demand fetch stored {} rate(s) for pair {} → {}",
                        ratesToSave.size(),
                        fromCurrency,
                        toCurrency);
                return true;
            } catch (Exception e) {
                log.warn(
                        "Failed to save on-demand rates for pair {} → {}: {}",
                        fromCurrency,
                        toCurrency,
                        e.getMessage());
                return false;
            }
        }

        log.warn(
                "On-demand fetch returned no parseable rates for pair {} → {}",
                fromCurrency,
                toCurrency);
        return false;
    }

    /**
     * Returns the latest rate for {@code currencyCode} → USD (i.e. 1 unit of the currency in USD).
     *
     * <p>Checks both the direct row ({@code currencyCode}→USD) and the inverse of USD→{@code
     * currencyCode}. Used to compute cross-rates when neither a direct nor inverse pair is stored
     * in the DB.
     *
     * @param currencyCode the non-USD currency
     * @return rate in USD, or {@code null} if not available
     */
    private BigDecimal getRateViaUsd(String currencyCode) {
        Optional<ExchangeRate> direct = findRate(currencyCode, "USD", null);
        if (direct.isPresent()) {
            return direct.get().getRate();
        }
        Optional<ExchangeRate> inverse = findRate("USD", currencyCode, null);
        if (inverse.isPresent()) {
            return inverse.get().getInverseRate();
        }
        return null;
    }

    /**
     * Adds a Yahoo Finance symbol to the list for the given currency (relative to USD). Does
     * nothing if the currency is USD itself.
     *
     * @param symbols the list to add the symbol to
     * @param currencyCode the currency code
     */
    private void addSymbolForCurrency(List<String> symbols, String currencyCode) {
        if ("USD".equalsIgnoreCase(currencyCode)) {
            return;
        }
        if (isCryptocurrency(currencyCode)) {
            symbols.add(currencyCode.toUpperCase() + "-USD");
        } else {
            symbols.add(currencyCode.toUpperCase() + "USD=X");
        }
    }

    /**
     * Validates that a currency code exists in the database.
     *
     * @param currencyCode the currency code to validate
     * @throws IllegalArgumentException if currency does not exist
     */
    private void validateCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency code cannot be null or empty");
        }

        if (!currencyRepository.existsByCode(currencyCode.toUpperCase())) {
            throw new IllegalArgumentException("Currency not found: " + currencyCode);
        }
    }

    /**
     * Finds an exchange rate in the database for a specific currency pair and date.
     *
     * <p>If date is null, returns the latest rate. If no exact date match, returns the most recent
     * rate on or before the specified date.
     *
     * @param baseCurrency the base currency code
     * @param targetCurrency the target currency code
     * @param date the date (null for latest)
     * @return Optional containing the exchange rate if found
     */
    private Optional<ExchangeRate> findRate(
            String baseCurrency, String targetCurrency, LocalDate date) {

        if (date == null) {
            // Get latest rate
            List<ExchangeRate> rates =
                    exchangeRateRepository.findLatestByBaseCurrencyAndTargetCurrency(
                            baseCurrency, targetCurrency);
            return rates.isEmpty() ? Optional.empty() : Optional.of(rates.get(0));
        } else {
            // Try exact date match first
            Optional<ExchangeRate> exactMatch =
                    exchangeRateRepository.findByBaseCurrencyAndTargetCurrencyAndRateDate(
                            baseCurrency, targetCurrency, date);

            if (exactMatch.isPresent()) {
                return exactMatch;
            }

            // Fall back to most recent rate on or before date
            List<ExchangeRate> historicalRates =
                    exchangeRateRepository
                            .findByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(
                                    baseCurrency, targetCurrency, date);
            return historicalRates.isEmpty()
                    ? Optional.empty()
                    : Optional.of(historicalRates.get(0));
        }
    }

    /**
     * Builds Yahoo Finance symbols for fetching exchange rates.
     *
     * <p><strong>Yahoo Finance Symbol Format:</strong>
     *
     * <ul>
     *   <li>Fiat pairs: EURUSD=X (EUR to USD)
     *   <li>Crypto pairs: BTC-USD (Bitcoin to USD)
     * </ul>
     *
     * @param currencies list of currencies to build symbols for
     * @return list of Yahoo Finance symbols
     */
    private List<String> buildYahooFinanceSymbols(List<Currency> currencies) {
        List<String> symbols = new ArrayList<>();

        for (Currency currency : currencies) {
            String code = currency.getCode();

            // Skip USD (base currency)
            if ("USD".equalsIgnoreCase(code)) {
                continue;
            }

            // Determine symbol format based on currency type
            if (isCryptocurrency(code)) {
                // Crypto format: BTC-USD, ETH-USD
                symbols.add(code + "-USD");
            } else {
                // Fiat format: EURUSD=X (for EUR → USD rate)
                // Yahoo Finance convention: base currency first, then quote currency
                symbols.add(code + "USD=X");
            }
        }

        return symbols;
    }

    /**
     * Determines if a currency code represents a cryptocurrency.
     *
     * @param currencyCode the currency code to check
     * @return true if cryptocurrency, false otherwise
     */
    private boolean isCryptocurrency(String currencyCode) {
        // Common cryptocurrency codes
        List<String> cryptoCodes =
                List.of(
                        "BTC", "ETH", "BNB", "XRP", "ADA", "SOL", "DOT", "DOGE", "USDT", "USDC",
                        "MATIC", "AVAX", "LINK", "UNI");
        return cryptoCodes.contains(currencyCode.toUpperCase());
    }

    /**
     * Parses a Yahoo Finance quote into an ExchangeRate entity.
     *
     * <p>Extracts base and target currencies from the symbol format: - EURUSD=X → base: EUR,
     * target: USD - BTC-USD → base: BTC, target: USD
     *
     * @param quote the market quote from Yahoo Finance
     * @param date the date to assign to the exchange rate
     * @return the ExchangeRate entity, or null if parsing fails
     */
    private ExchangeRate parseQuoteToExchangeRate(MarketQuote quote, LocalDate date) {
        String symbol = quote.getSymbol();
        BigDecimal price = quote.getPrice();

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Invalid price for symbol {}: {}", symbol, price);
            return null;
        }

        // Parse currency pair from symbol
        String baseCurrency;
        String targetCurrency = "USD"; // Yahoo Finance uses USD as quote currency

        if (symbol.contains("-")) {
            // Crypto format: BTC-USD
            baseCurrency = symbol.split("-")[0];
        } else if (symbol.endsWith("=X")) {
            // Fiat format: EURUSD=X
            String pair = symbol.replace("=X", "");
            if (pair.length() == 6) {
                baseCurrency = pair.substring(0, 3);
                targetCurrency = pair.substring(3, 6);
            } else {
                log.warn("Invalid currency pair format: {}", symbol);
                return null;
            }
        } else {
            log.warn("Unknown symbol format: {}", symbol);
            return null;
        }

        // Yahoo Finance returns the price as: 1 baseCurrency = X targetCurrency
        // For crypto (BTC-USD), price is in USD per BTC (e.g., $95,000)
        // For fiat (EURUSD=X), price is USD per EUR (e.g., 1.08)

        // We need to store: 1 USD = X baseCurrency (inverse for crypto)
        BigDecimal rate;
        String storedBase;
        String storedTarget;

        if (isCryptocurrency(baseCurrency)) {
            // Crypto: Store as USD → crypto (inverse)
            // Example: BTC-USD = 95000 → store USD → BTC = 0.00001053
            rate = BigDecimal.ONE.divide(price, 8, RoundingMode.HALF_UP);
            storedBase = "USD";
            storedTarget = baseCurrency;
        } else {
            // Fiat: Store as currency → USD (direct)
            // Example: EURUSD=X = 1.08 → store EUR → USD = 1.08
            rate = price;
            storedBase = baseCurrency;
            storedTarget = targetCurrency;
        }

        return ExchangeRate.builder()
                .baseCurrency(storedBase)
                .targetCurrency(storedTarget)
                .rate(rate)
                .rateDate(date)
                .source("yahoo-finance")
                .build();
    }
}
