package de.com.saenz.minidoodle.infrastructure.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Representation of a user's personal availability view")
public record UserAvailabilityResponse(
        @Schema(description = "User who owns the availability")
        UserResponse user,

        @Schema(description = "List of time slots ordered chronologically")
        List<TimeSlotResponse> timeSlots
) {
}
