package org.openfinance.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.ZoneId;

public class TimezoneValidator implements ConstraintValidator<ValidTimezone, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || ZoneId.getAvailableZoneIds().contains(value);
    }
}
