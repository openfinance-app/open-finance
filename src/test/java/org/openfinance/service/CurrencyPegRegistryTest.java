package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(
            strings = {
                "self",
                "zero-source",
                "negative-source",
                "zero-anchor",
                "negative-anchor",
                "no-start",
                "zero-duration",
                "reverse-duration",
                "no-authority",
                "http-source"
            })
    @DisplayName("A parity must have positive units and a documented nonempty validity interval")
    void rejectsInvalidParityDefinitions(String problem) {
        String anchor = problem.equals("self") ? "AAA" : "BBB";
        BigDecimal sourceUnits =
                problem.equals("zero-source")
                        ? BigDecimal.ZERO
                        : problem.equals("negative-source")
                                ? BigDecimal.ONE.negate()
                                : BigDecimal.ONE;
        BigDecimal anchorUnits =
                problem.equals("zero-anchor")
                        ? BigDecimal.ZERO
                        : problem.equals("negative-anchor")
                                ? BigDecimal.ONE.negate()
                                : BigDecimal.TEN;
        LocalDate from = problem.equals("no-start") ? null : START;
        LocalDate until =
                problem.equals("zero-duration")
                        ? START
                        : problem.equals("reverse-duration") ? START.minusDays(1) : null;
        String authority = problem.equals("no-authority") ? " " : "Test authority";
        String url =
                problem.equals("http-source")
                        ? "http://example.test/parity"
                        : "https://example.test/parity";
        assertThatThrownBy(
                        () ->
                                new CurrencyPegRegistry.Peg(
                                        "AAA",
                                        anchor,
                                        sourceUnits,
                                        anchorUnits,
                                        from,
                                        until,
                                        authority,
                                        url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPartialOverlapButKeepsAGapInTheParityHistoryUnpegged() {
        CurrencyPegRegistry.Peg first = peg("AAA", "BBB", START.plusYears(2));
        CurrencyPegRegistry.Peg overlapping =
                new CurrencyPegRegistry.Peg(
                        "AAA",
                        "CCC",
                        BigDecimal.ONE,
                        BigDecimal.TEN,
                        START.plusYears(1),
                        START.plusYears(3),
                        "Authority",
                        "https://example.test/parity");
        assertThatThrownBy(() -> new CurrencyPegRegistry(List.of(first, overlapping)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Overlapping");
        CurrencyPegRegistry.Peg later =
                new CurrencyPegRegistry.Peg(
                        "AAA",
                        "CCC",
                        BigDecimal.ONE,
                        BigDecimal.TEN,
                        START.plusYears(3),
                        null,
                        "Authority",
                        "https://example.test/parity");
        CurrencyPegRegistry registry = new CurrencyPegRegistry(List.of(later, first));
        assertThat(registry.resolve("AAA", START.plusYears(2)).currency()).isEqualTo("AAA");
        assertThat(registry.resolve("AAA", START.plusYears(3)).currency()).isEqualTo("CCC");
        assertThat(registry.entries()).containsExactly(first, later);
    }
}
