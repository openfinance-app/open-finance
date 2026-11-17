package org.openfinance.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.dto.UserSettingsUpdateRequest;

class TimezoneValidationTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"UTC", "Europe/Paris", "America/New_York", "Asia/Kathmandu"})
    void acceptsOmittedOrValidRegionalTimezones(String timezone) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request(timezone))).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Invalid/Timezone", " UTC", "+02:00", "GMT+02:00"})
    void rejectsInvalidOrFixedOffsetTimezones(String timezone) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request(timezone)))
                    .anySatisfy(
                            violation ->
                                    assertThat(violation.getPropertyPath().toString())
                                            .isEqualTo("timezone"));
        }
    }

    private UserSettingsUpdateRequest request(String timezone) {
        return new UserSettingsUpdateRequest(
                null, null, null, null, timezone, null, null, null, null, null, false);
    }
}
