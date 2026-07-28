package org.openfinance.util;

import java.math.BigDecimal;

/**
 * Shared numeric constants for monetary/percentage math.
 *
 * <p>Single source of truth for the literal {@code 100} used pervasively in percentage conversions
 * — multiplying a ratio by {@link #HUNDRED} to render it as a percent, or dividing a percent by
 * {@link #HUNDRED} to obtain a fraction. Previously this literal was constructed inline (~20 times)
 * across services and entities.
 *
 * <p>Named {@code HUNDRED} (the literal value) rather than {@code PERCENT}, because it is used for
 * both {@code ×100} and {@code ÷100} and reads correctly in both directions. Money thresholds that
 * merely happen to equal 100 (e.g. a {@code $100} low-balance threshold) are intentionally NOT
 * expressed with this constant — they are semantically distinct and configured separately.
 */
public final class MathConstants {

    /**
     * The value {@code 100}, used for percentage conversions ({@code ratio×100} / {@code pct÷100}).
     */
    public static final BigDecimal HUNDRED = new BigDecimal("100");

    private MathConstants() {
        // Utility class — not instantiable.
    }
}
