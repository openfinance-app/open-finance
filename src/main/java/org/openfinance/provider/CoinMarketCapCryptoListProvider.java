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
 * {@link CryptoListProvider} backed by CoinMarketCap. Keyless by default via the {@code
 * /public-api} path prefix; when an API key is configured the prefix is dropped and the key is sent
 * in the {@code X-CMC_PRO_API_KEY} header.
 */
@Slf4j
@Component
public class CoinMarketCapCryptoListProvider implements CryptoListProvider {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z]{3,10}$");
    private static final String LISTINGS_PATH = "/v3/cryptocurrency/listings/latest";
    private static final String KEYLESS_PREFIX = "/public-api";
    private static final String API_KEY_HEADER = "X-CMC_PRO_API_KEY";

    private final CryptoListProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public CoinMarketCapCryptoListProvider(
            CryptoListProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient =
                WebClient.builder().baseUrl(properties.getCoinmarketcap().getBaseUrl()).build();
    }

    @Override
    public String name() {
        return "coinmarketcap";
    }

    @Override
    public boolean isEnabled() {
        return properties.getCoinmarketcap().isEnabled();
    }

    private boolean hasApiKey() {
        String key = properties.getCoinmarketcap().getApiKey();
        return key != null && !key.isBlank();
    }

    /** Returns the request path; keyless requests use the {@code /public-api} prefix. */
    String buildPath() {
        return hasApiKey() ? LISTINGS_PATH : KEYLESS_PREFIX + LISTINGS_PATH;
    }

    @Override
    @Retryable(
            retryFor = {MarketDataException.class, WebClientResponseException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public List<CryptoCurrencyInfo> fetchTopCryptocurrencies(int limit) {
        String path = buildPath();
        try {
            String response =
                    webClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path(path)
                                                    .queryParam("start", 1)
                                                    .queryParam("limit", limit)
                                                    .queryParam("convert", "USD")
                                                    .build())
                            .headers(
                                    headers -> {
                                        if (hasApiKey()) {
                                            headers.add(
                                                    API_KEY_HEADER,
                                                    properties.getCoinmarketcap().getApiKey());
                                        }
                                    })
                            .retrieve()
                            .onStatus(
                                    status -> status.isError(),
                                    clientResponse ->
                                            Mono.error(
                                                    new MarketDataException(
                                                            "CoinMarketCap request failed",
                                                            null,
                                                            clientResponse.statusCode().value())))
                            .bodyToMono(String.class)
                            .block();
            return parse(response);
        } catch (MarketDataException e) {
            throw e;
        } catch (Exception e) {
            throw new MarketDataException("Failed to fetch CoinMarketCap crypto list", e);
        }
    }

    @Recover
    List<CryptoCurrencyInfo> recover(MarketDataException e, int limit) {
        log.error("CoinMarketCap crypto list failed after retries: {}", e.getMessage());
        throw e;
    }

    /**
     * Parses a CoinMarketCap {@code listings/latest} JSON envelope. Package-private for testing.
     */
    List<CryptoCurrencyInfo> parse(String json) {
        List<CryptoCurrencyInfo> result = new ArrayList<>();
        try {
            JsonNode data = objectMapper.readTree(json).path("data");
            if (!data.isArray()) {
                return result;
            }
            for (JsonNode node : data) {
                String code = node.path("symbol").asText("").toUpperCase(Locale.ROOT);
                if (!CODE_PATTERN.matcher(code).matches()) {
                    log.debug("Skipping non-conforming CoinMarketCap symbol: {}", code);
                    continue;
                }
                JsonNode usd = node.path("quote").path("USD").path("price");
                result.add(
                        new CryptoCurrencyInfo(
                                code,
                                node.path("name").asText(code),
                                code,
                                node.path("cmc_rank").isNumber()
                                        ? node.path("cmc_rank").asInt()
                                        : null,
                                usd.isNumber() ? usd.decimalValue() : null));
            }
        } catch (Exception e) {
            throw new MarketDataException("Failed to parse CoinMarketCap response", e);
        }
        return result;
    }
}
