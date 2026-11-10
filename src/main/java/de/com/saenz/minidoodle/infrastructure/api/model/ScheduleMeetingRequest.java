package de.com.saenz.minidoodle.infrastructure.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

@Schema(description = "Request payload to schedule a meeting with participants")
public record ScheduleMeetingRequest(
        @NotBlank(message = "Title must not be blank")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        @Schema(description = "Title of the meeting", example = "Sprint Retrospective")
        String title,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        @Schema(description = "Optional detailed description of the meeting agenda",
                example = "Reviewing Q4 deliverables and retrospective actions")
        String description,

        @NotNull(message = "Organizer ID must not be null")
        @Schema(description = "ID of the user organizing the meeting", example = "1")
        Long organizerId,

        @Schema(description = "List of invited participant user IDs. Each must have a matching FREE slot",
                example = "[2, 3]")
        List<Long> participantIds,

        @NotNull(message = "Start time must not be null")
        @Schema(description = "Meeting start timestamp in ISO-8601 UTC format", example = "2026-11-15T14:00:00Z")
        Instant startTime,

        @NotNull(message = "End time must not be null")
        @Schema(description = "Meeting end timestamp in ISO-8601 UTC format", example = "2026-11-15T15:00:00Z")
        Instant endTime
) {
}
