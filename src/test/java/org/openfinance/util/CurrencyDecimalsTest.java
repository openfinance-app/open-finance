package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CurrencyDecimals} — the single shared source of truth for per-currency
 * decimal precision, mirroring the frontend {@code getCurrencyDecimals} helper. Guards against
 * regressions where a fixed 2-decimal rounding would clamp zero-decimal (JPY) or high-precision
 * crypto (BTC) amounts.
 */
class CurrencyDecimalsTest {

    @Test
    @DisplayName("Returns 2 decimals for common fiat currencies")
    void fiatCurrencies() {
        assertThat(CurrencyDecimals.forCurrency("USD")).isEqualTo(2);
        assertThat(CurrencyDecimals.forCurrency("EUR")).isEqualTo(2);
        assertThat(CurrencyDecimals.forCurrency("GBP")).isEqualTo(2);
    }

    @Test
    @DisplayName("Returns 0 decimals for zero-decimal currencies")
    void zeroDecimalCurrencies() {
        assertThat(CurrencyDecimals.forCurrency("JPY")).isEqualTo(0);
        assertThat(CurrencyDecimals.forCurrency("KRW")).isEqualTo(0);
        assertThat(CurrencyDecimals.forCurrency("VND")).isEqualTo(0);
        assertThat(CurrencyDecimals.forCurrency("CLP")).isEqualTo(0);
        assertThat(CurrencyDecimals.forCurrency("ISK")).isEqualTo(0);
    }

    @Test
    @DisplayName("Returns 8 decimals for cryptocurrencies")
    void cryptoCurrencies() {
        assertThat(CurrencyDecimals.forCurrency("BTC")).isEqualTo(8);
        assertThat(CurrencyDecimals.forCurrency("ETH")).isEqualTo(8);
        assertThat(CurrencyDecimals.forCurrency("SOL")).isEqualTo(8);
    }

    @Test
    @DisplayName("Is case-insensitive and trims whitespace")
    void caseInsensitiveAndTrimmed() {
        assertThat(CurrencyDecimals.forCurrency("jpy")).isEqualTo(0);
        assertThat(CurrencyDecimals.forCurrency("  btc  ")).isEqualTo(8);
        assertThat(CurrencyDecimals.forCurrency("Usd")).isEqualTo(2);
    }

    @Test
    @DisplayName("Falls back to 2 decimals for null, blank, or unknown codes")
    void fallbackToDefault() {
        assertThat(CurrencyDecimals.forCurrency(null)).isEqualTo(2);
        assertThat(CurrencyDecimals.forCurrency("")).isEqualTo(2);
        assertThat(CurrencyDecimals.forCurrency("   ")).isEqualTo(2);
        assertThat(CurrencyDecimals.forCurrency("XYZ")).isEqualTo(2);
    }
}
