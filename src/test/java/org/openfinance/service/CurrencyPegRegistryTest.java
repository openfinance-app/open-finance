package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CurrencyPegRegistryTest {
    private static final LocalDate START = LocalDate.of(2000, 1, 1);

    @Test
    void rejectsOverlapsAndDatedCyclesInsteadOfSilentlyChoosingAParity() {
        CurrencyPegRegistry.Peg first = peg("AAA", "BBB", null);
        assertThatThrownBy(() -> new CurrencyPegRegistry(List.of(first, first)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Overlapping");
        assertThatThrownBy(() -> new CurrencyPegRegistry(List.of(first, peg("BBB", "AAA", null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cyclic");
    }

    @Test
    void adjacentRegimesAreSelectedWithoutCrossingTheirDateBoundaries() {
        LocalDate change = START.plusYears(1);
        CurrencyPegRegistry registry =
                new CurrencyPegRegistry(
                        List.of(
                                peg("AAA", "BBB", change),
                                new CurrencyPegRegistry.Peg(
                                        "AAA",
                                        "CCC",
                                        BigDecimal.ONE,
                                        BigDecimal.TEN,
                                        change,
                                        null,
                                        "Test authority",
                                        "https://example.test/parity")));
        assertThat(registry.resolve("AAA", change.minusDays(1)).currency()).isEqualTo("BBB");
        assertThat(registry.resolve("AAA", change).currency()).isEqualTo("CCC");
        assertThat(registry.resolve("AAA", START.minusDays(1)).currency()).isEqualTo("AAA");
    }

    private CurrencyPegRegistry.Peg peg(String currency, String anchor, LocalDate until) {
        return new CurrencyPegRegistry.Peg(
                currency,
                anchor,
                BigDecimal.ONE,
                BigDecimal.ONE,
                START,
                until,
                "Test authority",
                "https://example.test/parity");
    }
}
