package de.com.saenz.minidoodle.infrastructure.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that a {@link Timeframed} request has startTime strictly before endTime.
 * Returns true when either component is null so that dedicated null constraints
 * produce the field-level error instead.
 */
public class ValidTimeframeValidator implements ConstraintValidator<ValidTimeframe, Object> {

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (!(value instanceof Timeframed timeframed)) {
            return true;
        }
        if (timeframed.startTime() == null || timeframed.endTime() == null) {
            return true;
        }
        return timeframed.startTime().isBefore(timeframed.endTime());
    }
}
