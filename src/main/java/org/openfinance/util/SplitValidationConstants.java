package org.openfinance.util;

import java.math.BigDecimal;

/**
 * Shared constants for transaction-split amount validation.
 *
 * <p>This class is the single source of truth for the tolerance applied when checking that split
 * line amounts sum to their parent transaction amount. Both the import parsers (e.g. {@code
 * QifParser}) and the API-layer {@code TransactionSplitService} reference the same value so their
 * validation can never diverge.
 *
 * <p><strong>Why {@code 0.01} and not a crypto-scale value:</strong> the tolerance must absorb
 * ordinary two-decimal fiat rounding when a total is split into non-divisible parts (e.g. a
 * three-way split of {@code 100.00} into {@code 33.33 + 33.33 + 33.33 = 99.99}). A tighter value
 * such as {@code 0.0001} would reject those legitimate fiat splits. The QIF format carries no
 * currency information, so a per-currency tolerance is not possible at parse time; a single
 * fiat-safe tolerance is therefore applied uniformly.
 */
public final class SplitValidationConstants {

    /**
     * Tolerance (±{@value #SPLIT_SUM_TOLERANCE}) within which the sum of split amounts must match
     * the parent transaction amount. Absorbs two-decimal fiat rounding differences.
     */
    public static final BigDecimal SPLIT_SUM_TOLERANCE = new BigDecimal("0.01");

    private SplitValidationConstants() {
        // Utility class — not instantiable.
    }
}
