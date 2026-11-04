package org.openfinance.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.CryptoListProperties;
import org.openfinance.dto.CryptoCurrencyInfo;
import org.openfinance.exception.MarketDataException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@DisplayName("CoinGeckoCryptoListProvider Tests")
class CoinGeckoCryptoListProviderTest {

    private CoinGeckoCryptoListProvider provider;

    @Test
    @DisplayName("Fetch caps the requested page size and sends the configured API header")
    void requestCapsLargeListsAndKeepsUnknownPricesUnknown() {
        CryptoListProperties properties = new CryptoListProperties();
        properties.getCoingecko().setApiKey("TEST-KEY");
        properties.getCoingecko().setApiKeyHeader("x-cg-pro-api-key");
        provider = new CoinGeckoCryptoListProvider(properties, new ObjectMapper());
        WebClient client =
                WebClient.builder()
                        .baseUrl("https://market.test")
                        .exchangeFunction(
                                request -> {
                                    assertThat(request.url().getPath()).isEqualTo("/coins/markets");
                                    assertThat(request.url().getQuery())
                                            .isEqualTo(
                                                    "vs_currency=usd&order=market_cap_desc&per_page=250&page=1");
                                    assertThat(request.headers().getFirst("x-cg-pro-api-key"))
                                            .isEqualTo("TEST-KEY");
                                    return Mono.just(
                                            ClientResponse.create(HttpStatus.OK)
                                                    .body("[{\"symbol\":\"eth\"}]")
                                                    .build());
                                })
                        .build();
        ReflectionTestUtils.setField(provider, "webClient", client);
        List<CryptoCurrencyInfo> result = provider.fetchTopCryptocurrencies(1000);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().code()).isEqualTo("ETH");
        assertThat(result.getFirst().priceUsd()).isNull();
        assertThat(result.getFirst().marketCapRank()).isNull();
    }

    @Test
    @DisplayName(
            "Remote HTTP failure preserves status and malformed JSON is not treated as empty data")
    void failedRequestsRemainFailuresAfterRetryExhaustion() {
        WebClient client =
                WebClient.builder()
                        .baseUrl("https://market.test")
                        .exchangeFunction(
                                request ->
                                        Mono.just(
                                                ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                                                        .body("{}")
                                                        .build()))
                        .build();
        ReflectionTestUtils.setField(provider, "webClient", client);
        MarketDataException failure =
                assertThrows(
                        MarketDataException.class, () -> provider.fetchTopCryptocurrencies(10));
        assertThat(failure.getStatusCode()).isEqualTo(429);
        assertThat(assertThrows(MarketDataException.class, () -> provider.recover(failure, 10)))
                .isSameAs(failure);
        assertThat(provider.parse("{}")).isEmpty();
        assertThatThrownBy(() -> provider.parse("not-json"))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("parse");
    }

    @BeforeEach
    void setUp() {
        provider = new CoinGeckoCryptoListProvider(new CryptoListProperties(), new ObjectMapper());
    }

    @Test
    @DisplayName("name and enabled reflect configuration")
    void metadata() {
        assertThat(provider.name()).isEqualTo("coingecko");
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    @DisplayName(
            "parse maps /coins/markets JSON, upper-cases symbols, and skips non-conforming codes")
    void parse() {
        String json =
                """
                [
                  {"id":"bitcoin","symbol":"btc","name":"Bitcoin","current_price":95000.50,"market_cap_rank":1},
                  {"id":"ethereum","symbol":"eth","name":"Ethereum","current_price":3200,"market_cap_rank":2},
                  {"id":"1inch","symbol":"1inch","name":"1inch","current_price":0.42,"market_cap_rank":95}
                ]
                """;

        List<CryptoCurrencyInfo> result = provider.parse(json);

        assertThat(result).hasSize(2); // "1INCH" contains a digit -> skipped
        CryptoCurrencyInfo btc = result.get(0);
        assertThat(btc.code()).isEqualTo("BTC");
        assertThat(btc.name()).isEqualTo("Bitcoin");
        assertThat(btc.symbol()).isEqualTo("BTC");
        assertThat(btc.marketCapRank()).isEqualTo(1);
        assertThat(btc.priceUsd()).isEqualByComparingTo("95000.50");
        assertThat(result).extracting(CryptoCurrencyInfo::code).containsExactly("BTC", "ETH");
    }
}
