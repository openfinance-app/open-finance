package org.openfinance.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.dto.MarketQuote;
import org.openfinance.dto.SymbolSearchResult;
import org.openfinance.exception.MarketDataException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@DisplayName("Yahoo market data response contracts without network access")
class YahooFinanceResponseTest {
    private YahooFinanceProvider provider;
    private final List<URI> requests = new ArrayList<>();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 19);

    @BeforeEach
    void setUp() {
        provider = new YahooFinanceProvider(new ObjectMapper());
    }

    @Test
    void quoteMapsCurrencyAndComputesAnExactPriceChange() {
        respond(
                200,
                """
            {"chart":{"result":[{"meta":{"symbol":"ACME.PA","longName":"Acme SA",
            "regularMarketPrice":123.45,"chartPreviousClose":121.11,"currency":"EUR",
            "regularMarketDayHigh":125.02,"regularMarketDayLow":120.01,
            "regularMarketVolume":789,"fullExchangeName":"Paris","marketState":"CLOSED",
            "instrumentType":"EQUITY"}}]}}
            """);
        MarketQuote quote = provider.getQuote("ACME.PA");
        assertThat(quote.getSymbol()).isEqualTo("ACME.PA");
        assertThat(quote.getName()).isEqualTo("Acme SA");
        assertThat(quote.getCurrency()).isEqualTo("EUR");
        assertThat(quote.getPrice()).isEqualByComparingTo("123.45");
        assertThat(quote.getChange()).isEqualByComparingTo("2.34");
        assertThat(quote.getPreviousClose()).isEqualByComparingTo("121.11");
        assertThat(quote.getDayHigh()).isEqualByComparingTo("125.02");
        assertThat(quote.getDayLow()).isEqualByComparingTo("120.01");
        assertThat(quote.getVolume()).isEqualTo(789);
        assertThat(quote.getExchange()).isEqualTo("Paris");
        assertThat(quote.getMarketState()).isEqualTo("CLOSED");
        assertThat(quote.getQuoteType()).isEqualTo("EQUITY");
        assertThat(requests.getFirst().getPath()).endsWith("/ACME.PA");
    }

    @Test
    void omittedVolumeIsUnknownRatherThanZeroAndInvalidPriceIsNotInvented() {
        respond(
                200,
                """
            {"chart":{"result":[{"meta":{"symbol":"ACME","shortName":"Acme",
            "exchangeName":"NYSE","regularMarketPrice":"unavailable"}}]}}
            """);
        MarketQuote quote = provider.getQuote("ACME");
        assertThat(quote.getName()).isEqualTo("Acme");
        assertThat(quote.getExchange()).isEqualTo("NYSE");
        assertThat(quote.getPrice()).isNull();
        assertThat(quote.getChange()).isNull();
        assertThat(quote.getVolume()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 429, 503})
    void preservesHttpStatusAndSymbolForQuoteFailures(int status) {
        respond(status, "{}");
        MarketDataException error =
                assertThrows(MarketDataException.class, () -> provider.getQuote("MISSING"));
        assertThat(error.getStatusCode()).isEqualTo(status);
        assertThat(error.getSymbol()).isEqualTo("MISSING");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"chart\":{\"result\":[]}}", "{\"chart\":{\"result\":null}}"})
    void emptyQuotesRaiseADomainFailureAndEmptyHistoryIsAllowed(String body) {
        respond(200, body);
        assertThatThrownBy(() -> provider.getQuote("ACME")).isInstanceOf(MarketDataException.class);
        assertThat(provider.getHistoricalPrices("ACME", DAY, DAY)).isEmpty();
    }

    @Test
    void malformedResponsesRetainTheirCause() {
        respond(200, "not json");
        assertThatThrownBy(() -> provider.getQuote("ACME"))
                .isInstanceOf(MarketDataException.class)
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThatThrownBy(() -> provider.getHistoricalPrices("ACME", DAY, DAY))
                .isInstanceOf(MarketDataException.class)
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThatThrownBy(() -> provider.searchSymbol("ACME"))
                .isInstanceOf(MarketDataException.class)
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }

    @Test
    void batchRetainsSuccessfulQuotesWhenOneSymbolIsUnavailable() {
        WebClient client =
                WebClient.builder()
                        .exchangeFunction(
                                request -> {
                                    String symbol =
                                            request.url().getPath().endsWith("/BAD")
                                                    ? null
                                                    : "GOOD";
                                    return Mono.just(
                                            ClientResponse.create(
                                                            symbol == null
                                                                    ? HttpStatus.NOT_FOUND
                                                                    : HttpStatus.OK)
                                                    .header("Content-Type", "application/json")
                                                    .body(
                                                            symbol == null
                                                                    ? "{}"
                                                                    : "{\"chart\":{\"result\":[{\"meta\":{\"symbol\":\"GOOD\",\"regularMarketPrice\":12}}]}}")
                                                    .build());
                                })
                        .build();
        ReflectionTestUtils.setField(provider, "webClient", client);
        assertThat(provider.getQuotes(List.of("BAD", "GOOD")))
                .extracting(MarketQuote::getSymbol)
                .containsExactly("GOOD");
    }

    @Test
    void historySkipsMissingEssentialPricesAndRetainsOptionalAdjustedPrices() {
        respond(
                200,
                """
            {"chart":{"result":[{"meta":{"currency":"GBP"},
            "timestamp":[1792368000,1792454400,1792540800],
            "indicators":{"quote":[{"open":[10,null,12],"close":[11,12,13],
            "high":[12,null,14],"low":[9,null,11],"volume":[100,null,300]}],
            "adjclose":[{"adjclose":[10.5,null,12.5]}]}}]}}
            """);
        List<HistoricalPrice> prices = provider.getHistoricalPrices("ACME", DAY, DAY.plusDays(2));
        assertThat(prices).hasSize(2);
        assertThat(prices).extracting(HistoricalPrice::getCurrency).containsOnly("GBP");
        assertThat(prices.getFirst().getAdjustedClose()).isEqualByComparingTo("10.5");
        assertThat(prices.getLast().getVolume()).isEqualTo(300);
        assertThat(requests.getFirst().getQuery())
                .contains(
                        "period1=" + DAY.atStartOfDay(ZoneId.systemDefault()).toEpochSecond(),
                        "period2="
                                + DAY.plusDays(3)
                                        .atStartOfDay(ZoneId.systemDefault())
                                        .toEpochSecond());
    }

    @Test
    void shortPriceArraysAndMissingOptionalVolumeDoNotDiscardValidHistory() {
        respond(
                200,
                """
            {"chart":{"result":[{"timestamp":[1792368000,1792454400],
            "indicators":{"quote":[{"open":[10],"close":[11]}]}}]}}
            """);
        List<HistoricalPrice> prices = provider.getHistoricalPrices("ACME", DAY, DAY.plusDays(1));
        assertThat(prices).hasSize(1);
        assertThat(prices.getFirst().getClose()).isEqualByComparingTo("11");
        assertThat(prices.getFirst().getVolume()).isNull();
        assertThat(prices.getFirst().getHigh()).isNull();
        assertThat(prices.getFirst().getAdjustedClose()).isNull();
    }

    @Test
    void searchPreservesNamesAndExchangeFallbacks() {
        respond(
                200,
                """
            {"quotes":[{"symbol":"ACME.PA","longname":"Acme France","shortname":"Acme",
            "exchDisp":"Paris","quoteType":"EQUITY","sector":"Technology","industry":"Software"},
            {"symbol":"ACME","shortname":"Acme US","exchange":"NYSE"}]}
            """);
        List<SymbolSearchResult> results = provider.searchSymbol("Acme & Co");
        assertThat(results)
                .extracting(SymbolSearchResult::getName)
                .containsExactly("Acme France", "Acme US");
        assertThat(results)
                .extracting(SymbolSearchResult::getExchange)
                .containsExactly("Paris", "NYSE");
        assertThat(results.getFirst().getSector()).isEqualTo("Technology");
        assertThat(results.getLast().getIndustry()).isNull();
        assertThat(requests.getFirst().getRawQuery()).contains("q=Acme%20%26%20Co");
    }

    @Test
    void noSearchMatchesAndServiceFailuresHaveDifferentOutcomes() {
        respond(200, "{\"quotes\":[]}");
        assertThat(provider.searchSymbol("missing")).isEmpty();
        respond(503, "{}");
        assertThat(
                        assertThrows(
                                        MarketDataException.class,
                                        () -> provider.searchSymbol("missing"))
                                .getStatusCode())
                .isEqualTo(503);
        assertThat(
                        assertThrows(
                                        MarketDataException.class,
                                        () -> provider.getHistoricalPrices("ACME", DAY, DAY))
                                .getStatusCode())
                .isEqualTo(503);
    }

    private void respond(int status, String body) {
        WebClient client =
                WebClient.builder()
                        .exchangeFunction(
                                request -> {
                                    requests.add(request.url());
                                    return Mono.just(
                                            ClientResponse.create(HttpStatus.valueOf(status))
                                                    .header("Content-Type", "application/json")
                                                    .body(body)
                                                    .build());
                                })
                        .build();
        ReflectionTestUtils.setField(provider, "webClient", client);
    }
}
