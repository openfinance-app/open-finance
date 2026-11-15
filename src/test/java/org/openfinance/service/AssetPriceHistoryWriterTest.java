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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetPriceHistory;
import org.openfinance.entity.AssetType;
import org.openfinance.repository.AssetPriceHistoryRepository;
import org.openfinance.repository.NetWorthRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Historical quote writes respect ownership, source priority and snapshot boundaries")
class AssetPriceHistoryWriterTest {
    @Mock private AssetPriceHistoryRepository prices;
    @Mock private NetWorthRepository snapshots;
    @InjectMocks private AssetPriceHistoryWriter writer;
    private final LocalDate date = LocalDate.of(2025, 1, 10);

    private Asset asset() {
        return Asset.builder().id(3L).userId(7L).type(AssetType.STOCK).currency("EUR").build();
    }

    private HistoricalPrice quote() {
        return HistoricalPrice.builder()
                .date(date)
                .currency("EUR")
                .close(new BigDecimal("12.34"))
                .build();
    }

    private AssetPriceHistory existing(String source) {
        return AssetPriceHistory.builder()
                .id(11L)
                .userId(7L)
                .assetId(3L)
                .priceDate(date)
                .currency("EUR")
                .price(new BigDecimal("10.01"))
                .source(source)
                .build();
    }

    @ParameterizedTest
    @CsvSource({
        "MANUAL,MARKET,false",
        "MANUAL,SKROOGE,false",
        "SKROOGE,MARKET,false",
        "MARKET,MANUAL,true",
        "SKROOGE,MANUAL,true",
        "MARKET,SKROOGE,true",
        "CSV,MARKET,true",
        "CSV,CSV,true"
    })
    void importedAndFetchedQuotesNeverOverwriteHigherPriorityObservations(
            String oldSource, String newSource, boolean changed) {
        AssetPriceHistory existing = existing(oldSource);
        when(prices.findByAssetIdAndUserIdOrderByPriceDateAsc(3L, 7L))
                .thenReturn(List.of(existing));
        writer.recordAll(7L, asset(), List.of(quote()), newSource);
        if (changed) {
            verify(prices).saveAll(List.of(existing));
            verify(snapshots).deleteReconstructedFrom(7L, date);
            assertThat(existing.getPrice()).isEqualByComparingTo("12.34");
            assertThat(existing.getSource()).isEqualTo(newSource);
        } else {
            verify(prices, never()).saveAll(any());
            verifyNoInteractions(snapshots);
            assertThat(existing.getPrice()).isEqualByComparingTo("10.01");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"unchanged", "currency", "source"})
    void identicalNumbersStillRequireCurrencyAndSourceEqualityToSkipAnUpdate(String change) {
        AssetPriceHistory existing = existing("MARKET");
        HistoricalPrice quote = quote();
        quote.setClose(new BigDecimal("10.010"));
        if (change.equals("currency")) quote.setCurrency("USD");
        when(prices.findByAssetIdAndUserIdOrderByPriceDateAsc(3L, 7L))
                .thenReturn(List.of(existing));
        writer.recordAll(
                7L, asset(), List.of(quote), change.equals("source") ? "MANUAL" : "MARKET");
        if (change.equals("unchanged")) {
            verify(prices, never()).saveAll(any());
            verifyNoInteractions(snapshots);
        } else {
            verify(prices).saveAll(List.of(existing));
            verify(snapshots).deleteReconstructedFrom(7L, date);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"date", "future", "price", "negative", "currency", "blank-currency"})
    void unusableQuotesDoNotEvictValidReconstructedHistory(String missing) {
        HistoricalPrice quote = quote();
        switch (missing) {
            case "date" -> quote.setDate(null);
            case "future" -> quote.setDate(LocalDate.now().plusDays(1));
            case "price" -> quote.setClose(null);
            case "negative" -> quote.setClose(new BigDecimal("-0.01"));
            case "currency" -> quote.setCurrency(null);
            case "blank-currency" -> quote.setCurrency(" ");
            default -> throw new AssertionError(missing);
        }
        writer.recordAll(7L, asset(), List.of(quote), "MARKET");
        verify(prices, never()).saveAll(any());
        verifyNoInteractions(snapshots);
    }

    @ParameterizedTest
    @ValueSource(strings = {"physical", "property", "unsaved", "empty"})
    void onlyPersistedFinancialAssetsReceiveMarketQuoteHistory(String kind) {
        Asset asset = asset();
        if (kind.equals("physical")) asset.setType(AssetType.VEHICLE);
        if (kind.equals("property")) asset.setType(AssetType.REAL_ESTATE);
        if (kind.equals("unsaved")) asset.setId(null);
        writer.recordAll(7L, asset, kind.equals("empty") ? List.of() : List.of(quote()), "MARKET");
        verifyNoInteractions(prices, snapshots);
    }

    @Test
    void cannotWriteAnotherUsersHistoricalQuotes() {
        assertThatThrownBy(() -> writer.recordAll(8L, asset(), List.of(quote()), "MANUAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");
        verifyNoInteractions(prices, snapshots);
    }

    @Test
    void unsortedNewQuotesInvalidateOnlyFromTheEarliestChangedDateAndKeepAZeroValuation() {
        HistoricalPrice older =
                quote().toBuilder().date(date.minusDays(1)).close(BigDecimal.ZERO).build();
        writer.recordAll(7L, asset(), List.of(quote(), older, quote()), "SKROOGE");
        ArgumentCaptor<Iterable<AssetPriceHistory>> rows = ArgumentCaptor.forClass(Iterable.class);
        verify(prices).saveAll(rows.capture());
        assertThat(rows.getValue())
                .hasSize(2)
                .allSatisfy(
                        row -> {
                            assertThat(row.getAssetId()).isEqualTo(3L);
                            assertThat(row.getUserId()).isEqualTo(7L);
                        });
        assertThat(rows.getValue())
                .filteredOn(row -> row.getPriceDate().equals(date.minusDays(1)))
                .singleElement()
                .satisfies(row -> assertThat(row.getPrice()).isZero());
        verify(snapshots).deleteReconstructedFrom(7L, date.minusDays(1));
    }
}
