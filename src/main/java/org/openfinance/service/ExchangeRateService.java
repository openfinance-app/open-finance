package org.openfinance.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.ExchangeRateLeg;
import org.openfinance.dto.MarketQuote;
import org.openfinance.entity.Currency;
import org.openfinance.entity.CurrencyType;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.exception.MarketDataException;
import org.openfinance.provider.MarketDataProvider;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.ExchangeRateRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final CurrencyTypeResolver currencyTypeResolver;

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
        return getExchangeRateQuote(fromCurrency, toCurrency, date).rate();
    }

    /**
     * Resolves the rate with the actual quote dates and sources, including both cross-rate legs.
     */
    public ExchangeRateQuote getExchangeRateQuote(
            String fromCurrency, String toCurrency, LocalDate date) {
        log.debug("Getting exchange rate: {} → {} for date: {}", fromCurrency, toCurrency, date);

        // Validate currencies exist
        validateCurrency(fromCurrency);
        validateCurrency(toCurrency);

        // Same currency conversion
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            log.debug("Same currency conversion, returning 1.0");
            return new ExchangeRateQuote(BigDecimal.ONE, List.of());
        }

        Optional<ExchangeRateQuote> storedRate =
                findExchangeRate(fromCurrency, toCurrency, date, List.of());
        if (storedRate.isPresent()) {
            return storedRate.get();
        }

        List<ExchangeRate> fetched =
                date != null && date.isBefore(LocalDate.now())
                        ? fetchPairRatesForDate(fromCurrency, toCurrency, date)
                        : fetchPairRates(fromCurrency, toCurrency);
        if (!fetched.isEmpty()) {
            persistFetchedRates(fetched);
            Optional<ExchangeRateQuote> fetchedRate =
                    findExchangeRate(fromCurrency, toCurrency, date, fetched);
            if (fetchedRate.isPresent()) return fetchedRate.get();
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
     * <p>Calculated money retains 18 decimal places, independently of display preferences.
     * Intermediate rates use DECIMAL128 precision so small cross-rates remain meaningful.
     *
     * @param amount the amount to convert
     * @param fromCurrency the source currency code (e.g., "USD")
     * @param toCurrency the target currency code (e.g., "EUR")
     * @param date the date for the exchange rate (null for latest)
     * @return the converted amount rounded to 18 decimal places
     * @throws IllegalArgumentException if amount is negative or currencies are invalid
     */
    public BigDecimal convert(
            BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }

        log.debug("Converting {} {} to {} on {}", amount, fromCurrency, toCurrency, date);

        BigDecimal rate = getExchangeRate(fromCurrency, toCurrency, date);
        BigDecimal converted = amount.multiply(rate).setScale(18, RoundingMode.HALF_UP);

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
     *   <li>Build Yahoo Finance symbols for fiat currencies (e.g., EURUSD=X); crypto prices are
     *       seeded separately
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
                                                    .rate(r.getInverseRate())
                                                    .rateDate(r.getRateDate())
                                                    .source(r.getSource())
                                                    .build())
                            .collect(java.util.stream.Collectors.toList());
            newRates.addAll(inverseRates);

            // Save all rates to database
            if (!newRates.isEmpty()) {
                exchangeRateRepository.upsertAll(newRates);
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

        List<Currency> activeCurrencies = currencyRepository.findByIsActiveTrueOrderByCodeAsc();
        if (activeCurrencies.isEmpty()) {
            log.warn("No active currencies found, skipping historical update for {}", date);
            return 0;
        }

        LocalDate from = date.minusDays(7);
        List<String> symbols = buildYahooFinanceSymbols(activeCurrencies);
        return fetchAndStoreHistoricalRates(symbols, from, date);
    }

    private List<ExchangeRate> fetchPairRatesForDate(
            String fromCurrency, String toCurrency, LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Cannot fetch exchange rates for future date: " + date);
        }

        LocalDate from = date.minusDays(7);
        List<String> symbols = new ArrayList<>();
        addSymbolForCurrency(symbols, fromCurrency);
        addSymbolForCurrency(symbols, toCurrency);
        if (symbols.isEmpty()) {
            log.debug("Both currencies are USD, no historical fetch needed");
            return List.of();
        }

        log.debug("Historical on-demand fetch: requesting symbols {}", symbols);
        return fetchHistoricalRates(symbols, from, date);
    }

    private int fetchAndStoreHistoricalRates(List<String> symbols, LocalDate from, LocalDate date) {
        List<ExchangeRate> rates = filterExistingRates(fetchHistoricalRates(symbols, from, date));
        if (!rates.isEmpty()) exchangeRateRepository.upsertAll(rates);
        return rates.size();
    }

    private List<ExchangeRate> fetchHistoricalRates(
            List<String> symbols, LocalDate from, LocalDate date) {
        List<ExchangeRate> newRates = new ArrayList<>();

        for (String symbol : symbols) {
            try {
                List<org.openfinance.dto.HistoricalPrice> prices =
                        marketDataProvider.getHistoricalPrices(symbol, from, date);
                Optional<org.openfinance.dto.HistoricalPrice> closestPrice =
                        prices.stream()
                                .filter(
                                        price ->
                                                price.getDate() != null
                                                        && !price.getDate().isAfter(date))
                                .filter(
                                        price ->
                                                price.getClose() != null
                                                        && price.getClose()
                                                                        .compareTo(BigDecimal.ZERO)
                                                                > 0)
                                .max(
                                        Comparator.comparing(
                                                org.openfinance.dto.HistoricalPrice::getDate));
                if (closestPrice.isEmpty()) {
                    log.warn(
                            "No historical close found for symbol {} on or before {}",
                            symbol,
                            date);
                    continue;
                }

                org.openfinance.dto.HistoricalPrice price = closestPrice.get();
                MarketQuote syntheticQuote =
                        MarketQuote.builder().symbol(symbol).price(price.getClose()).build();
                ExchangeRate rate = parseQuoteToExchangeRate(syntheticQuote, price.getDate());
                if (rate != null) {
                    newRates.add(rate);
                }
            } catch (Exception ex) {
                log.warn(
                        "Historical chart fetch failed for symbol {} on {}: {}",
                        symbol,
                        date,
                        ex.getMessage());
            }
        }

        List<ExchangeRate> inverseRates = buildInverseRates(newRates);
        newRates.addAll(inverseRates);

        return newRates;
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
     * Fetches exchange-rate quotes for a specific currency pair on demand.
     *
     * <p>Builds the appropriate Yahoo Finance symbol for the given pair and calls the market data
     * provider. If the pair involves USD, a single symbol is fetched. For cross pairs (e.g., XOF →
     * EUR), both legs against USD are fetched so the inverse-rate lookup in {@link
     * #getExchangeRate} can resolve the cross rate.
     *
     * @param fromCurrency the source currency code
     * @param toCurrency the target currency code
     * @return fetched quotes, which can be used without a write inside the caller's snapshot
     */
    private List<ExchangeRate> fetchPairRates(String fromCurrency, String toCurrency) {
        List<String> symbols = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // Build symbols for each non-USD leg so we can resolve any cross pair
        addSymbolForCurrency(symbols, fromCurrency);
        addSymbolForCurrency(symbols, toCurrency);

        if (symbols.isEmpty()) {
            log.debug("Both currencies are USD, no fetch needed");
            return List.of();
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

        return newRates;
    }

    /**
     * Quote caching must not upgrade a financial read or poison History's repeatable-read
     * transaction. Use fetched quotes immediately and store them after a successful commit.
     */
    private void persistFetchedRates(List<ExchangeRate> rates) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            List<ExchangeRate> pending = List.copyOf(rates);
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                exchangeRateRepository.upsertAllIndependently(pending);
                            } catch (RuntimeException failure) {
                                log.warn(
                                        "Unable to cache fetched exchange rates after commit",
                                        failure);
                            }
                        }
                    });
        } else {
            exchangeRateRepository.upsertAll(rates);
        }
    }

    /** Resolves direct, inverse or USD cross quotes while retaining all input provenance. */
    private Optional<ExchangeRateQuote> findExchangeRate(
            String fromCurrency, String toCurrency, LocalDate date, List<ExchangeRate> fetched) {
        Optional<ExchangeRate> direct = findRate(fromCurrency, toCurrency, date, fetched);
        if (direct.isPresent()) return Optional.of(quote(direct.get(), false));
        Optional<ExchangeRate> inverse = findRate(toCurrency, fromCurrency, date, fetched);
        if (inverse.isPresent()) return Optional.of(quote(inverse.get(), true));
        if (!"USD".equalsIgnoreCase(fromCurrency) && !"USD".equalsIgnoreCase(toCurrency)) {
            Optional<ExchangeRateQuote> fromUsd = rateViaUsd(fromCurrency, date, fetched);
            Optional<ExchangeRateQuote> toUsd = rateViaUsd(toCurrency, date, fetched);
            if (fromUsd.isPresent() && toUsd.isPresent()) {
                List<ExchangeRateLeg> legs = new ArrayList<>(fromUsd.get().legs());
                legs.addAll(toUsd.get().legs());
                return Optional.of(
                        new ExchangeRateQuote(
                                fromUsd.get()
                                        .rate()
                                        .divide(toUsd.get().rate(), MathContext.DECIMAL128),
                                legs));
            }
        }
        return Optional.empty();
    }

    private Optional<ExchangeRateQuote> rateViaUsd(
            String currency, LocalDate date, List<ExchangeRate> fetched) {
        return findRate(currency, "USD", date, fetched)
                .map(rate -> quote(rate, false))
                .or(() -> findRate("USD", currency, date, fetched).map(rate -> quote(rate, true)));
    }

    private ExchangeRateQuote quote(ExchangeRate rate, boolean inverse) {
        return new ExchangeRateQuote(
                inverse ? rate.getInverseRate() : rate.getRate(),
                List.of(
                        new ExchangeRateLeg(
                                rate.getBaseCurrency(),
                                rate.getTargetCurrency(),
                                rate.getRate(),
                                rate.getRateDate(),
                                rate.getSource())));
    }

    private Optional<ExchangeRate> findRate(
            String base, String target, LocalDate date, List<ExchangeRate> fetched) {
        return fetched.stream()
                .filter(
                        rate ->
                                base.equals(rate.getBaseCurrency())
                                        && target.equals(rate.getTargetCurrency()))
                .filter(rate -> date == null || !rate.getRateDate().isAfter(date))
                .max(Comparator.comparing(ExchangeRate::getRateDate))
                .or(() -> findRate(base, target, date));
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
     * Builds Yahoo Finance symbols for the bulk exchange-rate update.
     *
     * <p>Only fiat currencies are included (format {@code EURUSD=X}). USD is skipped because it is
     * the quote currency, and cryptocurrencies are skipped entirely — their prices come from the
     * crypto-list provider rather than Yahoo Finance.
     *
     * @param currencies list of currencies to build symbols for
     * @return list of fiat Yahoo Finance symbols
     */
    private List<String> buildYahooFinanceSymbols(List<Currency> currencies) {
        List<String> symbols = new ArrayList<>();
        for (Currency currency : currencies) {
            String code = currency.getCode();
            if ("USD".equalsIgnoreCase(code)) {
                continue; // USD is the quote currency
            }
            // Use the entity's authoritative type directly (the resolver is used where only a
            // code string is available).
            if (currency.getType() == CurrencyType.CRYPTO) {
                continue; // crypto prices come from the crypto-list provider, not Yahoo
            }
            symbols.add(code + "USD=X"); // fiat: EURUSD=X
        }
        return symbols;
    }

    private List<ExchangeRate> buildInverseRates(List<ExchangeRate> rates) {
        return rates.stream()
                .filter(
                        rate ->
                                rate.getRate() != null
                                        && rate.getRate().compareTo(BigDecimal.ZERO) > 0)
                .map(
                        rate ->
                                ExchangeRate.builder()
                                        .baseCurrency(rate.getTargetCurrency())
                                        .targetCurrency(rate.getBaseCurrency())
                                        .rate(rate.getInverseRate())
                                        .rateDate(rate.getRateDate())
                                        .source(rate.getSource())
                                        .build())
                .collect(Collectors.toList());
    }

    private List<ExchangeRate> filterExistingRates(List<ExchangeRate> rates) {
        Map<String, ExchangeRate> deduplicatedRates =
                rates.stream()
                        .collect(
                                Collectors.toMap(
                                        rate ->
                                                rate.getBaseCurrency()
                                                        + "-"
                                                        + rate.getTargetCurrency()
                                                        + "-"
                                                        + rate.getRateDate(),
                                        rate -> rate,
                                        (first, ignored) -> first));
        return deduplicatedRates.values().stream()
                .filter(
                        rate ->
                                exchangeRateRepository
                                        .findByBaseCurrencyAndTargetCurrencyAndRateDate(
                                                rate.getBaseCurrency(),
                                                rate.getTargetCurrency(),
                                                rate.getRateDate())
                                        .isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Determines if a currency code represents a cryptocurrency.
     *
     * @param currencyCode the currency code to check
     * @return true if cryptocurrency, false otherwise
     */
    private boolean isCryptocurrency(String currencyCode) {
        return currencyTypeResolver.isCrypto(currencyCode);
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
            rate = BigDecimal.ONE.divide(price, MathContext.DECIMAL128);
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
