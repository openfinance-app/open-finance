package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.ExchangeRateLeg;
import org.openfinance.dto.ExchangeRateResponse;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.provider.MarketDataProvider;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.ExchangeRateRepository;

@ExtendWith(MockitoExtension.class)
class CurrencyPegServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 7);
    @Mock private ExchangeRateRepository rates;
    @Mock private CurrencyRepository currencies;
    @Mock private MarketDataProvider provider;
    @Mock private CurrencyTypeResolver types;
    @InjectMocks private ExchangeRateService service;

    @BeforeEach
    void currenciesExist() {
        org.mockito.Mockito.lenient().when(currencies.existsByCode(anyString())).thenReturn(true);
    }

    @ParameterizedTest
    @CsvSource({
        "EUR,XOF,1,655.957", "XAF,EUR,655957,1000", "XOF,XAF,12345,12345",
        "XPF,EUR,1000,8.38", "EUR,KMF,1,491.96775", "EUR,CVE,1,110.265",
        "EUR,BAM,1,1.95583", "EUR,STN,1,24.50", "STD,STN,1000,1",
        "USD,AED,100,367.25", "USD,SAR,100,375", "USD,QAR,100,364",
        "USD,BHD,1000,376", "OMR,USD,1,2.6008", "USD,JOD,1000,709",
        "BBD,BZD,2,2", "BMD,BSD,100,100", "KYD,USD,100,120",
        "USD,XCD,10,27", "AWG,XCG,179,179", "ANG,XCG,179,179",
        "PAB,USD,100,100", "USD,DJF,1,177.721", "USD,ERN,1,15",
        "USD,TMT,1,3.5", "USD,NIO,1,36.6243", "USD,SVC,1,8.75",
        "BND,SGD,100,100", "INR,NPR,100,160", "BTN,INR,100,100",
        "HKD,MOP,100,103", "NAD,LSL,100,100", "SZL,ZAR,100,100",
        "FKP,GBP,100,100", "GIP,SHP,100,100", "EUR,BGN,1,1.95583",
        "FRF,EUR,655.957,100", "EUR,HRK,1,7.5345"
    })
    @DisplayName("Official ratios and their fixed cross-rates need no market quote")
    void fixedParities(String from, String to, String amount, String expected) {
        assertThat(service.convert(new BigDecimal(amount), from, to, DATE))
                .isEqualByComparingTo(expected);
        verifyNoInteractions(rates, provider);
    }

    @Test
    void usesTheParityThatWasEffectiveOnTheObservationDate() {
        assertThat(service.convert(BigDecimal.ONE, "USD", "TMT", LocalDate.of(2014, 12, 31)))
                .isEqualByComparingTo("2.85");
        assertThat(service.convert(BigDecimal.ONE, "USD", "TMT", LocalDate.of(2015, 1, 1)))
                .isEqualByComparingTo("3.5");
        verifyNoInteractions(rates, provider);
    }

    @ParameterizedTest
    @CsvSource({"EUR,XOF,1998-12-31,650", "USD,NIO,2027-01-01,37", "EUR,HRK,2022-01-01,7.52"})
    void doesNotBackdateOrExtendAnExpiredParity(String from, String to, String date, String value) {
        LocalDate at = LocalDate.parse(date);
        stored(from, to, value, at, at);
        assertThat(service.getExchangeRate(from, to, at)).isEqualByComparingTo(value);
        verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @CsvSource({"USD,HKD,7.79", "EUR,DKK,7.46", "USD,USDT,1.001", "USD,KWD,0.307"})
    void bandsBasketsAndStablecoinsRetainTheirMarketQuotes(String from, String to, String value) {
        stored(from, to, value, DATE, DATE);
        assertThat(service.getExchangeRate(from, to, DATE)).isEqualByComparingTo(value);
    }

    @Test
    void fixedCrossRatesUseTheMarketRateBetweenTheirAnchors() {
        LocalDate quoteDate = DATE.minusDays(1);
        stored("EUR", "USD", "1.1", DATE, quoteDate);
        assertThat(service.convert(new BigDecimal("655.957"), "XOF", "AED", DATE))
                .isEqualByComparingTo("4.03975");
        ExchangeRateResponse response =
                service.getExchangeRateQuote("XOF", "AED", DATE).response("XOF", "AED", DATE);
        assertThat(response.rateDate()).isEqualTo(quoteDate);
        assertThat(response.quoteLegs())
                .extracting(ExchangeRateLeg::baseCurrency)
                .containsExactly("EUR", "EUR", "USD");
        assertThat(response.quoteLegs().getFirst().validFrom()).isEqualTo("1999-01-01");
        assertThat(response.quoteLegs().getFirst().sourceUrl()).contains("bceao.int");
    }

    @Test
    void macauFollowsTheObservedHongKongDollarRatherThanAFictitiousDollarConstant() {
        stored("HKD", "USD", "0.128", DATE, DATE);
        assertThat(service.convert(new BigDecimal("103"), "MOP", "USD", DATE))
                .isEqualByComparingTo("12.8");
    }

    @Test
    void reciprocalPreservesDecimal128PrecisionAndFixedQuoteIsNotReportedAsStale() {
        ExchangeRateResponse response =
                service.getExchangeRateQuote("XOF", "EUR", DATE).response("XOF", "EUR", DATE);
        assertThat(response.rate())
                .isEqualByComparingTo(
                        BigDecimal.ONE.divide(new BigDecimal("655.957"), MathContext.DECIMAL128));
        assertThat(response.rateDate()).isEqualTo(DATE);
        assertThat(response.quoteLegs().getFirst().validFrom()).isEqualTo("1999-01-01");
    }

    @Test
    void quoteUpdatesFetchEachFloatingAnchorOnce() {
        when(currencies.findByIsActiveTrueOrderByCodeAsc())
                .thenReturn(
                        java.util.List.of(
                                org.openfinance.entity.Currency.builder().code("XOF").build(),
                                org.openfinance.entity.Currency.builder().code("XAF").build(),
                                org.openfinance.entity.Currency.builder().code("EUR").build(),
                                org.openfinance.entity.Currency.builder().code("AED").build(),
                                org.openfinance.entity.Currency.builder().code("MOP").build()));
        service.updateExchangeRates();
        org.mockito.Mockito.verify(provider).getQuotes(java.util.List.of("EURUSD=X", "HKDUSD=X"));
    }

    private void stored(
            String from, String to, String value, LocalDate requested, LocalDate quoted) {
        when(rates.findByBaseCurrencyAndTargetCurrencyAndRateDate(from, to, requested))
                .thenReturn(
                        Optional.of(
                                ExchangeRate.builder()
                                        .baseCurrency(from)
                                        .targetCurrency(to)
                                        .rate(new BigDecimal(value))
                                        .rateDate(quoted)
                                        .source("market-test")
                                        .build()));
    }
}
