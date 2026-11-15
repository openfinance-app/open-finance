package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.exception.InvalidSimulationException;

@DisplayName("Saved simulation input boundaries")
class SimulationInputBoundaryTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RealEstateSimulationValidator validator =
            new RealEstateSimulationValidator(mapper);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n", "true", "5", "null", "[]", "{} {}"})
    void rejectsMissingOrNonObjectPayloads(String value) {
        assertThatThrownBy(() -> validator.validate("buy_rent", value))
                .isInstanceOf(InvalidSimulationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"BUY_RENT", "rental", "unknown"})
    void refusesUnsupportedSimulationTypes(String type) throws Exception {
        String data = fixture("buy-rent").toString();
        assertThatThrownBy(() -> validator.validate(type, data))
                .isInstanceOf(InvalidSimulationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "5", "\"true\"", "[]", "{}"})
    void propertyConditionMustBeBoolean(String value) throws Exception {
        ObjectNode data = fixture("buy-rent");
        ((ObjectNode) data.get("purchase")).set("isNewProperty", mapper.readTree(value));
        assertThatThrownBy(() -> validator.validate("buy_rent", data.toString()))
                .isInstanceOf(InvalidSimulationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"usd\"", "\"US\"", "\"USDD\"", "5", "null", "{}"})
    void buyRentCurrencyMustBeThreeUppercaseLetters(String currency) throws Exception {
        ObjectNode data = fixture("buy-rent");
        data.set("currency", mapper.readTree(currency));
        assertThatThrownBy(() -> validator.validate("buy_rent", data.toString()))
                .isInstanceOf(InvalidSimulationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EUR", "USD", "CHF"})
    void acceptsBuyRentCurrencyAndBothPropertyConditions(String currency) throws Exception {
        ObjectNode data = fixture("buy-rent");
        data.put("currency", currency);
        for (boolean isNew : new boolean[] {true, false}) {
            ((ObjectNode) data.get("purchase")).put("isNewProperty", isNew);
            assertThatCode(() -> validator.validate("buy_rent", data.toString()))
                    .doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"unfurnished", "basic", "standard", "luxury"})
    void acceptsSupportedFurnishingAndExplicitHouseholdIncome(String furnishing) throws Exception {
        ObjectNode data = fixture("rental-investment");
        ((ObjectNode) data.get("property")).put("furnishingType", furnishing);
        data.put("currency", "EUR");
        data.putObject("tax")
                .put("incomeYear", 2026)
                .put("otherHouseholdIncome", 45000)
                .put("otherFurnishedReceipts", 0)
                .put("otherUnfurnishedRent", 0);
        assertThatCode(() -> validator.validate("rental_investment", data.toString()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "", "BASIC"})
    void rejectsUnsupportedFurnishing(String furnishing) throws Exception {
        ObjectNode data = fixture("rental-investment");
        ((ObjectNode) data.get("property")).put("furnishingType", furnishing);
        assertThatThrownBy(() -> validator.validate("rental_investment", data.toString()))
                .isInstanceOf(InvalidSimulationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"USD\"", "\"eur\"", "null", "5"})
    void frenchRentalTaxSimulationRejectsOtherCurrencies(String currency) throws Exception {
        ObjectNode data = fixture("rental-investment");
        data.set("currency", mapper.readTree(currency));
        assertThatThrownBy(() -> validator.validate("rental_investment", data.toString()))
                .isInstanceOf(InvalidSimulationException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "null",
                "[]",
                "{\"incomeYear\":2026,\"otherFurnishedReceipts\":0,\"otherUnfurnishedRent\":0}",
                "{\"incomeYear\":2026,\"otherFurnishedReceipts\":0,\"otherUnfurnishedRent\":0,\"otherHouseholdIncome\":\"45000\"}"
            })
    void partialTaxContextIsNotSilentlyReplacedWithZero(String tax) throws Exception {
        ObjectNode data = fixture("rental-investment");
        data.set("tax", mapper.readTree(tax));
        assertThatThrownBy(() -> validator.validate("rental_investment", data.toString()))
                .isInstanceOf(InvalidSimulationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.7976931348623159E308", "-1.7976931348623159E308", "1E999", "-1E999"})
    void rejectsValuesThatOverflowTheBrowser(String value) throws Exception {
        ObjectNode data = fixture("buy-rent");
        ((ObjectNode) data.get("purchase")).put("propertyPrice", new BigDecimal(value));
        assertThatThrownBy(() -> validator.validate("buy_rent", data.toString()))
                .isInstanceOf(InvalidSimulationException.class);
    }

    @Test
    void numericValidationDoesNotRejectEditableZeroAndNegativeInputs() throws Exception {
        ObjectNode data = fixture("buy-rent");
        ((ObjectNode) data.get("purchase")).put("propertyPrice", BigDecimal.ZERO);
        ((ObjectNode) data.get("market")).put("priceEvolution", new BigDecimal("-2.75"));
        assertThatCode(() -> validator.validate("buy_rent", data.toString()))
                .doesNotThrowAnyException();
    }

    private ObjectNode fixture(String name) throws Exception {
        return (ObjectNode)
                mapper.readTree(
                        Files.readString(
                                Path.of(
                                        "src/test/resources/fixtures/"
                                                + name
                                                + "-simulation.json")));
    }
}
