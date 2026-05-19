package org.openfinance.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.dto.MarketQuote;
import org.openfinance.dto.SymbolSearchResult;
import org.openfinance.exception.MarketDataException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Implementation of MarketDataProvider using Yahoo Finance API.
 *
 * <p>This provider fetches real-time and historical financial data from Yahoo Finance's public API.
 * It supports stocks, ETFs, cryptocurrencies, and currency pairs.
 *
 * <p><strong>API Endpoints:</strong>
 *
 * <ul>
 *   <li>Quote/Batch:
 *       https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=1d&interval=1d
 *   <li>Search: https://query1.finance.yahoo.com/v1/finance/search?q=apple
 *   <li>Historical:
 *       https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?period1=...&period2=...&interval=1d
 * </ul>
 *
 * <p><strong>Note:</strong> The v7/finance/quote endpoint is no longer used as it requires
 * authentication. All quote data is now sourced from v8/finance/chart which remains publicly
 * accessible and also supports currency pairs (e.g. XOFUSD=X).
 *
 * <p><strong>Rate Limiting:</strong> Yahoo Finance has no official rate limits for public API, but
 * excessive requests may result in temporary blocks. Use caching to minimize requests.
 *
 * <p><strong>Error Handling:</strong> Throws MarketDataException for invalid symbols, network
 * errors, or malformed responses. Uses Spring Retry with exponential backoff for transient
 * failures.
 *
 * <p><strong>Retry Strategy:</strong>
 *
 * <ul>
 *   <li>Max attempts: 3
 *   <li>Initial delay: 1 second
 *   <li>Multiplier: 2.0 (exponential backoff)
 *   <li>Max delay: 10 seconds
 * </ul>
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2024-01-20
 */
@Slf4j
@Component
public class YahooFinanceProvider implements MarketDataProvider {

