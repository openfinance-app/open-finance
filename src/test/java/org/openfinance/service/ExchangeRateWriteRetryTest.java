package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.MarketQuote;
import org.openfinance.entity.Currency;
import org.openfinance.entity.CurrencyType;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.provider.MarketDataProvider;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.ExchangeRateRepository;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ExchangeRateWriteRetryTest {
    @Mock private ExchangeRateRepository rates;
    @Mock private CurrencyRepository currencies;
    @Mock private MarketDataProvider provider;
    @Mock private CurrencyTypeResolver types;
    @InjectMocks private ExchangeRateService service;
    @Captor private ArgumentCaptor<List<ExchangeRate>> batches;

    @BeforeEach
    void prepareQuotes() {
        when(currencies.findByIsActiveTrueOrderByCodeAsc())
                .thenReturn(
                        List.of(Currency.builder().code("EUR").type(CurrencyType.FIAT).build()));
        when(provider.getQuotes(anyList()))
                .thenReturn(
                        List.of(
                                MarketQuote.builder()
                                        .symbol("EURUSD=X")
                                        .price(new BigDecimal("1.25"))
                                        .build()));
    }

    @Test
    @DisplayName("Retries the same direct and inverse rates without refetching quotes")
    void retriesFetchedBatch() {
        doThrow(new CannotAcquireLockException("database is locked"))
                .doNothing()
                .when(rates)
                .upsertAllIndependently(anyList());

        assertThat(service.updateExchangeRates()).isEqualTo(2);

        verify(rates, times(2)).upsertAllIndependently(batches.capture());
        assertThat(batches.getAllValues().get(1)).isSameAs(batches.getAllValues().getFirst());
        assertThat(batches.getValue())
                .extracting(ExchangeRate::getBaseCurrency)
                .containsExactly("EUR", "USD");
        assertThat(batches.getValue().get(0).getRate()).isEqualByComparingTo("1.25");
        assertThat(batches.getValue().get(1).getRate()).isEqualByComparingTo("0.8");
        verify(provider).getQuotes(anyList());
        verify(currencies).findByIsActiveTrueOrderByCodeAsc();
    }

    @Test
    @DisplayName("Propagates the final lock failure after three write attempts")
    void stopsAfterThreeLockFailures() {
        CannotAcquireLockException failure = new CannotAcquireLockException("database is locked");
        doThrow(failure).when(rates).upsertAllIndependently(anyList());

        assertThatThrownBy(service::updateExchangeRates).isSameAs(failure);

        verify(rates, times(3)).upsertAllIndependently(anyList());
        verify(provider).getQuotes(anyList());
    }

    @Test
    @DisplayName("Does not retry a non-lock database failure")
    void propagatesNonLockFailuresImmediately() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("invalid rate");
        doThrow(failure).when(rates).upsertAllIndependently(anyList());

        assertThatThrownBy(service::updateExchangeRates).isSameAs(failure);

        verify(rates).upsertAllIndependently(anyList());
        verify(provider).getQuotes(anyList());
    }

    @Test
    @DisplayName("Preserves interruption and stops before another write attempt")
    void stopsWhenRetryDelayIsInterrupted() {
        CannotAcquireLockException failure = new CannotAcquireLockException("database is locked");
        doAnswer(
                        call -> {
                            Thread.currentThread().interrupt();
                            throw failure;
                        })
                .when(rates)
                .upsertAllIndependently(anyList());

        try {
            assertThatThrownBy(service::updateExchangeRates).isSameAs(failure);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(failure.getSuppressed()).hasOnlyElementsOfType(InterruptedException.class);
            verify(rates).upsertAllIndependently(anyList());
        } finally {
            Thread.interrupted();
        }
    }
}
