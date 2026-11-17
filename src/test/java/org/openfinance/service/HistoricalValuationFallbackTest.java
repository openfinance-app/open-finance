package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetPriceHistory;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Transaction;
import org.openfinance.repository.AssetPriceHistoryRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Historical valuations use only dated, relevant, valid prices")
class HistoricalValuationFallbackTest {
    private static final LocalDate DAY = LocalDate.of(2024, 6, 30);
    @Mock private AssetPriceHistoryRepository prices;
    @Mock private AssetPriceHistoryWriter writer;
    @Mock private MarketDataService market;
    @Mock private ExchangeRateService exchange;
    @InjectMocks private HistoricalAssetValuationServiceImpl service;

    @BeforeEach
    void conversion() {
        org.mockito.Mockito.lenient()
                .when(exchange.convert(any(), any(), any(), any()))
                .thenAnswer(call -> call.getArgument(0));
    }

    @ParameterizedTest(name = "Ignore execution price: {0}")
    @ValueSource(
            strings = {
                "other asset",
                "deleted",
                "no quantity",
                "zero quantity",
                "future transaction",
                "future quote",
                "negative quote",
                "foreign cash without investment value"
            })
    void unsuitableExecutionsCannotOverwriteAcquisitionValue(String problem) {
        Transaction tx = trade();
        switch (problem) {
            case "other asset" -> tx.setAssetId(99L);
            case "deleted" -> tx.setIsDeleted(true);
            case "no quantity" -> tx.setInvestmentQuantity(null);
            case "zero quantity" -> tx.setInvestmentQuantity(BigDecimal.ZERO);
            case "future transaction" -> tx.setDate(DAY.plusDays(1));
            case "future quote" -> tx.setInvestmentPriceDate(DAY.plusDays(1));
            case "negative quote" -> tx.setInvestmentPrice(new BigDecimal("-99"));
            case "foreign cash without investment value" -> {
                tx.setInvestmentPrice(null);
                tx.setCurrency("USD");
            }
            default -> throw new AssertionError(problem);
        }
        assertThat(service.valueAt(asset(), List.of(tx), DAY, "EUR")).isEqualByComparingTo("20");
        verifyNoInteractions(market, writer);
    }