    private static final String BASE_URL = "https://query1.finance.yahoo.com";
    private static final String SEARCH_ENDPOINT = "/v1/finance/search";
    private static final String CHART_ENDPOINT = "/v8/finance/chart";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a new YahooFinanceProvider with default configuration.
     *
     * @param objectMapper JSON object mapper for parsing responses
     */
    public YahooFinanceProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient =
                WebClient.builder()
                        .baseUrl(BASE_URL)
                        .defaultHeader(
                                "User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36")
                        .build();
        log.info("YahooFinanceProvider initialized with base URL: {}", BASE_URL);
    }

    @Override
    @Retryable(
            retryFor = {MarketDataException.class, WebClientResponseException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public MarketQuote getQuote(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }

        log.debug("Fetching quote for symbol: {}", symbol);

        try {
            String response =
                    webClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path(CHART_ENDPOINT + "/" + symbol)
                                                    .queryParam("range", "1d")
                                                    .queryParam("interval", "1d")
                                                    .build())
                            .retrieve()
                            .onStatus(
                                    status -> status.is4xxClientError(),
                                    clientResponse -> {
                                        log.warn(
                                                "Client error fetching quote for {}: {}",
                                                symbol,
                                                clientResponse.statusCode());
                                        return Mono.error(
                                                new MarketDataException(
                                                        "Symbol not found: " + symbol,
                                                        symbol,
                                                        clientResponse.statusCode().value()));
                                    })
                            .onStatus(
                                    status -> status.is5xxServerError(),
                                    clientResponse -> {
                                        log.error(
                                                "Server error fetching quote for {}: {}",
                                                symbol,
                                                clientResponse.statusCode());
                                        return Mono.error(
                                                new MarketDataException(
                                                        "Market data service unavailable",
                                                        symbol,
                                                        clientResponse.statusCode().value()));
                                    })
                            .bodyToMono(String.class)
                            .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode result = root.path("chart").path("result");

            if (result.isEmpty() || result.size() == 0) {
                log.warn("No data found for symbol: {}", symbol);
                throw new MarketDataException("No data available for symbol: " + symbol, symbol);
            }

            MarketQuote quote = parseChartMeta(result.get(0).path("meta"));

            log.info("Successfully fetched quote for {}: price={}", symbol, quote.getPrice());
            return quote;

        } catch (MarketDataException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching quote for symbol: {}", symbol, e);
            throw new MarketDataException("Failed to fetch quote for: " + symbol, symbol, null, e);
        }
    }

    /**
     * Recovery method when getQuote() fails after all retry attempts.
     *
     * <p>This method is called when the maximum retry attempts are exhausted. It logs the failure
     * and re-throws the exception so the caller can handle it.
     *
     * @param e the exception that caused the failure
     * @param symbol the symbol that was being fetched
     * @throws MarketDataException always thrown to propagate the failure
     */
    @Recover
    public MarketQuote recoverGetQuote(MarketDataException e, String symbol) {
        log.error(
                "Failed to fetch quote for {} after all retry attempts: {}",
                symbol,
                e.getMessage());
        throw e;
    }

    @Override
    public List<MarketQuote> getQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("Symbols list cannot be null or empty");
        }

        log.debug("Fetching quotes for {} symbols via chart endpoint", symbols.size());

        // v8/finance/chart is per-symbol only — fan out individually.
        // Failures for individual symbols are logged and skipped so one bad symbol
        // does not prevent others from being fetched.
        List<MarketQuote> quotes = new ArrayList<>();
        for (String symbol : symbols) {
            try {
                quotes.add(getQuote(symbol));
            } catch (Exception e) {
                log.warn(
                        "Failed to fetch quote for symbol {}, skipping: {}",
                        symbol,
                        e.getMessage());
            }
        }

        log.info(
                "Successfully fetched {} quotes out of {} requested",
                quotes.size(),
                symbols.size());
        return quotes;
    }

    @Override
    public List<HistoricalPrice> getHistoricalPrices(
            String symbol, LocalDate startDate, LocalDate endDate) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date cannot be null");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }

        long period1 = startDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
        long period2 = endDate.atStartOfDay(ZoneId.systemDefault()).plusDays(1).toEpochSecond();

        log.debug("Fetching historical prices for {} from {} to {}", symbol, startDate, endDate);

        try {
            String response =
                    webClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path(CHART_ENDPOINT + "/" + symbol)
                                                    .queryParam("period1", period1)
                                                    .queryParam("period2", period2)
                                                    .queryParam("interval", "1d")
                                                    .build())
                            .retrieve()
                            .onStatus(
                                    status -> status.isError(),
                                    clientResponse ->
                                            Mono.error(
                                                    new MarketDataException(
                                                            "Failed to fetch historical prices for: "
                                                                    + symbol,
                                                            symbol,
                                                            clientResponse.statusCode().value())))
                            .bodyToMono(String.class)
                            .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode chart = root.path("chart").path("result");

            if (chart.isEmpty() || chart.size() == 0) {
                log.warn("No historical data found for symbol: {}", symbol);
                return List.of();
            }

            JsonNode result = chart.get(0);
            JsonNode timestamps = result.path("timestamp");
            JsonNode indicators = result.path("indicators").path("quote").get(0);
            JsonNode adjClose = result.path("indicators").path("adjclose");

            List<HistoricalPrice> prices = new ArrayList<>();
            int size = timestamps.size();

            for (int i = 0; i < size; i++) {
                long timestamp = timestamps.get(i).asLong();
                LocalDate date =
                        Instant.ofEpochSecond(timestamp)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate();

                JsonNode opens = indicators.path("open");
                JsonNode highs = indicators.path("high");
                JsonNode lows = indicators.path("low");
                JsonNode closes = indicators.path("close");
                JsonNode volumes = indicators.path("volume");
                JsonNode adjCloses = adjClose.isEmpty() ? null : adjClose.get(0).path("adjclose");

                // Skip if essential data is missing
                if (opens.get(i).isNull() || closes.get(i).isNull()) {
                    continue;
                }

                HistoricalPrice price =
                        HistoricalPrice.builder()
                                .symbol(symbol)
                                .date(date)
                                .open(getBigDecimal(opens.get(i)))
                                .high(getBigDecimal(highs.get(i)))
                                .low(getBigDecimal(lows.get(i)))
                                .close(getBigDecimal(closes.get(i)))
                                .adjustedClose(
                                        adjCloses != null ? getBigDecimal(adjCloses.get(i)) : null)
                                .volume(volumes.get(i).isNull() ? null : volumes.get(i).asLong())
                                .build();

                prices.add(price);
            }

            log.info("Successfully fetched {} historical prices for {}", prices.size(), symbol);
            return prices;

        } catch (MarketDataException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching historical prices for symbol: {}", symbol, e);
            throw new MarketDataException(
                    "Failed to fetch historical prices for: " + symbol, symbol, null, e);
        }
    }

    @Override
    public List<SymbolSearchResult> searchSymbol(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Search query cannot be null or empty");
        }

        log.debug("Searching for symbols matching: {}", query);

        try {
            String response =
                    webClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path(SEARCH_ENDPOINT)
                                                    .queryParam("q", query)
                                                    .build())
                            .retrieve()
                            .onStatus(
                                    status -> status.isError(),
                                    clientResponse ->
                                            Mono.error(
                                                    new MarketDataException(
                                                            "Failed to search for: " + query,
                                                            null,
                                                            clientResponse.statusCode().value())))
                            .bodyToMono(String.class)
                            .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode quotes = root.path("quotes");

            if (quotes.isEmpty()) {
                log.debug("No search results found for query: {}", query);
                return List.of();
            }

            List<SymbolSearchResult> results = new ArrayList<>();
            for (JsonNode quoteNode : quotes) {
                try {
                    SymbolSearchResult result =
                            SymbolSearchResult.builder()
                                    .symbol(quoteNode.path("symbol").asText())
                                    .name(
                                            quoteNode
                                                    .path("longname")
                                                    .asText(quoteNode.path("shortname").asText("")))
                                    .type(quoteNode.path("quoteType").asText(""))
                                    .exchange(
                                            quoteNode
                                                    .path("exchDisp")
                                                    .asText(quoteNode.path("exchange").asText("")))
                                    .exchangeDisplay(quoteNode.path("exchDisp").asText(""))
                                    .sector(quoteNode.path("sector").asText(null))
                                    .industry(quoteNode.path("industry").asText(null))
                                    .build();
                    results.add(result);
                } catch (Exception e) {
                    log.warn("Failed to parse search result", e);
                }
            }

            log.info("Found {} symbols matching query: {}", results.size(), query);
            return results;

        } catch (MarketDataException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error searching for symbols with query: {}", query, e);
            throw new MarketDataException("Failed to search for: " + query, e);
        }
    }

    /**
     * Parses quote data from a v8/finance/chart result meta node into a MarketQuote object.
     *
     * @param meta the meta JSON node from the chart result
     * @return the parsed MarketQuote
     */
    private MarketQuote parseChartMeta(JsonNode meta) {
        return MarketQuote.builder()
                .symbol(meta.path("symbol").asText())
                .name(meta.path("longName").asText(meta.path("shortName").asText("")))
                .price(getBigDecimal(meta.path("regularMarketPrice")))
                .change(
                        safeChange(
                                getBigDecimal(meta.path("regularMarketPrice")),
                                getBigDecimal(meta.path("chartPreviousClose"))))
                .previousClose(getBigDecimal(meta.path("chartPreviousClose")))
                .dayHigh(getBigDecimal(meta.path("regularMarketDayHigh")))
                .dayLow(getBigDecimal(meta.path("regularMarketDayLow")))
                .volume(
                        meta.path("regularMarketVolume").isNull()
                                ? null
                                : meta.path("regularMarketVolume").asLong())
                .currency(meta.path("currency").asText("USD"))
                .exchange(
                        meta.path("fullExchangeName").asText(meta.path("exchangeName").asText("")))
                .timestamp(LocalDateTime.now())
                .marketState(meta.path("marketState").asText("UNKNOWN"))
                .quoteType(meta.path("instrumentType").asText(""))
                .build();
    }

    /**
     * Safely extracts a BigDecimal from a JSON node.
     *
     * @param node the JSON node
     * @return the BigDecimal value, or null if the node is null or not a number
     */
    private BigDecimal getBigDecimal(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return BigDecimal.valueOf(node.asDouble());
    }

    /** Returns price − previousClose, or null if price is unavailable. */
    private BigDecimal safeChange(BigDecimal price, BigDecimal previousClose) {
        if (price == null) return null;
        return price.subtract(previousClose != null ? previousClose : BigDecimal.ZERO);
    }
}
