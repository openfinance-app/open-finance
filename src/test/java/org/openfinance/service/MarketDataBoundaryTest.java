package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.dto.MarketQuote;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.exception.MarketDataException;
import org.openfinance.provider.MarketDataProvider;
import org.openfinance.repository.AssetRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Market-data boundaries retain valid prices and preserve dated quote currencies")
class MarketDataBoundaryTest {
    @Mock private MarketDataProvider provider;
    @Mock private AssetRepository assets;
    @Mock private ExchangeRateService rates;
    @Mock private AssetPriceHistoryWriter history;
    @Mock private OperationHistoryService operations;
    @InjectMocks private MarketDataService service;
    private final LocalDate date = LocalDate.of(2025, 1, 10);

    private Asset asset(long id, String symbol) {
        return Asset.builder()
                .id(id)
                .userId(7L)
                .type(AssetType.STOCK)
                .symbol(symbol)
                .currency("EUR")
                .quantity(BigDecimal.ONE)
                .purchasePrice(new BigDecimal("10"))
                .currentPrice(new BigDecimal("12.34"))
                .acquisitionType(AcquisitionType.PURCHASE)
                .build();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1"})
    void invalidQuotesNeverReplaceTheLastKnownPositivePrice(String price) {
        Asset asset = asset(1L, "ACME");
        when(assets.findById(1L)).thenReturn(Optional.of(asset));
        when(provider.getQuote("ACME"))
                .thenReturn(
                        MarketQuote.builder()
                                .symbol("ACME")
                                .currency("EUR")
                                .price(price == null ? null : new BigDecimal(price))
                                .build());
        assertThat(service.updateAssetPrice(1L)).isFalse();
        assertThat(asset.getCurrentPrice()).isEqualByComparingTo("12.34");
        verify(assets, never()).save(any());
        verifyNoInteractions(history);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void quoteWithoutCurrencyCannotBeLabeledAsTheHoldingsCurrency(String currency) {
        Asset asset = asset(1L, "ACME");
        when(assets.findById(1L)).thenReturn(Optional.of(asset));
        when(provider.getQuote("ACME"))
                .thenReturn(
                        MarketQuote.builder()
                                .symbol("ACME")
                                .currency(currency)
                                .price(new BigDecimal("15"))
                                .build());
        assertThatThrownBy(() -> service.updateAssetPrice(1L))
                .isInstanceOf(MarketDataException.class);
        assertThat(asset.getCurrentPrice()).isEqualByComparingTo("12.34");
        verify(assets, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "exception"})
    void failedOrInvalidConversionKeepsTheLastKnownPrice(String conversion) {
        Asset asset = asset(1L, "ACME");
        when(assets.findById(1L)).thenReturn(Optional.of(asset));
        when(provider.getQuote("ACME"))
                .thenReturn(
                        MarketQuote.builder()
                                .symbol("ACME")
                                .currency("USD")
                                .price(new BigDecimal("15"))
                                .build());
        if ("exception".equals(conversion))
            when(rates.convert(new BigDecimal("15"), "USD", "EUR"))
                    .thenThrow(new IllegalStateException("No rate"));
        else
            when(rates.convert(new BigDecimal("15"), "USD", "EUR"))
                    .thenReturn(conversion == null ? null : new BigDecimal(conversion));
        assertThatThrownBy(() -> service.updateAssetPrice(1L))
                .isInstanceOf(MarketDataException.class);
        assertThat(asset.getCurrentPrice()).isEqualByComparingTo("12.34");
        verifyNoInteractions(history);
    }

    @Test
    void oneUnconvertibleQuoteDoesNotPreventUpdatingAnotherHolding() {
        Asset first = asset(1L, "FOREIGN");
        Asset second = asset(2L, "NATIVE");
        Asset plan = asset(3L, "PLANNED");
        plan.setAcquisitionType(AcquisitionType.PLANNED);
        when(assets.findByUserId(7L)).thenReturn(List.of(first, second, plan));
        when(provider.getQuotes(List.of("FOREIGN", "NATIVE")))
                .thenReturn(
                        List.of(
                                MarketQuote.builder()
                                        .symbol("FOREIGN")
                                        .currency("USD")
                                        .price(new BigDecimal("15"))
                                        .build(),
                                MarketQuote.builder()
                                        .symbol("NATIVE")
                                        .currency("EUR")
                                        .price(new BigDecimal("16.01"))
                                        .timestamp(date.atStartOfDay())
                                        .build(),
                                MarketQuote.builder()
                                        .symbol("NATIVE")
                                        .currency("EUR")
                                        .price(new BigDecimal("999"))
                                        .build()));
        when(rates.convert(new BigDecimal("15"), "USD", "EUR"))
                .thenThrow(new IllegalStateException("No rate"));
        assertThat(service.updateAssetPrices(7L)).isEqualTo(1);
        assertThat(first.getCurrentPrice()).isEqualByComparingTo("12.34");
        assertThat(second.getCurrentPrice()).isEqualByComparingTo("16.01");
        assertThat(plan.getCurrentPrice()).isEqualByComparingTo("12.34");
        verify(history).record(7L, second, date, new BigDecimal("16.01"), "EUR", "MARKET");
    }

    @Test
    void historicalQuotesConvertEachPriceFieldAtItsOwnDateWithoutRewritingTheProviderData() {
        HistoricalPrice quote =
                HistoricalPrice.builder()
                        .symbol("ACME")
                        .date(date)
                        .currency("USD")
                        .open(new BigDecimal("10.01"))
                        .high(new BigDecimal("12.01"))
                        .low(new BigDecimal("9.01"))
                        .close(new BigDecimal("11.01"))
                        .adjustedClose(null)
                        .build();
        when(provider.getHistoricalPrices("ACME", date, date)).thenReturn(List.of(quote));
        when(rates.getExchangeRate("USD", "EUR", date))
                .thenReturn(new BigDecimal("0.812345678901234567"));
        HistoricalPrice converted =
                service.getHistoricalPrices("ACME", date, date, "eur").getFirst();
        assertThat(converted.getCurrency()).isEqualTo("EUR");
        assertThat(converted.getOpen()).isEqualByComparingTo("8.131580245801358016");
        assertThat(converted.getHigh()).isEqualByComparingTo("9.756271603603827150");
        assertThat(converted.getLow()).isEqualByComparingTo("7.319234566900123449");
        assertThat(converted.getClose()).isEqualByComparingTo("8.943925924702592583");
        assertThat(converted.getAdjustedClose()).isNull();
        assertThat(quote.getCurrency()).isEqualTo("USD");
        assertThat(quote.getOpen()).isEqualByComparingTo("10.01");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing-currency",
                "blank-currency",
                "missing-date",
                "missing-rate",
                "zero-rate",
                "negative-rate"
            })
    void historicalSeriesFailExplicitlyWhenDatedConversionCannotBeConfirmed(String missing) {
        HistoricalPrice quote =
                HistoricalPrice.builder()
                        .symbol("ACME")
                        .currency("USD")
                        .date(date)
                        .close(new BigDecimal("10"))
                        .build();
        if (missing.equals("missing-currency")) quote.setCurrency(null);
        if (missing.equals("blank-currency")) quote.setCurrency(" ");
        if (missing.equals("missing-date")) quote.setDate(null);
        if (missing.equals("zero-rate") || missing.equals("negative-rate"))
            when(rates.getExchangeRate("USD", "EUR", date))
                    .thenReturn(new BigDecimal(missing.equals("zero-rate") ? "0" : "-1"));
        when(provider.getHistoricalPrices("ACME", date, date)).thenReturn(List.of(quote));
        assertThatThrownBy(() -> service.getHistoricalPrices("ACME", date, date, "EUR"))
                .isInstanceOf(MarketDataException.class);
    }

    @ParameterizedTest
    @CsvSource({"CRYPTO,btc,BTC-USD", "CRYPTO,BTC-EUR,BTC-EUR", "STOCK,ACME,ACME"})
    void batchQuotesUseNormalizedProviderSymbolsAndPersistSuccessfulNormalizations(
            AssetType type, String symbol, String providerSymbol) {
        Asset asset = asset(1L, symbol);
        asset.setType(type);
        when(assets.findByUserId(7L)).thenReturn(List.of(asset));
        when(provider.getQuotes(List.of(providerSymbol)))
                .thenReturn(
                        List.of(
                                MarketQuote.builder()
                                        .symbol(providerSymbol)
                                        .currency("EUR")
                                        .price(new BigDecimal("25"))
                                        .build()));
        assertThat(service.updateAssetPrices(7L)).isEqualTo(1);
        assertThat(asset.getSymbol()).isEqualTo(providerSymbol);
        assertThat(asset.getCurrentPrice()).isEqualByComparingTo("25");
    }
}
