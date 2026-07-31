package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.CryptoListProperties;
import org.openfinance.dto.CryptoCurrencyInfo;
import org.openfinance.entity.Currency;
import org.openfinance.entity.CurrencyType;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.exception.MarketDataException;
import org.openfinance.provider.CryptoListProvider;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.ExchangeRateRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CryptoListService Tests")
class CryptoListServiceTest {

    @Mock private CryptoListProvider gecko;
    @Mock private CryptoListProvider cmc;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private ExchangeRateRepository exchangeRateRepository;
    @Mock private CurrencyTypeResolver currencyTypeResolver;

    private CryptoListProperties properties;
    private CryptoListService service;

    private CryptoCurrencyInfo info(String code, double price, int rank) {
        return new CryptoCurrencyInfo(code, code, code, rank, BigDecimal.valueOf(price));
    }

    private Currency fiat(String code) {
        return Currency.builder()
                .code(code)
                .name(code)
                .symbol(code)
                .isActive(true)
                .type(CurrencyType.FIAT)
                .build();
    }

    @BeforeEach
    void setUp() {
        properties = new CryptoListProperties();
        lenient().when(gecko.name()).thenReturn("coingecko");
        lenient().when(gecko.isEnabled()).thenReturn(true);
        lenient().when(cmc.name()).thenReturn("coinmarketcap");
        lenient().when(cmc.isEnabled()).thenReturn(true);
        service =
                new CryptoListService(
                        List.of(gecko, cmc),
                        properties,
                        currencyRepository,
                        exchangeRateRepository,
                        currencyTypeResolver);
    }

    @Test
    @DisplayName("falls back to the secondary provider when the primary fails")
    void fallsBackOnFailure() {
        when(gecko.fetchTopCryptocurrencies(anyInt()))
                .thenThrow(new MarketDataException("429 Too Many Requests"));
        when(cmc.fetchTopCryptocurrencies(anyInt())).thenReturn(List.of(info("BTC", 95000, 1)));
        when(currencyRepository.findByCode("BTC")).thenReturn(Optional.empty());
        when(exchangeRateRepository.findByBaseCurrencyAndTargetCurrencyAndRateDate(
                        any(), any(), any()))
                .thenReturn(Optional.empty());

        service.refresh();

        verify(cmc).fetchTopCryptocurrencies(250);
        verify(currencyRepository)
                .save(
                        argThat(
                                c ->
                                        "BTC".equals(c.getCode())
                                                && c.getType() == CurrencyType.CRYPTO));
    }

    @Test
    @DisplayName("does not use the fallback provider when fallback is disabled")
    void pinnedSingleProviderWhenFallbackDisabled() {
        properties.setFallback("disabled");
        when(gecko.fetchTopCryptocurrencies(anyInt())).thenThrow(new MarketDataException("boom"));

        int count = service.refresh();

        assertThat(count).isZero();
        verify(cmc, never()).fetchTopCryptocurrencies(anyInt());
    }

    @Test
    @DisplayName("inserts a new crypto and seeds USD<->crypto rates")
    void insertsAndSeedsRates() {
        when(gecko.fetchTopCryptocurrencies(anyInt())).thenReturn(List.of(info("BTC", 95000, 1)));
        when(currencyRepository.findByCode("BTC")).thenReturn(Optional.empty());
        when(exchangeRateRepository.findByBaseCurrencyAndTargetCurrencyAndRateDate(
                        any(), any(), any()))
                .thenReturn(Optional.empty());

        int count = service.refresh();

        assertThat(count).isEqualTo(1);
        verify(currencyRepository)
                .save(
                        argThat(
                                c ->
                                        "BTC".equals(c.getCode())
                                                && c.getType() == CurrencyType.CRYPTO
                                                && Boolean.TRUE.equals(c.getIsActive())));
        verify(exchangeRateRepository)
                .saveAll(
                        argThat(
                                (Iterable<ExchangeRate> it) -> {
                                    List<ExchangeRate> list = new ArrayList<>();
                                    it.forEach(list::add);
                                    return list.size() == 2
                                            && list.stream()
                                                    .anyMatch(
                                                            r ->
                                                                    "USD"
                                                                                    .equals(
                                                                                            r
                                                                                                    .getBaseCurrency())
                                                                            && "BTC"
                                                                                    .equals(
                                                                                            r
                                                                                                    .getTargetCurrency()))
                                            && list.stream()
                                                    .anyMatch(
                                                            r ->
                                                                    "BTC"
                                                                                    .equals(
                                                                                            r
                                                                                                    .getBaseCurrency())
                                                                            && "USD"
                                                                                    .equals(
                                                                                            r
                                                                                                    .getTargetCurrency()));
                                }));
        verify(currencyTypeResolver).reload();
    }

    @Test
    @DisplayName("never overwrites an existing fiat currency with a colliding crypto code")
    void skipsFiatCollision() {
        when(gecko.fetchTopCryptocurrencies(anyInt())).thenReturn(List.of(info("USDT", 1.0, 3)));
        when(currencyRepository.findByCode("USDT")).thenReturn(Optional.of(fiat("USDT")));

        int count = service.refresh();

        assertThat(count).isZero();
        verify(currencyRepository, never()).save(any());
    }

    @Test
    @DisplayName("returns 0 and calls no provider when the feature is disabled")
    void returnsZeroWhenDisabled() {
        properties.setEnabled(false);

        int count = service.refresh();

        assertThat(count).isZero();
        verify(gecko, never()).fetchTopCryptocurrencies(anyInt());
        verify(cmc, never()).fetchTopCryptocurrencies(anyInt());
    }

    @Test
    @DisplayName("de-duplicates coins sharing the same code (keeps the first / highest rank)")
    void deduplicatesCoinsBySameCode() {
        when(gecko.fetchTopCryptocurrencies(anyInt()))
                .thenReturn(List.of(info("BTC", 95000, 1), info("BTC", 90000, 5)));
        when(currencyRepository.findByCode("BTC")).thenReturn(Optional.empty());
        when(exchangeRateRepository.findByBaseCurrencyAndTargetCurrencyAndRateDate(
                        any(), any(), any()))
                .thenReturn(Optional.empty());

        int count = service.refresh();

        assertThat(count).isEqualTo(1);
        // BTC upserted exactly once (not twice)
        verify(currencyRepository, times(1)).save(any());
        // exactly the two directional rates, no duplicate (base,target,date) rows
        verify(exchangeRateRepository)
                .saveAll(
                        argThat(
                                (Iterable<ExchangeRate> it) -> {
                                    List<ExchangeRate> list = new ArrayList<>();
                                    it.forEach(list::add);
                                    return list.size() == 2;
                                }));
    }
}
