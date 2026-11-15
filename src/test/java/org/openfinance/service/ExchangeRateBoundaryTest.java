package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.dto.MarketQuote;
import org.openfinance.entity.Currency;
import org.openfinance.entity.CurrencyType;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.provider.MarketDataProvider;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.ExchangeRateRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName(
        "Exchange quotes reject invalid dates and prices without discarding valid observations")
class ExchangeRateBoundaryTest {
    @Mock private ExchangeRateRepository rates;
    @Mock private CurrencyRepository currencies;
    @Mock private MarketDataProvider provider;
    @Mock private CurrencyTypeResolver types;
    @InjectMocks private ExchangeRateService service;
    @Captor private ArgumentCaptor<List<ExchangeRate>> batches;

    private HistoricalPrice price(LocalDate date, String amount) {
        return HistoricalPrice.builder()
                .symbol("EURUSD=X")
                .date(date)
                .close(amount == null ? null : new BigDecimal(amount))
                .build();
    }

    private void knownCurrencies() {
        lenient().when(currencies.existsByCode("EUR")).thenReturn(true);
        lenient().when(currencies.existsByCode("USD")).thenReturn(true);
    }

    private void activeCurrency() {
        when(currencies.findByIsActiveTrueOrderByCodeAsc())
                .thenReturn(
                        List.of(Currency.builder().code("EUR").type(CurrencyType.FIAT).build()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"undated", "future", "zero", "negative", "missing-price", "older-last"})
    void latestLookupSelectsTheNewestValidDatedCloseRatherThanTheLastListElement(String invalid) {
        knownCurrencies();
        LocalDate today = LocalDate.now();
        HistoricalPrice bad =
                switch (invalid) {
                    case "undated" -> price(null, "99");
                    case "future" -> price(today.plusDays(1), "99");
                    case "zero" -> price(today, "0");
                    case "negative" -> price(today, "-1");
                    case "missing-price" -> price(today, null);
                    default -> price(today.minusDays(2), "0.5");
                };
        when(provider.getHistoricalPrices(eq("EURUSD=X"), any(), any()))
                .thenReturn(List.of(price(today.minusDays(1), "1.25"), bad));
        assertThat(service.getExchangeRate("EUR", "USD", today)).isEqualByComparingTo("1.25");
        verify(rates).upsertAllIndependently(batches.capture());
        assertThat(batches.getValue())
                .allSatisfy(
                        rate -> {
                            assertThat(rate.getRate()).isEqualByComparingTo("1.25");
                            assertThat(rate.getRateDate()).isEqualTo(today.minusDays(1));
                        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"undated", "future", "zero", "negative", "missing-price"})
    void historicalBackfillIgnoresInvalidObservationsAndUsesTheClosestPriorClose(String invalid) {
        activeCurrency();
        LocalDate date = LocalDate.of(2024, 3, 1);
        HistoricalPrice bad =
                switch (invalid) {
                    case "undated" -> price(null, "99");
                    case "future" -> price(date.plusDays(1), "99");
                    case "zero" -> price(date, "0");
                    case "negative" -> price(date, "-1");
                    default -> price(date, null);
                };
        when(provider.getHistoricalPrices("EURUSD=X", date.minusDays(7), date))
                .thenReturn(
                        List.of(
                                price(date.minusDays(2), "1.1"),
                                price(date.minusDays(1), "1.25"),
                                bad));
        assertThat(service.updateExchangeRatesForDate(date)).isEqualTo(2);
        verify(rates).upsertAllIndependently(batches.capture());
        assertThat(batches.getValue())
                .filteredOn(rate -> rate.getBaseCurrency().equals("EUR"))
                .singleElement()
                .satisfies(
                        rate -> {
                            assertThat(rate.getRate()).isEqualByComparingTo("1.25");
                            assertThat(rate.getRateDate()).isEqualTo(date.minusDays(1));
                        });
    }

    @Test
    void anAlreadyCachedHistoricalObservationIsNotRewritten() {
        activeCurrency();
        LocalDate date = LocalDate.of(2024, 3, 1);
        when(provider.getHistoricalPrices("EURUSD=X", date.minusDays(7), date))
                .thenReturn(List.of(price(date, "1.25")));
        when(rates.findByBaseCurrencyAndTargetCurrencyAndRateDate("EUR", "USD", date))
                .thenReturn(
                        Optional.of(ExchangeRate.builder().rate(new BigDecimal("1.2")).build()));
        when(rates.findByBaseCurrencyAndTargetCurrencyAndRateDate("USD", "EUR", date))
                .thenReturn(
                        Optional.of(ExchangeRate.builder().rate(new BigDecimal("0.8")).build()));
        assertThat(service.updateExchangeRatesForDate(date)).isZero();
        verify(rates, never()).upsertAllIndependently(anyList());
    }

    @Test
    void noActiveCurrenciesMeansNoHistoricalNetworkRequest() {
        assertThat(service.updateExchangeRatesForDate(LocalDate.now())).isZero();
        verifyNoInteractions(provider, rates);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EURUSD=X", "BAD=X", "UNKNOWN"})
    void invalidQuotesDoNotPreventOtherCurrenciesFromBeingStored(String symbol) {
        activeCurrency();
        when(provider.getQuotes(anyList()))
                .thenReturn(
                        List.of(
                                MarketQuote.builder()
                                        .symbol(symbol)
                                        .price(symbol.equals("EURUSD=X") ? null : BigDecimal.ONE)
                                        .build(),
                                MarketQuote.builder()
                                        .symbol("GBPUSD=X")
                                        .price(new BigDecimal("1.25"))
                                        .build()));
        assertThat(service.updateExchangeRates()).isEqualTo(2);
        verify(rates).upsertAllIndependently(batches.capture());
        assertThat(batches.getValue())
                .extracting(ExchangeRate::getBaseCurrency)
                .containsExactly("GBP", "USD");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void invalidCurrencyCannotTriggerQuotes(String currency) {
        assertThatThrownBy(() -> service.getExchangeRate(currency, "USD", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider, rates, currencies);
    }

    @Test
    void currencyIdentityDoesNotDependOnTheServersLanguage() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            lenient().when(currencies.existsByCode("INR")).thenReturn(true);
            assertThat(service.getExchangeRate("inr", "INR", null)).isEqualByComparingTo("1");
            verifyNoInteractions(provider, rates);
        } finally {
            Locale.setDefault(previous);
        }
    }
}
