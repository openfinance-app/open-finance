package org.openfinance.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Exact allocation / reconciliation of monetary amounts using the largest-remainder method.
 *
 * <p>All arithmetic is performed in integer minor units ({@code 10^-scale}) so results always sum
 * EXACTLY to the target total. This replaces the previous ±0.01 split-sum tolerance: instead of
 * tolerating an imbalance, the residue is distributed one minor unit at a time.
 *
 * <p>A residue larger than {@code parts} minor units (one unit per line) cannot be explained by
 * per-line rounding and is reported as a {@link ReconcileResult#grossMismatch()}. A gross mismatch
 * is also reported when a residue within that bound cannot be placed without pushing a line below
 * one minor unit.
 */
public final class MoneyAllocation {

    private MoneyAllocation() {}

    /**
     * Outcome of {@link #reconcile}. When {@code grossMismatch} is true, {@code parts} is the
     * untouched input.
     */
    public record ReconcileResult(boolean grossMismatch, List<BigDecimal> parts) {}

    /**
     * Adjusts {@code parts} minimally so they sum EXACTLY to {@code total} at the given {@code
     * scale}.
     *
     * <p>Precondition: each element of {@code parts} is expected to be a positive amount of at
     * least one minor unit. The largest-absolute-value ordering and the one-minor-unit floor guard
     * assume positive parts, which holds for transaction splits.
     *
     * @param total the authoritative total the parts must sum to
     * @param parts the split amounts to reconcile (not mutated); each expected to be a positive
     *     amount of at least one minor unit
     * @param scale the minor-unit scale (e.g. 2 for USD, 0 for JPY)
     */
    public static ReconcileResult reconcile(BigDecimal total, List<BigDecimal> parts, int scale) {
        BigInteger totalUnits = toUnits(total, scale);
        BigInteger[] units = new BigInteger[parts.size()];
        BigInteger partsSum = BigInteger.ZERO;
        for (int i = 0; i < parts.size(); i++) {
            units[i] = toUnits(parts.get(i), scale);
            partsSum = partsSum.add(units[i]);
        }
        BigInteger residue = totalUnits.subtract(partsSum);

        if (residue.abs().compareTo(BigInteger.valueOf(parts.size())) > 0) {
            return new ReconcileResult(true, parts);
        }

        // Order line indices by largest absolute value first (least proportional distortion),
        // tie-broken by index for determinism.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < units.length; i++) {
            order.add(i);
        }
        final BigInteger[] snapshot = units.clone();
        order.sort(
                Comparator.<Integer, BigInteger>comparing(i -> snapshot[i].abs().negate())
                        .thenComparingInt(i -> i));

        BigInteger step = BigInteger.valueOf(residue.signum());
        for (int idx : order) {
            if (residue.signum() == 0) {
                break;
            }
            BigInteger next = units[idx].add(step);
            if (next.signum() > 0) { // never push a line below one minor unit
                units[idx] = next;
                residue = residue.subtract(step);
            }
        }
        if (residue.signum() != 0) {
            // Could not place the residue without violating the per-line minimum.
            return new ReconcileResult(true, parts);
        }

        List<BigDecimal> result = new ArrayList<>(units.length);
        for (BigInteger u : units) {
            if (u.signum() <= 0) {
                return new ReconcileResult(true, parts);
            }
            result.add(new BigDecimal(u, scale));
        }
        return new ReconcileResult(false, result);
    }

    private static BigInteger toUnits(BigDecimal value, int scale) {
        return value.movePointRight(scale).setScale(0, RoundingMode.HALF_UP).toBigIntegerExact();
    }
}
