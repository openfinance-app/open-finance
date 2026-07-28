package org.openfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
import org.openfinance.service.CurrencyConversionHelper.ConversionResult;

/**
 * Unit tests for {@link CurrencyConversionHelper} — the single implementation of the base +
 * secondary currency conversion logic previously duplicated across five services.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CurrencyConversionHelper Tests")
class CurrencyConversionHelperTest {

    @Mock private UserRepository userRepository;
    @Mock private DefaultCurrencyProvider defaultCurrencyProvider;
    @Mock private ExchangeRateService exchangeRateService;

    private CurrencyConversionHelper helper;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        helper =
                new CurrencyConversionHelper(
                        userRepository, defaultCurrencyProvider, exchangeRateService);
        lenient()
                .when(defaultCurrencyProvider.resolve(any()))
                .thenAnswer(
                        inv -> {
                            String c = inv.getArgument(0);
                            return (c != null && !c.isBlank()) ? c : "EUR";
                        });
    }

    private void stubUser(String baseCurrency, String secondaryCurrency) {
        User user =
                User.builder()
                        .baseCurrency(baseCurrency)
                        .secondaryCurrency(secondaryCurrency)
                        .build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("native currency equal to base: no conversion, amount passed through")
    void noConversionWhenNativeEqualsBase() {
        stubUser("USD", null);

        ConversionResult r =
                helper.convert(USER_ID, "USD", new BigDecimal("100.00"), true, null, "account");

        assertThat(r.baseCurrency()).isEqualTo("USD");
        assertThat(r.amountInBaseCurrency()).isEqualByComparingTo("100.00");
        assertThat(r.converted()).isFalse();
        assertThat(r.exchangeRate()).isNull();
        assertThat(r.secondaryCurrency()).isNull();
    }

    @Test
    @DisplayName("successful base conversion sets converted amount and rate")
    void successfulBaseConversion() {
        stubUser("EUR", null);
        when(exchangeRateService.getExchangeRate("USD", "EUR", null))
                .thenReturn(new BigDecimal("0.9"));
        when(exchangeRateService.convert(new BigDecimal("100"), "USD", "EUR"))
                .thenReturn(new BigDecimal("90"));

        ConversionResult r =
                helper.convert(USER_ID, "USD", new BigDecimal("100"), true, null, "account");

        assertThat(r.baseCurrency()).isEqualTo("EUR");
        assertThat(r.amountInBaseCurrency()).isEqualByComparingTo("90");
        assertThat(r.exchangeRate()).isEqualByComparingTo("0.9");
        assertThat(r.converted()).isTrue();
    }

    @Test
    @DisplayName("conversion failure falls back to native amount, not converted")
    void conversionFailureFallsBack() {
        stubUser("EUR", null);
        when(exchangeRateService.getExchangeRate("USD", "EUR", null))
                .thenThrow(new RuntimeException("no rate"));

        ConversionResult r =
                helper.convert(USER_ID, "USD", new BigDecimal("100"), true, null, "account");

        assertThat(r.amountInBaseCurrency()).isEqualByComparingTo("100");
        assertThat(r.converted()).isFalse();
        assertThat(r.exchangeRate()).isNull();
    }

    @Test
    @DisplayName("secondary currency conversion populates secondary fields")
    void secondaryConversion() {
        stubUser("EUR", "GBP");
        when(exchangeRateService.getExchangeRate("USD", "EUR", null))
                .thenReturn(new BigDecimal("0.9"));
        when(exchangeRateService.convert(new BigDecimal("100"), "USD", "EUR"))
                .thenReturn(new BigDecimal("90"));
        when(exchangeRateService.getExchangeRate("USD", "GBP", null))
                .thenReturn(new BigDecimal("0.8"));
        when(exchangeRateService.convert(new BigDecimal("100"), "USD", "GBP"))
                .thenReturn(new BigDecimal("80"));

        ConversionResult r =
                helper.convert(USER_ID, "USD", new BigDecimal("100"), true, null, "account");

        assertThat(r.secondaryCurrency()).isEqualTo("GBP");
        assertThat(r.amountInSecondaryCurrency()).isEqualByComparingTo("80");
        assertThat(r.secondaryExchangeRate()).isEqualByComparingTo("0.8");
    }

    @Test
    @DisplayName("secondary equal to native: currency set but no amount/rate")
    void secondaryEqualsNative() {
        stubUser("EUR", "USD");
        when(exchangeRateService.getExchangeRate("USD", "EUR", null))
                .thenReturn(new BigDecimal("0.9"));
        when(exchangeRateService.convert(new BigDecimal("100"), "USD", "EUR"))
                .thenReturn(new BigDecimal("90"));

        ConversionResult r =
                helper.convert(USER_ID, "USD", new BigDecimal("100"), true, null, "account");

        assertThat(r.secondaryCurrency()).isEqualTo("USD");
        assertThat(r.amountInSecondaryCurrency()).isNull();
        assertThat(r.secondaryExchangeRate()).isNull();
    }

    @Test
    @DisplayName("includeSecondary=false ignores the user's secondary currency")
    void secondaryExcludedWhenNotRequested() {
        stubUser("EUR", "GBP");
        when(exchangeRateService.getExchangeRate("USD", "EUR", null))
                .thenReturn(new BigDecimal("0.9"));
        when(exchangeRateService.convert(new BigDecimal("100"), "USD", "EUR"))
                .thenReturn(new BigDecimal("90"));

        ConversionResult r =
                helper.convert(USER_ID, "USD", new BigDecimal("100"), false, null, "transaction");

        assertThat(r.secondaryCurrency()).isNull();
        assertThat(r.amountInSecondaryCurrency()).isNull();
    }

    @Test
    @DisplayName("scale rounds base amount and rate (transaction path)")
    void scaleRoundsBaseAmountAndRate() {
        stubUser("EUR", null);
        when(exchangeRateService.getExchangeRate("USD", "EUR", null))
                .thenReturn(new BigDecimal("0.912345"));
        when(exchangeRateService.convert(new BigDecimal("100"), "USD", "EUR"))
                .thenReturn(new BigDecimal("91.234567"));

        ConversionResult r =
                helper.convert(USER_ID, "USD", new BigDecimal("100"), false, 4, "transaction");

        assertThat(r.amountInBaseCurrency()).isEqualByComparingTo("91.2346");
        assertThat(r.exchangeRate()).isEqualByComparingTo("0.9123");
        assertThat(r.converted()).isTrue();
    }
}
