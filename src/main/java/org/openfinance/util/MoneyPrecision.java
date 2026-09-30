package org.openfinance.util;

/** Display-independent precision for estimated charges; booked amounts are retained exactly. */
public final class MoneyPrecision {
    private MoneyPrecision() {}

    public static int scale(String currency) {
        if (currency == null) return 18;
        try {
            int digits = java.util.Currency.getInstance(currency).getDefaultFractionDigits();
            return digits < 0 ? 18 : digits;
        } catch (IllegalArgumentException ignored) {
            return 18;
        }
    }
}
