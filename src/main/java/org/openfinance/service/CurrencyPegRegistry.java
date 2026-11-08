package org.openfinance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.openfinance.dto.ExchangeRateLeg;

/**
 * Official, dated fixed parities and irrevocable conversion ratios. The resource includes the
 * primary sources and separately documents arrangements that still require market quotes. Validity
 * intervals are inclusive at the start and exclusive at the end.
 */
public final class CurrencyPegRegistry {
    private static final CurrencyPegRegistry STANDARD = load();
    private final Map<String, List<Peg>> pegs;

    public record Peg(
            String currency,
            String anchor,
            BigDecimal currencyUnits,
            BigDecimal anchorUnits,
            LocalDate validFrom,
            LocalDate validUntil,
            String authority,
            String sourceUrl) {
        public Peg {
            if (currency.equals(anchor)
                    || currencyUnits.signum() <= 0
                    || anchorUnits.signum() <= 0
                    || validFrom == null
                    || (validUntil != null && !validUntil.isAfter(validFrom))
                    || authority.isBlank()
                    || !sourceUrl.startsWith("https://")) {
                throw new IllegalArgumentException("Invalid currency parity for " + currency);
            }
        }

        boolean applies(LocalDate date) {
            return !date.isBefore(validFrom) && (validUntil == null || date.isBefore(validUntil));
        }
    }

    public record Anchor(String currency, BigDecimal rate, List<ExchangeRateLeg> legs) {
        public Anchor {
            legs = List.copyOf(legs);
        }
    }

    CurrencyPegRegistry(List<Peg> entries) {
        pegs = entries.stream().collect(Collectors.groupingBy(Peg::currency));
        pegs.values().forEach(CurrencyPegRegistry::validateIntervals);
        // Every graph change happens at an interval boundary. Check both sides of each boundary.
        Set<LocalDate> boundaries = new HashSet<>();
        for (Peg entry : entries) {
            boundaries.add(entry.validFrom());
            if (entry.validUntil() != null) boundaries.add(entry.validUntil());
        }
        for (LocalDate boundary : boundaries) {
            for (String currency : pegs.keySet()) resolve(currency, boundary);
        }
    }

    public static CurrencyPegRegistry standard() {
        return STANDARD;
    }

    /** Collapse fixed links only; the remaining anchor can itself float within a band. */
    public Anchor resolve(String currency, LocalDate date) {
        String anchor = currency.toUpperCase(Locale.ROOT);
        BigDecimal rate = BigDecimal.ONE;
        List<ExchangeRateLeg> legs = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        while (true) {
            if (!visited.add(anchor))
                throw new IllegalArgumentException("Cyclic currency peg: " + anchor);
            Peg peg =
                    pegs.getOrDefault(anchor, List.of()).stream()
                            .filter(p -> p.applies(date))
                            .findFirst()
                            .orElse(null);
            if (peg == null) return new Anchor(anchor, rate, legs);
            rate =
                    rate.multiply(peg.anchorUnits())
                            .divide(peg.currencyUnits(), MathContext.DECIMAL128);
            legs.add(
                    new ExchangeRateLeg(
                            peg.anchor(),
                            peg.currency(),
                            peg.currencyUnits().divide(peg.anchorUnits(), MathContext.DECIMAL128),
                            date,
                            peg.authority() + " fixed parity",
                            peg.sourceUrl(),
                            peg.validFrom(),
                            peg.validUntil()));
            anchor = peg.anchor();
        }
    }

    public List<Peg> entries() {
        return pegs.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(Peg::currency).thenComparing(Peg::validFrom))
                .toList();
    }

    private static void validateIntervals(List<Peg> entries) {
        entries.sort(Comparator.comparing(Peg::validFrom));
        for (int i = 1; i < entries.size(); i++) {
            Peg previous = entries.get(i - 1);
            if (previous.validUntil() == null
                    || previous.validUntil().isAfter(entries.get(i).validFrom())) {
                throw new IllegalArgumentException(
                        "Overlapping currency pegs: " + previous.currency());
            }
        }
    }

    private static CurrencyPegRegistry load() {
        try (InputStream input =
                CurrencyPegRegistry.class.getResourceAsStream("/currency-pegs.json")) {
            if (input == null) throw new IllegalStateException("Missing currency peg registry");
            JsonNode root = new ObjectMapper().readTree(input);
            List<Peg> entries = new ArrayList<>();
            for (JsonNode node : root.required("fixed")) {
                String until = node.path("validUntil").asText(null);
                entries.add(
                        new Peg(
                                node.required("currency").asText(),
                                node.required("anchor").asText(),
                                new BigDecimal(node.required("currencyUnits").asText()),
                                new BigDecimal(node.required("anchorUnits").asText()),
                                LocalDate.parse(node.required("validFrom").asText()),
                                until == null ? null : LocalDate.parse(until),
                                node.required("authority").asText(),
                                node.required("sourceUrl").asText()));
            }
            return new CurrencyPegRegistry(entries);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read currency peg registry", exception);
        }
    }
}
