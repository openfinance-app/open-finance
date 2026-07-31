package org.openfinance.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.CryptoListProperties;
import org.openfinance.dto.CryptoCurrencyInfo;
import org.openfinance.exception.MarketDataException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * {@link CryptoListProvider} backed by CoinGecko's keyless public API ({@code GET
 * /coins/markets?vs_currency=usd&order=market_cap_desc}). An optional API key is sent via the
 * configured header to raise rate limits.
 */
@Slf4j
@Component
public class CoinGeckoCryptoListProvider implements CryptoListProvider {

    /** Currency codes must be 3-10 uppercase letters (matches the DB/entity constraint). */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z]{3,10}$");

    private final CryptoListProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public CoinGeckoCryptoListProvider(CryptoListProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient =
                WebClient.builder().baseUrl(properties.getCoingecko().getBaseUrl()).build();
    }

    @Override
    public String name() {
        return "coingecko";
    }

    @Override
    public boolean isEnabled() {
        return properties.getCoingecko().isEnabled();
    }

    @Override
    @Retryable(
            retryFor = {MarketDataException.class, WebClientResponseException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public List<CryptoCurrencyInfo> fetchTopCryptocurrencies(int limit) {
        int perPage = Math.min(limit, 250); // CoinGecko caps per_page at 250
        String apiKey = properties.getCoingecko().getApiKey();
        try {
            String response =
                    webClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/coins/markets")
                                                    .queryParam("vs_currency", "usd")
                                                    .queryParam("order", "market_cap_desc")
                                                    .queryParam("per_page", perPage)
                                                    .queryParam("page", 1)
                                                    .build())
                            .headers(
                                    headers -> {
                                        if (apiKey != null && !apiKey.isBlank()) {
                                            headers.add(
                                                    properties.getCoingecko().getApiKeyHeader(),
                                                    apiKey);
                                        }
                                    })
                            .retrieve()
                            .onStatus(
                                    status -> status.isError(),
                                    clientResponse ->
                                            Mono.error(
                                                    new MarketDataException(
                                                            "CoinGecko request failed",
                                                            null,
                                                            clientResponse.statusCode().value())))
                            .bodyToMono(String.class)
                            .block();
            return parse(response);
        } catch (MarketDataException e) {
            throw e;
        } catch (Exception e) {
            throw new MarketDataException("Failed to fetch CoinGecko crypto list", e);
        }
    }

    @Recover
    List<CryptoCurrencyInfo> recover(MarketDataException e, int limit) {
        log.error("CoinGecko crypto list failed after retries: {}", e.getMessage());
        throw e;
    }

    /** Parses a CoinGecko {@code /coins/markets} JSON array. Package-private for unit testing. */
    List<CryptoCurrencyInfo> parse(String json) {
        List<CryptoCurrencyInfo> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return result;
            }
            for (JsonNode node : root) {
                String code = node.path("symbol").asText("").toUpperCase(Locale.ROOT);
                if (!CODE_PATTERN.matcher(code).matches()) {
                    log.debug("Skipping non-conforming CoinGecko symbol: {}", code);
                    continue;
                }
                result.add(
                        new CryptoCurrencyInfo(
                                code,
                                node.path("name").asText(code),
                                code,
                                node.path("market_cap_rank").isNumber()
                                        ? node.path("market_cap_rank").asInt()
                                        : null,
                                node.path("current_price").isNumber()
                                        ? node.path("current_price").decimalValue()
                                        : null));
            }
        } catch (Exception e) {
            throw new MarketDataException("Failed to parse CoinGecko response", e);
        }
        return result;
    }
}
