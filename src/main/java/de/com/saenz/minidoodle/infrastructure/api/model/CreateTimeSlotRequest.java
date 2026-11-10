package de.com.saenz.minidoodle.infrastructure.api.model;

import de.com.saenz.minidoodle.infrastructure.api.validation.Timeframed;
import de.com.saenz.minidoodle.infrastructure.api.validation.ValidTimeframe;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@ValidTimeframe
@Schema(description = "Request payload to create a new proactively available time slot for a user")
public record CreateTimeSlotRequest(
        @NotNull(message = "User ID must not be null")
        @Schema(description = "ID of the user creating the slot", example = "1")
        Long userId,

        @NotNull(message = "Start time must not be null")
        @Schema(description = "Start timestamp in ISO-8601 UTC format", example = "2026-11-15T09:00:00Z")
        Instant startTime,

        @NotNull(message = "End time must not be null")
        @Schema(description = "End timestamp in ISO-8601 UTC format", example = "2026-11-15T10:00:00Z")
        Instant endTime
) implements Timeframed {
}
