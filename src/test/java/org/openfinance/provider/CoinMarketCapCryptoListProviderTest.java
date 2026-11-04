package org.openfinance.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.config.CryptoListProperties;
import org.openfinance.dto.CryptoCurrencyInfo;
import org.openfinance.exception.MarketDataException;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@DisplayName("CoinMarketCapCryptoListProvider Tests")
class CoinMarketCapCryptoListProviderTest {

    private final List<ClientRequest> requests = new ArrayList<>();

    @ParameterizedTest
    @ValueSource(strings = {"", "TEST-KEY"})
    @DisplayName(
            "Requests use the configured authentication mode and preserve optional quote fields")
    void fetchUsesTheCorrectEndpointAndDoesNotInventMissingPrices(String apiKey) {
        CoinMarketCapCryptoListProvider provider = newProvider(apiKey);
        respond(
                provider,
                200,
                """
                {"data":[
                  {"symbol":"btc","name":"Bitcoin","cmc_rank":1,"quote":{"USD":{"price":95000.50}}},
                  {"symbol":"ETH"},
                  {"symbol":"1INCH"},
                  {"symbol":"TOOLONGSYMBOL"}
                ]}
                """);
        List<CryptoCurrencyInfo> result = provider.fetchTopCryptocurrencies(25);
        assertThat(result).extracting(CryptoCurrencyInfo::code).containsExactly("BTC", "ETH");
        assertThat(result.getFirst().priceUsd()).isEqualByComparingTo("95000.50");
        assertThat(result.get(1).name()).isEqualTo("ETH");
        assertThat(result.get(1).priceUsd()).isNull();
        assertThat(result.get(1).marketCapRank()).isNull();
        ClientRequest request = requests.getFirst();
        assertThat(request.url().getPath())
                .isEqualTo(
                        apiKey.isEmpty()
                                ? "/public-api/v3/cryptocurrency/listings/latest"
                                : "/v3/cryptocurrency/listings/latest");
        assertThat(request.url().getQuery()).isEqualTo("start=1&limit=25&convert=USD");
        assertThat(request.headers().getFirst("X-CMC_PRO_API_KEY"))
                .isEqualTo(apiKey.isEmpty() ? null : apiKey);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 503})
    @DisplayName(
            "HTTP failures retain their status and are never reported as an empty successful list")
    void fetchPreservesRemoteFailureStatus(int status) {
        CoinMarketCapCryptoListProvider provider = newProvider("");
        respond(provider, status, "{}");
        MarketDataException failure =
                assertThrows(
                        MarketDataException.class, () -> provider.fetchTopCryptocurrencies(10));
        assertThat(failure.getStatusCode()).isEqualTo(status);
        assertThat(assertThrows(MarketDataException.class, () -> provider.recover(failure, 10)))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("Malformed responses and network failures remain distinguishable from empty data")
    void handlesMalformedAndUnavailableResponses() {
        CoinMarketCapCryptoListProvider provider = newProvider("");
        assertThat(provider.parse("{}")).isEmpty();
        assertThatThrownBy(() -> provider.parse("not-json"))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("parse");
        WebClient client =
                WebClient.builder()
                        .baseUrl("https://market.test")
                        .exchangeFunction(
                                request -> Mono.error(new IOException("Connection reset")))
                        .build();
        ReflectionTestUtils.setField(provider, "webClient", client);
        assertThatThrownBy(() -> provider.fetchTopCryptocurrencies(10))
                .isInstanceOf(MarketDataException.class)
                .hasRootCauseMessage("Connection reset");
    }

    private void respond(CoinMarketCapCryptoListProvider provider, int status, String json) {
        WebClient client =
                WebClient.builder()
                        .baseUrl("https://market.test")
                        .exchangeFunction(
                                request -> {
                                    requests.add(request);
                                    return Mono.just(
                                            ClientResponse.create(HttpStatusCode.valueOf(status))
                                                    .header("Content-Type", "application/json")
                                                    .body(json)
                                                    .build());
                                })
                        .build();
        ReflectionTestUtils.setField(provider, "webClient", client);
    }

    private CoinMarketCapCryptoListProvider newProvider(String apiKey) {
        CryptoListProperties props = new CryptoListProperties();
        props.getCoinmarketcap().setApiKey(apiKey);
        return new CoinMarketCapCryptoListProvider(props, new ObjectMapper());
    }

    @Test
    @DisplayName("keyless path uses the /public-api prefix; keyed path drops it")
    void buildPath() {
        assertThat(newProvider("").buildPath())
                .isEqualTo("/public-api/v3/cryptocurrency/listings/latest");
        assertThat(newProvider("KEY-123").buildPath())
                .isEqualTo("/v3/cryptocurrency/listings/latest");
    }

    @Test
    @DisplayName("parse maps listings/latest JSON with USD price and cmc_rank")
    void parse() {
        String json =
                """
                {
                  "status": {"error_code": 0},
                  "data": [
                    {"symbol":"BTC","name":"Bitcoin","cmc_rank":1,"quote":{"USD":{"price":95000.50}}},
                    {"symbol":"ETH","name":"Ethereum","cmc_rank":2,"quote":{"USD":{"price":3200}}}
                  ]
                }
                """;

        List<CryptoCurrencyInfo> result = newProvider("").parse(json);

        assertThat(result).extracting(CryptoCurrencyInfo::code).containsExactly("BTC", "ETH");
        assertThat(result.get(0).priceUsd()).isEqualByComparingTo("95000.50");
        assertThat(result.get(0).marketCapRank()).isEqualTo(1);
        assertThat(result.get(0).symbol()).isEqualTo("BTC");
    }
}