    @ParameterizedTest
    @CsvSource({
        "explicit, 14",
        "investment value, 9",
        "same currency cash, 7",
        "zero price, 0",
        "sale execution, 9"
    })
    void executionPricesUseTheCorrectUnitsAndCurrency(String source, String expected) {
        Transaction tx = trade();
        tx.setInvestmentQuantity(new BigDecimal("4"));
        tx.setAmount(new BigDecimal("14"));
        if (!source.equals("explicit")) tx.setInvestmentPrice(null);
        if (source.equals("investment value") || source.equals("sale execution")) {
            tx.setInvestmentValue(new BigDecimal("18"));
            tx.setCurrency("USD");
        }
        if (source.equals("sale execution")) tx.setInvestmentQuantity(new BigDecimal("-4"));
        if (source.equals("zero price")) tx.setInvestmentPrice(BigDecimal.ZERO);
        assertThat(service.valueAt(asset(), List.of(tx), DAY, "EUR"))
                .isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"-1, true, 26", "0, true, 26", "1, true, 20", "0, false, 20"})
    void currentPriceIsOnlyAnEstimateAtOrAfterItsRecordedDate(
            int dateOffset, boolean hasPrice, String expected) {
        Asset asset = asset();
        asset.setLastUpdated(
                org.openfinance.util.ReportingClock.startOfDay(DAY.plusDays(dateOffset)));
        asset.setCurrentPrice(hasPrice ? new BigDecimal("13") : null);
        assertThat(service.valueAt(asset, List.of(), DAY, "EUR")).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"date", "price", "negative price", "currency", "blank currency"})
    void invalidMarketQuotesCannotReplaceTheRecordedCost(String missing) {
        Asset asset = asset();
        asset.setSymbol("ABC");
        HistoricalPrice quote =
                HistoricalPrice.builder()
                        .date(DAY)
                        .close(new BigDecimal("99"))
                        .currency("EUR")
                        .build();
        switch (missing) {
            case "date" -> quote.setDate(null);
            case "price" -> quote.setClose(null);
            case "negative price" -> quote.setClose(new BigDecimal("-1"));
            case "currency" -> quote.setCurrency(null);
            case "blank currency" -> quote.setCurrency(" ");
            default -> throw new AssertionError(missing);
        }
        when(market.getHistoricalPrices(any(), any(), any())).thenReturn(List.of(quote));
        assertThat(service.valueAt(asset, List.of(), DAY, "EUR")).isEqualByComparingTo("20");
    }

    @ParameterizedTest
    @CsvSource({"MANUAL, 6", "SKROOGE, 6", "MARKET, 10", "TRADE, 10"})
    void marketRefreshRespectsAuthoritativeSameDayPrices(String source, String expected) {
        Asset asset = asset();
        asset.setSymbol("ABC");
        LocalDate quoteDate = DAY.minusDays(1);
        when(prices.findByAssetIdAndUserIdOrderByPriceDateAsc(7L, 1L))
                .thenReturn(
                        List.of(
                                AssetPriceHistory.builder()
                                        .priceDate(quoteDate)
                                        .price(new BigDecimal("3"))
                                        .currency("EUR")
                                        .source(source)
                                        .build()));
        when(market.getHistoricalPrices(any(), any(), any()))
                .thenReturn(
                        List.of(
                                HistoricalPrice.builder()
                                        .date(quoteDate)
                                        .close(new BigDecimal("5"))
                                        .currency("EUR")
                                        .build()));
        assertThat(service.valueAt(asset, List.of(), DAY, "EUR")).isEqualByComparingTo(expected);
    }

    @Test
    void aMissingAcquisitionDateUsesBoundedHistoryAndCachesTheFetch() {
        Asset asset = asset();
        asset.setSymbol("ABC");
        asset.setPurchaseDate(null);
        when(market.getHistoricalPrices("ABC", LocalDate.now().minusYears(10), LocalDate.now()))
                .thenReturn(
                        List.of(
                                HistoricalPrice.builder()
                                        .date(DAY.minusDays(5))
                                        .close(BigDecimal.ONE)
                                        .currency("EUR")
                                        .build()));
        service.preparePrices(asset, DAY.minusMonths(3), DAY);
        assertThat(service.valueAt(asset, List.of(), DAY, "EUR")).isEqualByComparingTo("2");
        verify(market, times(1)).getHistoricalPrices(any(), any(), any());
        verify(writer, times(1)).recordAll(any(), any(), any(), any());
    }

    @Test
    void preparingPricesForAnAssetNotYetAcquiredHasNoExternalEffects() {
        Asset asset = asset();
        asset.setPurchaseDate(DAY.plusDays(1));
        service.preparePrices(asset, DAY.minusMonths(1), DAY);
        verifyNoInteractions(prices, writer, market, exchange);
    }

    @Test
    void exactStoredQuoteAvoidsNetworkAccess() {
        Asset asset = asset();
        asset.setSymbol("ABC");
        when(prices.findByAssetIdAndUserIdOrderByPriceDateAsc(7L, 1L))
                .thenReturn(
                        List.of(
                                AssetPriceHistory.builder()
                                        .priceDate(DAY)
                                        .price(BigDecimal.ZERO)
                                        .currency("EUR")
                                        .source("MARKET")
                                        .build()));
        assertThat(service.valueAt(asset, List.of(), DAY, "EUR")).isZero();
        verifyNoInteractions(market, writer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing quantity", "missing cost", "blank symbol"})
    void incompleteManualHoldingsRetainTheirKnownValue(String missing) {
        Asset asset = asset();
        if (missing.equals("missing quantity")) asset.setQuantity(null);
        if (missing.equals("missing cost")) asset.setPurchasePrice(null);
        if (missing.equals("blank symbol")) asset.setSymbol(" ");
        assertThat(service.valueAt(asset, List.of(), DAY, "EUR"))
                .isEqualByComparingTo(missing.equals("blank symbol") ? "20" : "0");
        verifyNoInteractions(market, writer);
    }

    private Asset asset() {
        return Asset.builder()
                .id(7L)
                .userId(1L)
                .type(AssetType.STOCK)
                .currency("EUR")
                .purchaseDate(DAY.minusMonths(1))
                .quantity(new BigDecimal("2"))
                .purchasePrice(BigDecimal.TEN)
                .build();
    }

    private Transaction trade() {
        return Transaction.builder()
                .assetId(7L)
                .date(DAY)
                .currency("EUR")
                .investmentQuantity(BigDecimal.ONE)
                .investmentPrice(new BigDecimal("7"))
                .amount(new BigDecimal("7"))
                .isDeleted(false)
                .build();
    }
}
