package org.openfinance.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single source of the <em>principal leg</em> computation shared by {@code TransactionService}
 * (write-path balance deltas), {@code NetWorthService} (historical balance reconstruction) and
 * {@code LiabilityTrancheService} (tranche allocation).
 *
 * <p>The principal leg of a liability-linked movement is its total minus the sum of the split
 * amounts that carry a {@code categoryId} (interest / insurance / fee legs), floored at zero. A
 * plain repayment without splits is entirely principal; a fully categorized (interest-only) payment
 * has no principal leg.
 */
public final class PrincipalLegs {

    /** Intermediate scale used when converting split legs across the stored conversion rate. */
    private static final int CONVERSION_SCALE = 18;

    private PrincipalLegs() {}

    /**
     * Computes the principal leg from a pre-reduced categorized sum.
     *
     * @param total the movement's total amount ({@code null} resolves to zero)
     * @param categorizedSum the sum of the movement's categorized split amounts ({@code null}
     *     resolves to zero)
     * @return {@code max(total − categorizedSum, 0)}, never negative
     */
    public static BigDecimal of(BigDecimal total, BigDecimal categorizedSum) {
        BigDecimal safeTotal = total != null ? total : BigDecimal.ZERO;
        BigDecimal safeCategorized = categorizedSum != null ? categorizedSum : BigDecimal.ZERO;
        return safeTotal.subtract(safeCategorized).max(BigDecimal.ZERO);
    }

    /**
     * Principal leg of a <em>converted</em> movement (Task 9 FX legs).
     *
     * <p>The stored convention: {@code total} is expressed in the linked instrument's currency
     * ({@code originalAmount}, e.g. the liability currency) while the categorized splits are
     * expressed in the account currency. Each categorized split is converted to the instrument
     * currency by dividing by the stored rate ({@code 1 originalCurrency = rate × currency}), then
     * the plain subtraction applies. Conversion retains the same 18 fractional digits accepted by
     * the transaction API, including fractional crypto principal.
     *
     * @param total the movement total in the linked instrument's currency
     * @param categorizedSum the sum of categorized split amounts in the account currency
     * @param conversionRate the stored conversion rate, strictly positive
     * @return {@code max(total − categorizedSum / rate, 0)} at transaction precision
     * @throws IllegalArgumentException if {@code conversionRate} is null or not strictly positive.
     *     Unreachable from request paths (rates are validated positive at write time and callers
     *     only invoke this method with a non-null stored rate); the guard protects against silently
     *     dividing by a default of 1 on corrupt data.
     */
    public static BigDecimal ofConverted(
            BigDecimal total, BigDecimal categorizedSum, BigDecimal conversionRate) {
        if (conversionRate == null || conversionRate.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Conversion rate must be strictly positive, got: " + conversionRate);
        }
        BigDecimal converted =
                categorizedSum != null
                        ? categorizedSum.divide(
                                conversionRate, CONVERSION_SCALE, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
        return of(total, converted);
    }
}
