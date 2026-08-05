package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyAllocationTest {

    private static BigDecimal sum(List<BigDecimal> parts) {
        return parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("reconcile: 33.33 x3 for 100.00 becomes 33.34 + 33.33 + 33.33 (exact)")
    void reconcileThreeWaySplit() {
        List<BigDecimal> parts =
                List.of(new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.33"));
        MoneyAllocation.ReconcileResult result =
                MoneyAllocation.reconcile(new BigDecimal("100.00"), parts, 2);

        assertThat(result.grossMismatch()).isFalse();
        assertThat(result.parts())
                .containsExactly(
                        new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
        assertThat(sum(result.parts())).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("reconcile: negative residue is absorbed by the largest line")
    void reconcileNegativeResidue() {
        List<BigDecimal> parts = List.of(new BigDecimal("50.01"), new BigDecimal("50.00"));
        MoneyAllocation.ReconcileResult result =
                MoneyAllocation.reconcile(new BigDecimal("100.00"), parts, 2);

        assertThat(result.grossMismatch()).isFalse();
        assertThat(sum(result.parts())).isEqualByComparingTo("100.00");
        assertThat(result.parts().get(0)).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("reconcile: residue larger than parts count is a gross mismatch (unchanged parts)")
    void reconcileGrossMismatch() {
        List<BigDecimal> parts = List.of(new BigDecimal("50.00"), new BigDecimal("48.00"));
        MoneyAllocation.ReconcileResult result =
                MoneyAllocation.reconcile(new BigDecimal("100.00"), parts, 2);

        assertThat(result.grossMismatch()).isTrue();
        assertThat(result.parts()).isEqualTo(parts);
    }

    @Test
    @DisplayName("reconcile: within-bound residue that cannot be placed below the minimum is gross")
    void reconcileUnplaceableResidueIsGross() {
        // total 100.00 vs [0.01, 100.01] = residue -0.02 (within bound of 2 units), but the only
        // way to remove 2 units would push the 0.01 line below one minor unit -> gross, unchanged.
        List<BigDecimal> parts = List.of(new BigDecimal("0.01"), new BigDecimal("100.01"));
        MoneyAllocation.ReconcileResult result =
                MoneyAllocation.reconcile(new BigDecimal("100.00"), parts, 2);

        assertThat(result.grossMismatch()).isTrue();
        assertThat(result.parts()).isEqualTo(parts);
    }

    @Test
    @DisplayName("reconcile: at the bound (residue == parts) is NOT gross")
    void reconcileAtBound() {
        // total 100.00, splits 33.34/33.34/33.34 = 100.02, residue -0.02 (2 units <= 3 parts)
        List<BigDecimal> parts =
                List.of(new BigDecimal("33.34"), new BigDecimal("33.34"), new BigDecimal("33.34"));
        MoneyAllocation.ReconcileResult result =
                MoneyAllocation.reconcile(new BigDecimal("100.00"), parts, 2);

        assertThat(result.grossMismatch()).isFalse();
        assertThat(sum(result.parts())).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("reconcile: zero-decimal currency (scale 0) sums exactly")
    void reconcileZeroDecimal() {
        List<BigDecimal> parts =
                List.of(new BigDecimal("33"), new BigDecimal("33"), new BigDecimal("33"));
        MoneyAllocation.ReconcileResult result =
                MoneyAllocation.reconcile(new BigDecimal("100"), parts, 0);

        assertThat(result.grossMismatch()).isFalse();
        assertThat(sum(result.parts())).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("reconcile property: within-bound results always sum exactly to total")
    void reconcilePropertyExactSum() {
        Random rnd = new Random(42);
        for (int i = 0; i < 500; i++) {
            int n = 2 + rnd.nextInt(9);
            BigDecimal total = new BigDecimal(100 + rnd.nextInt(9000)).movePointLeft(2);
            // even split rounded down per line -> residue within bound
            BigDecimal share = total.divide(new BigDecimal(n), 2, RoundingMode.DOWN);
            List<BigDecimal> parts = new ArrayList<>();
            for (int k = 0; k < n; k++) parts.add(share);
            MoneyAllocation.ReconcileResult result = MoneyAllocation.reconcile(total, parts, 2);
            assertThat(result.grossMismatch()).isFalse();
            assertThat(sum(result.parts())).isEqualByComparingTo(total);
        }
    }
}
