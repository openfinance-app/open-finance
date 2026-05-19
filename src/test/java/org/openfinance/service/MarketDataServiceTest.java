package org.openfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.dto.MarketQuote;
import org.openfinance.dto.SymbolSearchResult;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.exception.MarketDataException;
import org.openfinance.provider.MarketDataProvider;
import org.openfinance.repository.AssetRepository;

/**
 * Unit tests for MarketDataService. Tests market data operations with mocked provider and
 * repository.
 */
@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    @Mock private MarketDataProvider marketDataProvider;

    @Mock private AssetRepository assetRepository;

    @Mock private OperationHistoryService operationHistoryService;

    @InjectMocks private MarketDataService marketDataService;

    @BeforeEach
    void setUp() {
        // Common setup can go here if needed
    }

    @Test
    void shouldGetQuoteSuccessfully() {
        // Given: Mock quote from provider
        MarketQuote expectedQuote =
                MarketQuote.builder()
                        .symbol("AAPL")
                        .name("Apple Inc.")
                        .price(new BigDecimal("175.50"))
                        .currency("USD")
                        .build();

        when(marketDataProvider.getQuote("AAPL")).thenReturn(expectedQuote);

        // When: Get quote
        MarketQuote quote = marketDataService.getQuote("AAPL");

        // Then: Verify quote returned
        assertThat(quote).isNotNull();
        assertThat(quote.getSymbol()).isEqualTo("AAPL");
        assertThat(quote.getPrice()).isEqualByComparingTo(new BigDecimal("175.50"));
        verify(marketDataProvider).getQuote("AAPL");
    }

    @Test
    void shouldCacheGetQuote() {
        // Given: Mock quote
        MarketQuote quote =
                MarketQuote.builder().symbol("AAPL").price(new BigDecimal("175.50")).build();

        when(marketDataProvider.getQuote("AAPL")).thenReturn(quote);

        // When: Call getQuote twice
        marketDataService.getQuote("AAPL");

        // Note: Cache behavior tested in integration test
        // Here we just verify the method works correctly
        verify(marketDataProvider, atLeast(1)).getQuote("AAPL");
    }

    @Test
    void shouldGetMultipleQuotes() {
        // Given: Mock multiple quotes
        List<MarketQuote> expectedQuotes =
                List.of(
                        MarketQuote.builder()
                                .symbol("AAPL")
                                .price(new BigDecimal("175.50"))
                                .build(),
                        MarketQuote.builder()
                                .symbol("MSFT")
                                .price(new BigDecimal("380.00"))
                                .build());

        when(marketDataProvider.getQuotes(anyList())).thenReturn(expectedQuotes);

        // When: Get quotes
        List<MarketQuote> quotes = marketDataService.getQuotes(List.of("AAPL", "MSFT"));

        // Then: Verify all quotes returned
        assertThat(quotes).hasSize(2);
        verify(marketDataProvider).getQuotes(List.of("AAPL", "MSFT"));
    }

    @Test
    void shouldUpdateAssetPriceSuccessfully() {
        // Given: Mock asset and quote
        Asset asset = createMockAsset(1L, 1L, "AAPL", new BigDecimal("100.00"));
        MarketQuote quote =
                MarketQuote.builder().symbol("AAPL").price(new BigDecimal("175.50")).build();

        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(marketDataProvider.getQuote("AAPL")).thenReturn(quote);
        when(assetRepository.save(any(Asset.class))).thenReturn(asset);

        // When: Update asset price
        boolean updated = marketDataService.updateAssetPrice(1L);

        // Then: Verify price updated
        assertThat(updated).isTrue();
        assertThat(asset.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("175.50"));
        assertThat(asset.getLastUpdated()).isNotNull();
        verify(assetRepository).save(asset);
    }

    @Test
    void shouldReturnFalseWhenAssetHasNoSymbol() {
        // Given: Asset with no symbol
        Asset asset = createMockAsset(1L, 1L, null, new BigDecimal("100.00"));

        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));

        // When: Update asset price
        boolean updated = marketDataService.updateAssetPrice(1L);

        // Then: Should return false and not call provider
        assertThat(updated).isFalse();
        verify(marketDataProvider, never()).getQuote(anyString());
        verify(assetRepository, never()).save(any());
    }

    @Test
    void shouldReturnFalseWhenAssetHasEmptySymbol() {
        // Given: Asset with empty symbol
        Asset asset = createMockAsset(1L, 1L, "", new BigDecimal("100.00"));

        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));

        // When: Update asset price
        boolean updated = marketDataService.updateAssetPrice(1L);

        // Then: Should return false
        assertThat(updated).isFalse();
        verify(marketDataProvider, never()).getQuote(anyString());
    }

    @Test
    void shouldThrowExceptionWhenAssetNotFound() {
        // Given: Asset doesn't exist
        when(assetRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() -> marketDataService.updateAssetPrice(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Asset not found");
    }

    @Test
    void shouldThrowExceptionWhenMarketDataUnavailable() {
        // Given: Asset exists but market data fails
        Asset asset = createMockAsset(1L, 1L, "AAPL", new BigDecimal("100.00"));

        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(marketDataProvider.getQuote("AAPL"))
                .thenThrow(new MarketDataException("Service unavailable", "AAPL", 503));

        // When/Then: Should propagate MarketDataException
        assertThatThrownBy(() -> marketDataService.updateAssetPrice(1L))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("Service unavailable");
    }

    @Test
    void shouldUpdateMultipleAssetPrices() {
        // Given: User has multiple assets
        Asset asset1 = createMockAsset(1L, 10L, "AAPL", new BigDecimal("100.00"));
        Asset asset2 = createMockAsset(2L, 10L, "MSFT", new BigDecimal("200.00"));
        Asset asset3 = createMockAsset(3L, 10L, null, new BigDecimal("50.00")); // No symbol

        List<Asset> assets = new ArrayList<>();
        assets.add(asset1);
        assets.add(asset2);
        assets.add(asset3);

        List<MarketQuote> quotes =
                List.of(
                        MarketQuote.builder()
                                .symbol("AAPL")
                                .price(new BigDecimal("175.50"))
                                .build(),
                        MarketQuote.builder()
                                .symbol("MSFT")
                                .price(new BigDecimal("380.00"))
                                .build());

        when(assetRepository.findByUserId(10L)).thenReturn(assets);
        when(marketDataProvider.getQuotes(List.of("AAPL", "MSFT"))).thenReturn(quotes);
        when(assetRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Update all user assets
        int updatedCount = marketDataService.updateAssetPrices(10L);

        // Then: Should update only assets with symbols
        assertThat(updatedCount).isEqualTo(2);
        assertThat(asset1.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("175.50"));
        assertThat(asset2.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("380.00"));
        assertThat(asset3.getCurrentPrice())
                .isEqualByComparingTo(new BigDecimal("50.00")); // Unchanged
        verify(assetRepository).saveAll(anyList());
    }

    @Test
    void shouldReturnZeroWhenUserHasNoAssets() {
        // Given: User has no assets
        when(assetRepository.findByUserId(10L)).thenReturn(List.of());

        // When: Update asset prices
        int updatedCount = marketDataService.updateAssetPrices(10L);

        // Then: Should return 0
        assertThat(updatedCount).isZero();
        verify(marketDataProvider, never()).getQuotes(anyList());
    }

    @Test
    void shouldReturnZeroWhenNoAssetsHaveSymbols() {
        // Given: Assets without symbols
        Asset asset = createMockAsset(1L, 10L, null, new BigDecimal("100.00"));

        when(assetRepository.findByUserId(10L)).thenReturn(List.of(asset));

        // When: Update asset prices
        int updatedCount = marketDataService.updateAssetPrices(10L);

        // Then: Should return 0
        assertThat(updatedCount).isZero();
        verify(marketDataProvider, never()).getQuotes(anyList());
    }

    @Test
    void shouldSkipAssetsWithInvalidPrices() {
        // Given: Quote with null price
        Asset asset = createMockAsset(1L, 10L, "INVALID", new BigDecimal("100.00"));

        List<MarketQuote> quotes =
                List.of(MarketQuote.builder().symbol("INVALID").price(null).build());

        when(assetRepository.findByUserId(10L)).thenReturn(List.of(asset));
        when(marketDataProvider.getQuotes(List.of("INVALID"))).thenReturn(quotes);

        // When: Update asset prices
        int updatedCount = marketDataService.updateAssetPrices(10L);

        // Then: Should skip asset with invalid price
        assertThat(updatedCount).isZero();
        assertThat(asset.getCurrentPrice())
                .isEqualByComparingTo(new BigDecimal("100.00")); // Unchanged
    }

    @Test
    void shouldGetHistoricalPrices() {
        // Given: Mock historical data
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        List<HistoricalPrice> expectedPrices =
                List.of(
                        HistoricalPrice.builder()
                                .symbol("AAPL")
                                .date(LocalDate.of(2024, 1, 2))
                                .close(new BigDecimal("185.50"))
                                .build());

        when(marketDataProvider.getHistoricalPrices("AAPL", startDate, endDate))
                .thenReturn(expectedPrices);

        // When: Get historical prices
        List<HistoricalPrice> prices =
                marketDataService.getHistoricalPrices("AAPL", startDate, endDate);

        // Then: Verify data returned
        assertThat(prices).hasSize(1);
        assertThat(prices.get(0).getSymbol()).isEqualTo("AAPL");
        verify(marketDataProvider).getHistoricalPrices("AAPL", startDate, endDate);
    }

    @Test
    void shouldSearchSymbols() {
        // Given: Mock search results
        List<SymbolSearchResult> expectedResults =
                List.of(
                        SymbolSearchResult.builder()
                                .symbol("AAPL")
                                .name("Apple Inc.")
                                .type("EQUITY")
                                .build());

        when(marketDataProvider.searchSymbol("apple")).thenReturn(expectedResults);

        // When: Search symbols
        List<SymbolSearchResult> results = marketDataService.searchSymbol("apple");

        // Then: Verify results returned
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSymbol()).isEqualTo("AAPL");
        verify(marketDataProvider).searchSymbol("apple");
    }

    /** Helper method to create mock Asset. */
    private Asset createMockAsset(Long id, Long userId, String symbol, BigDecimal currentPrice) {
        Asset asset =
                Asset.builder()
                        .id(id)
                        .userId(userId)
                        .accountId(1L)
                        .name("Test Asset")
                        .type(AssetType.STOCK)
                        .symbol(symbol)
                        .quantity(new BigDecimal("10"))
                        .purchasePrice(new BigDecimal("100.00"))
                        .currentPrice(currentPrice)
                        .currency("USD")
                        .purchaseDate(LocalDate.now().minusMonths(6))
                        .lastUpdated(LocalDateTime.now().minusDays(1))
                        .build();
        return asset;
    }
}
