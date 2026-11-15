package org.openfinance.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName(
        "Unverified insight estimates require bounded exact numbers and recent attributable sources")
class AIInsightDataValidatorTest {
    private final ObjectMapper json = new ObjectMapper();

    private ObjectNode source() {
        return json.createObjectNode()
                .put("currency", "EUR")
                .put("country", "FR")
                .put("asOf", LocalDate.now().toString())
                .put("sourceUrl", "https://example.test/rates?year=2025");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "-0.00000001",
                "100.00000001",
                "0.000000001",
                "NaN",
                "Infinity",
                "",
                " 1 ",
                "100000000000000000000000000000000",
                "1e999999",
                "true"
            })
    void rejectsOutOfRangeOrAmbiguousModelNumbers(String value) {
        ObjectNode data = json.createObjectNode().put("rate", value);
        assertThatThrownBy(
                        () ->
                                AIInsightDataValidator.number(
                                        data, "rate", BigDecimal.ZERO, new BigDecimal("100")))
                .isInstanceOf(AIProviderException.class)
                .hasMessageContaining("invalid or unsourced");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "100", "99.99999999", "1E+2"})
    void acceptsInclusiveBoundsWithoutPassingThroughBinaryFloatingPoint(String value) {
        assertThat(
                        AIInsightDataValidator.number(
                                json.createObjectNode().put("rate", value),
                                "rate",
                                BigDecimal.ZERO,
                                new BigDecimal("100")))
                .isEqualByComparingTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "null", "object", "array"})
    void rejectsNonScalarOrAbsentNumericFields(String type) {
        ObjectNode data = json.createObjectNode();
        switch (type) {
            case "null" -> data.putNull("rate");
            case "object" -> data.putObject("rate");
            case "array" -> data.putArray("rate");
            default -> {}
        }
        assertThatThrownBy(
                        () ->
                                AIInsightDataValidator.number(
                                        data, "rate", BigDecimal.ZERO, BigDecimal.TEN))
                .isInstanceOf(AIProviderException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "wrong-currency",
                "wrong-country",
                "future",
                "stale",
                "bad-date",
                "http",
                "relative",
                "no-host",
                "credentials",
                "fragment",
                "too-long",
                "invalid-uri"
            })
    void rejectsUnavailableOrMisattributedProvenance(String invalid) {
        ObjectNode data = source();
        switch (invalid) {
            case "wrong-currency" -> data.put("currency", "USD");
            case "wrong-country" -> data.put("country", "US");
            case "future" -> data.put("asOf", LocalDate.now().plusDays(1).toString());
            case "stale" -> data.put("asOf", LocalDate.now().minusDays(91).toString());
            case "bad-date" -> data.put("asOf", "2025-02-30");
            case "http" -> data.put("sourceUrl", "http://example.test/rates");
            case "relative" -> data.put("sourceUrl", "/rates");
            case "no-host" -> data.put("sourceUrl", "https:/rates");
            case "credentials" -> data.put("sourceUrl", "https://alice:secret@example.test/rates");
            case "fragment" -> data.put("sourceUrl", "https://example.test/rates#invented-source");
            case "too-long" -> data.put("sourceUrl", "https://example.test/" + "x".repeat(300));
            case "invalid-uri" -> data.put("sourceUrl", "https://not a host/rates");
            default -> throw new AssertionError(invalid);
        }
        assertThatThrownBy(() -> AIInsightDataValidator.provenance(data, "EUR", "FR", 90))
                .isInstanceOf(AIProviderException.class);
    }

    @ParameterizedTest
    @CsvSource({"0", "90"})
    void acceptsBothInclusiveEndsOfTheSourceFreshnessWindow(int age) {
        ObjectNode data = source().put("asOf", LocalDate.now().minusDays(age).toString());
        assertThat(AIInsightDataValidator.provenance(data, "EUR", "FR", 90))
                .isEqualTo("https://example.test/rates?year=2025");
    }

    @Test
    void aNonregionalOfferStillRequiresTheRequestedCurrencyButNotACountry() {
        ObjectNode data = source();
        data.remove("country");
        assertThat(AIInsightDataValidator.provenance(data, "EUR", null, 90))
                .isEqualTo("https://example.test/rates?year=2025");
    }
}
