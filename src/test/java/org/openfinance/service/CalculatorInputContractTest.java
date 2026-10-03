package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.calculator.CompoundInterestRequest;

class CalculatorInputContractTest {
    @Test
    void zeroStartAndZeroInterestAreValidButFractionalTermsAreNeverTruncated() throws Exception {
        ObjectMapper json = new ObjectMapper();
        String input =
                "{\"principal\":0,\"annualRate\":0,\"years\":1,\"compoundingFrequency\":12,\"regularContribution\":100}";
        CompoundInterestRequest request = json.readValue(input, CompoundInterestRequest.class);
        try (jakarta.validation.ValidatorFactory factory =
                Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
        assertThatThrownBy(
                        () ->
                                json.readValue(
                                        input.replace("\"years\":1", "\"years\":1.5"),
                                        CompoundInterestRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        assertThatThrownBy(
                        () ->
                                json.readValue(
                                        input.replace(
                                                "\"compoundingFrequency\":12",
                                                "\"compoundingFrequency\":12.5"),
                                        CompoundInterestRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }
}
