package de.com.saenz.minidoodle.infrastructure.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Class-level constraint ensuring {@code startTime} is strictly before {@code endTime}
 * on any {@link Timeframed} request DTO. Null components are ignored here and
 * reported by the individual {@code @NotNull} constraints instead.
 */
@Documented
@Constraint(validatedBy = ValidTimeframeValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimeframe {

    String message() default "Start time must be strictly before end time";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
