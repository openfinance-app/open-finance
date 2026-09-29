package org.openfinance.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;

/** Validates model data as unverified estimates, never as authoritative rates or prices. */
public final class AIInsightDataValidator {
    private AIInsightDataValidator() {}

    public static BigDecimal number(JsonNode data, String field, BigDecimal min, BigDecimal max) {
        try {
            String raw = data.path(field).asText();
            if (raw.length() > 32) throw invalid();
            BigDecimal value = new BigDecimal(raw);
            if (value.scale() > 8 || value.compareTo(min) < 0 || value.compareTo(max) > 0)
                throw invalid();
            return value;
        } catch (NumberFormatException ex) {
            throw invalid();
        }
    }

    public static String provenance(
            JsonNode data, String currency, String country, int maxAgeDays) {
        try {
            if (!currency.equals(data.path("currency").asText())) throw invalid();
            if (country != null && !country.equals(data.path("country").asText())) throw invalid();
            LocalDate asOf = LocalDate.parse(data.path("asOf").asText());
            if (asOf.isAfter(LocalDate.now())
                    || asOf.isBefore(LocalDate.now().minusDays(maxAgeDays))) throw invalid();
            String source = data.path("sourceUrl").asText();
            URI uri = URI.create(source);
            if (source.length() > 300
                    || !"https".equals(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) throw invalid();
            return source;
        } catch (IllegalArgumentException | java.time.DateTimeException ex) {
            throw invalid();
        }
    }

    public static AIProviderException invalid() {
        return new AIProviderException(
                "Insights", "Model returned invalid or unsourced estimate data");
    }
}
