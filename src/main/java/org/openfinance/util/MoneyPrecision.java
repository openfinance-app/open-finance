package org.openfinance.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Monetary precision boundaries, independent of display settings. Recorded values are retained
 * exactly. Projections use working precision; scheduled payments use currency settlement units.
 */
public final class MoneyPrecision {
    public static final int CALCULATION_SCALE = 18;
    // Headroom beyond the supported 26 integer + 18 fractional input digits.
    public static final MathContext CALCULATION_CONTEXT = new MathContext(80, RoundingMode.HALF_UP);

    private MoneyPrecision() {}

    /** Bound a calculated result without rounding it to the currency's payment denomination. */
    public static BigDecimal calculated(BigDecimal value) {
        return value.setScale(CALCULATION_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /** Currency settlement scale for payment schedules and explicit split allocation only. */
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
