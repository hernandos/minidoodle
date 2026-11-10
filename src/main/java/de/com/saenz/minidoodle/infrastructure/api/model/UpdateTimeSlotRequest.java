package de.com.saenz.minidoodle.infrastructure.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Schema(description = "Request payload to update the timeframe of an existing free time slot")
public record UpdateTimeSlotRequest(
        @NotNull(message = "Start time must not be null")
        @Schema(description = "New start timestamp in ISO-8601 UTC format", example = "2026-11-15T10:30:00Z")
        Instant startTime,

        @NotNull(message = "End time must not be null")
        @Schema(description = "New end timestamp in ISO-8601 UTC format", example = "2026-11-15T11:30:00Z")
        Instant endTime
) {
}
