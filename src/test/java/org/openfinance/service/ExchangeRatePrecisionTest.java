package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.provider.MarketDataProvider;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.ExchangeRateRepository;

@ExtendWith(MockitoExtension.class)
class ExchangeRatePrecisionTest {
    @Mock private ExchangeRateRepository rates;
    @Mock private CurrencyRepository currencies;
    @Mock private MarketDataProvider provider;
    @Mock private CurrencyTypeResolver types;
    @InjectMocks private ExchangeRateService service;

    private final Map<String, ExchangeRate> storedRates = new HashMap<>();

    @BeforeEach
    void currenciesExist() {
        when(currencies.existsByCode(anyString())).thenReturn(true);
        when(rates.findLatestByBaseCurrencyAndTargetCurrency(anyString(), anyString()))
                .thenAnswer(
                        call -> {
                            ExchangeRate rate =
                                    storedRates.get(
                                            call.getArgument(0) + ":" + call.getArgument(1));
                            return rate == null ? List.of() : List.of(rate);
                        });
    }

    @Test
    void smallCrossRateMustPreserveAMeaningfulBalance() {
        stored("IDR", "USD", "0.00006");
        stored("BTC", "USD", "50000");
        assertThat(service.convert(new BigDecimal("1000000"), "IDR", "BTC"))
                .isEqualByComparingTo("0.0012");
    }

    @Test
    void recurringRatePrecisionMustNotLoseDollarsInHistoricalValuations() {
        stored("GBP", "USD", "1.4");
        stored("BTC", "USD", "45000");
        BigDecimal btc = service.convert(new BigDecimal("90000"), "GBP", "BTC");
        assertThat(btc.multiply(new BigDecimal("45000")))
                .isCloseTo(
                        new BigDecimal("126000"),
                        org.assertj.core.data.Offset.offset(new BigDecimal("0.00000001")));
    }

    @Test
    void aLargeDirectRateMustHaveANonzeroAccurateInverse() {
        stored("BTC", "JPY", "150000000");
        assertThat(service.getExchangeRate("JPY", "BTC", null))
                .isCloseTo(
                        BigDecimal.ONE.divide(new BigDecimal("150000000"), MathContext.DECIMAL128),
                        org.assertj.core.data.Offset.offset(
                                new BigDecimal("0.000000000000000000000001")));
    }

    @Test
    void quoteResponsePreservesTheActualSourceAndEachLegDate() {
        stored("GBP", "USD", "1.5");
        stored("BTC", "USD", "50000");
        storedRates.get("BTC:USD").setRateDate(LocalDate.of(2026, 9, 30));
        org.openfinance.dto.ExchangeRateResponse response =
                service.getExchangeRateQuote("GBP", "BTC", null)
                        .response("GBP", "BTC", LocalDate.of(2026, 10, 7));
        assertThat(response.rateDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(response.valuationDate()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(response.source()).isEqualTo("precision-test");
        assertThat(response.quoteLegs())
                .extracting(org.openfinance.dto.ExchangeRateLeg::rateDate)
                .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 30));
    }

    private void stored(String from, String to, String rate) {
        storedRates.put(
                from + ":" + to,
                ExchangeRate.builder()
                        .baseCurrency(from)
                        .targetCurrency(to)
                        .rate(new BigDecimal(rate))
                        .rateDate(LocalDate.of(2026, 10, 1))
                        .source("precision-test")
                        .build());
    }
}
