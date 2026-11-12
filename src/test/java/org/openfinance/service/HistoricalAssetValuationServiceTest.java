package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetPriceHistory;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Transaction;
import org.openfinance.exception.MarketDataException;
import org.openfinance.repository.AssetPriceHistoryRepository;

@ExtendWith(MockitoExtension.class)
class HistoricalAssetValuationServiceTest {
    private static final LocalDate END = LocalDate.of(2024, 12, 31);
    @Mock private AssetPriceHistoryRepository prices;
    @Mock private AssetPriceHistoryWriter writer;
    @Mock private MarketDataService marketData;
    @Mock private ExchangeRateService exchangeRates;
    @InjectMocks private HistoricalAssetValuationServiceImpl service;

    @BeforeEach
    void setup() {
        org.mockito.Mockito.lenient()
                .when(exchangeRates.convert(any(), any(), any(), any()))
                .thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void valuesYearEndDogeUnitsAtLastYearEndPriceInsteadOfCostOrFuturePrice() {
        Asset asset = holding();
        asset.setQuantity(new BigDecimal("48.3529383"));
        when(prices.findByAssetIdAndUserIdOrderByPriceDateAsc(7L, 1L))
                .thenReturn(
                        List.of(
                                stored(END.minusDays(4), "0.2981366657", "EUR"),
                                stored(END.plusYears(1), "0.06443275927", "EUR")));
        List<Transaction> movements =
                List.of(buy(), movement(2L, END.plusDays(1), "-161.3570617", "50"));
        assertThat(service.valueAt(asset, movements, END, "EUR"))
                .isEqualByComparingTo("62.522240163947");
        verify(marketData).getHistoricalPrices(any(), any(), any());
    }

    @Test
    void usesUnitsRemainingAfterASaleBeforeTheSnapshot() {
        when(prices.findByAssetIdAndUserIdOrderByPriceDateAsc(7L, 1L))
                .thenReturn(List.of(stored(END.minusDays(4), "2", "EUR")));
        assertThat(
                        service.valueAt(
                                holding(),
                                List.of(buy(), movement(2L, END.minusDays(1), "-200", "100")),
                                END,
                                "EUR"))
                .isEqualByComparingTo("19.42");
    }

    @Test
    void zeroPositionDoesNotFetchOrAddPrices() {
        assertThat(
                        service.valueAt(
                                holding(),
                                List.of(buy(), movement(2L, END.minusDays(1), "-209.71", "100")),
                                END,
                                "EUR"))
                .isZero();
        verifyNoInteractions(prices, marketData, writer, exchangeRates);
    }

    @Test
    void convertsTheQuoteCurrencyAtTheSnapshotDate() {
        when(prices.findByAssetIdAndUserIdOrderByPriceDateAsc(7L, 1L))
                .thenReturn(List.of(stored(END.minusDays(4), "2", "USD")));
        when(exchangeRates.convert(new BigDecimal("419.42"), "USD", "EUR", END))
                .thenReturn(new BigDecimal("377.478"));
        assertThat(service.valueAt(holding(), List.of(buy()), END, "EUR"))
                .isEqualByComparingTo("377.478");
        verify(exchangeRates).convert(new BigDecimal("419.42"), "USD", "EUR", END);
    }

    @Test
    void fillsAMissingYearEndQuoteEvenWhenImportedHistoryCoversLaterDates() {
        when(prices.findByAssetIdAndUserIdOrderByPriceDateAsc(7L, 1L))
                .thenReturn(
                        List.of(
                                stored(END.minusDays(4), "0.30", "EUR"),
                                stored(END.plusYears(1), "0.06", "EUR")));
        when(marketData.getHistoricalPrices(any(), any(), any()))
                .thenReturn(
                        List.of(
                                HistoricalPrice.builder()
                                        .date(END)
                                        .currency("EUR")
                                        .close(new BigDecimal("0.32"))
                                        .build()));
        assertThat(service.valueAt(holding(), List.of(buy()), END, "EUR"))
                .isEqualByComparingTo("67.1072");
    }

    @Test
    void fetchesAndStoresUnadjustedHistoricalCloseForAlreadyImportedAssets() {
        HistoricalPrice price =
                HistoricalPrice.builder()
                        .date(END.minusDays(4))
                        .currency("EUR")
                        .close(new BigDecimal("0.2981366657"))
                        .adjustedClose(new BigDecimal("0.1"))
                        .build();
        HistoricalPrice future =
                HistoricalPrice.builder()
                        .date(END.plusDays(1))
                        .currency("EUR")
                        .close(new BigDecimal("99"))
                        .build();
        AssetPriceHistory trade = stored(holding().getPurchaseDate(), "0.3561283785", "EUR");
        trade.setSource("TRADE");
        when(prices.findByAssetIdAndUserIdOrderByPriceDateAsc(7L, 1L)).thenReturn(List.of(trade));
        when(marketData.getHistoricalPrices(
                        "DOGE-EUR", holding().getPurchaseDate().minusDays(7), LocalDate.now()))
                .thenReturn(List.of(price, future));
        assertThat(service.valueAt(holding(), List.of(buy()), END, "EUR"))
                .isEqualByComparingTo("62.522240163947");
        verify(writer)
                .recordAll(
                        org.mockito.ArgumentMatchers.eq(1L),
                        any(Asset.class),
                        org.mockito.ArgumentMatchers.eq(List.of(price, future)),
                        org.mockito.ArgumentMatchers.eq("MARKET"));
    }

    @Test
    void unavailableMarketDataKeepsADatedEntryEstimateAndNeverUsesTodaysPrice() {
        Asset asset = holding();
        asset.setCurrentPrice(new BigDecimal("999"));
        asset.setLastUpdated(END.plusDays(1).atStartOfDay());
        when(marketData.getHistoricalPrices(any(), any(), any()))
                .thenThrow(new MarketDataException("offline"));
        assertThat(service.valueAt(asset, List.of(buy()), END, "EUR"))
                .isCloseTo(
                        new BigDecimal("74.683682255235"),
                        org.assertj.core.data.Offset.offset(new BigDecimal("0.000000000001")));
    }

    @Test
    void laterOpeningDateIsStillExcluded() {
        Asset asset = holding();
        asset.setPurchaseDate(END.plusDays(1));
        assertThat(service.valueAt(asset, List.of(), END, "EUR")).isZero();
        verifyNoInteractions(prices, marketData, exchangeRates);
    }

    private Asset holding() {
        return Asset.builder()
                .id(7L)
                .userId(1L)
                .accountId(2L)
                .name("Dogecoin")
                .type(AssetType.CRYPTO)
                .currency("EUR")
                .symbol("DOGE-EUR")
                .purchaseDate(LocalDate.of(2024, 11, 13))
                .quantity(new BigDecimal("209.71"))
                .purchasePrice(new BigDecimal("0.3561283785"))
                .importSource("skrooge:unit:8")
                .importOpeningQuantity(BigDecimal.ZERO)
                .importOpeningCost(BigDecimal.ZERO)
                .build();
    }

    private Transaction buy() {
        return movement(1L, LocalDate.of(2024, 11, 13), "209.71", "74.683682255235");
    }

    private Transaction movement(Long id, LocalDate date, String quantity, String value) {
        return Transaction.builder()
                .id(id)
                .assetId(7L)
                .userId(1L)
                .accountId(2L)
                .date(date)
                .investmentQuantity(new BigDecimal(quantity))
                .amount(new BigDecimal(value))
                .currency("EUR")
                .build();
    }

    private AssetPriceHistory stored(LocalDate date, String price, String currency) {
        return AssetPriceHistory.builder()
                .assetId(7L)
                .userId(1L)
                .priceDate(date)
                .price(new BigDecimal(price))
                .currency(currency)
                .source("SKROOGE")
                .build();
    }
}
