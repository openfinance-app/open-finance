package org.openfinance.util;

import java.util.Set;

/**
 * Single shared source of truth for the number of decimal places a currency uses.
 *
 * <p>Most fiat currencies use 2 decimal places, but some (JPY, KRW, ...) use 0 and cryptocurrencies
 * (BTC, ETH, ...) use 8. Rounding a monetary amount to a fixed 2 decimals is wrong for those
 * currencies — e.g. it clamps a {@code 0.00123456 BTC} amount to {@code 0.00}, silently destroying
 * value.
 *
 * <p>This mirrors the frontend {@code getCurrencyDecimals} helper in {@code utils/currency.ts} so
 * that both tiers agree on per-currency precision.
 */
public final class CurrencyDecimals {

    /** Currencies that use zero decimal places. */
    private static final Set<String> ZERO_DECIMAL_CURRENCIES =
            Set.of("JPY", "KRW", "VND", "CLP", "ISK");

    /** Cryptocurrencies that use 8 decimal places. */
    private static final Set<String> CRYPTO_CURRENCIES =
            Set.of("BTC", "ETH", "BNB", "ADA", "SOL", "DOT", "AVAX", "MATIC", "LINK", "UNI");

    /** Default decimal places for most fiat currencies. */
    private static final int DEFAULT_DECIMALS = 2;

    private CurrencyDecimals() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Returns the number of decimal places for the given currency code.
     *
     * @param currencyCode ISO 4217 code (e.g. {@code "USD"}, {@code "JPY"}, {@code "BTC"}); may be
     *     {@code null} or blank
     * @return {@code 0} for zero-decimal currencies, {@code 8} for cryptocurrencies, otherwise
     *     {@code 2} (also the fallback for {@code null}/blank/unknown codes)
     */
    public static int forCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return DEFAULT_DECIMALS;
        }
        String normalized = currencyCode.trim().toUpperCase();
        if (ZERO_DECIMAL_CURRENCIES.contains(normalized)) {
            return 0;
        }
        if (CRYPTO_CURRENCIES.contains(normalized)) {
            return 8;
        }
        return DEFAULT_DECIMALS;
    }
}
