package de.com.saenz.minidoodle.infrastructure.api.model;

import de.com.saenz.minidoodle.domain.model.SlotStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Representation of a user's time slot")
public record TimeSlotResponse(
        @Schema(description = "Unique identifier of the time slot", example = "10")
        Long id,

        @Schema(description = "Lightweight user reference who owns this slot")
        UserSummaryResponse user,

        @Schema(description = "Start timestamp in ISO-8601 UTC format", example = "2026-11-15T09:00:00Z")
        Instant startTime,

        @Schema(description = "End timestamp in ISO-8601 UTC format", example = "2026-11-15T10:00:00Z")
        Instant endTime,

        @Schema(description = "Availability status of the slot (FREE or BUSY)", example = "FREE")
        SlotStatus status
) {
}
