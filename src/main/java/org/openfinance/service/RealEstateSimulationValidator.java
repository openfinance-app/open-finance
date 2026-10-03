package org.openfinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.math.BigDecimal;
import java.util.List;
import org.openfinance.exception.InvalidSimulationException;
import org.springframework.stereotype.Component;

/**
 * Checks persisted input shapes; editable numeric ranges remain the calculators' responsibility.
 */
@Component
public class RealEstateSimulationValidator {
    private static final BigDecimal MAX_BROWSER_NUMBER = new BigDecimal("1.7976931348623157E308");
    private final ObjectReader reader;

    public RealEstateSimulationValidator(ObjectMapper objectMapper) {
        reader = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public void validate(String type, String data) {
        if (data == null || data.isBlank()) throw new InvalidSimulationException();
        try {
            JsonNode root = reader.readTree(data);
            if (root == null || !root.isObject()) throw new InvalidSimulationException();
            if ("buy_rent".equals(type)) validateBuyRent(root);
            else if ("rental_investment".equals(type)) validateRental(root);
            else throw new InvalidSimulationException();
        } catch (JsonProcessingException | ArithmeticException ex) {
            throw new InvalidSimulationException();
        }
    }

    private void validateBuyRent(JsonNode root) {
        numbers(
                root,
                "purchase",
                "propertyPrice renovationAmount notaryFeesPercent agencyFees downPayment loanDuration interestRate totalInsurance applicationFees guaranteeFees accountFees propertyTax coOwnershipCharges maintenancePercent homeInsurance bankFees garbageTax");
        if (!root.path("purchase").path("isNewProperty").isBoolean())
            throw new InvalidSimulationException();
        numbers(
                root,
                "rental",
                "monthlyRent monthlyCharges securityDeposit rentalInsurance garbageTax initialSavings monthlySavings");
        numbers(root, "market", "priceEvolution rentEvolution investmentReturn inflation");
        numbers(root, "resale", "targetYear desiredProfit resaleFeesPercent");
        if (root.has("currency")
                && (!root.get("currency").isTextual()
                        || !root.get("currency").asText().matches("[A-Z]{3}")))
            throw new InvalidSimulationException();
    }

    private void validateRental(JsonNode root) {
        numbers(root, "credit", "monthlyPayment annualCost totalCost assurance bankFees");
        numbers(root, "property", "totalPrice furnitureValue");
        if (!List.of("unfurnished", "basic", "standard", "luxury")
                .contains(root.path("property").path("furnishingType").asText()))
            throw new InvalidSimulationException();
        numbers(root, "revenue", "monthlyRent recoverableCharges occupancyRate badDebtRate");
        numbers(
                root,
                "expenses",
                "propertyTax nonRecoverableCharges annualMaintenance cfe cvae managementFees pnoInsurance accountingFees marginalTaxRate");
        if (root.has("currency") && !"EUR".equals(root.get("currency").asText()))
            throw new InvalidSimulationException();
        // Older saves predate the currency and household tax context fields.
        if (root.has("tax")) {
            numbers(root, "tax", "incomeYear otherFurnishedReceipts otherUnfurnishedRent");
            JsonNode income = root.path("tax").path("otherHouseholdIncome");
            if (!income.isNull()) number(income);
        }
    }

    private void numbers(JsonNode root, String section, String fields) {
        JsonNode group = root.path(section);
        if (!group.isObject()) throw new InvalidSimulationException();
        for (String field : fields.split(" ")) number(group.path(field));
    }

    private void number(JsonNode value) {
        if (!value.isNumber()
                || value.isFloatingPointNumber() && value.asText().matches(".*(Infinity|NaN).*")) {
            throw new InvalidSimulationException();
        }
        if (value.decimalValue().abs().compareTo(MAX_BROWSER_NUMBER) > 0)
            throw new InvalidSimulationException();
    }
}
