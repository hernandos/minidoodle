package de.com.saenz.minidoodle.infrastructure.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Representation of a scheduled meeting")
public record MeetingResponse(
        @Schema(description = "Unique identifier of the meeting", example = "100")
        Long id,

        @Schema(description = "Title of the meeting", example = "Sprint Retrospective")
        String title,

        @Schema(description = "Optional description", example = "Review deliverables")
        String description,

        @Schema(description = "Organizer of the meeting")
        UserResponse organizer,

        @Schema(description = "Inherited meeting start timestamp", example = "2026-11-15T14:00:00Z")
        Instant startTime,

        @Schema(description = "Inherited meeting end timestamp", example = "2026-11-15T15:00:00Z")
        Instant endTime,

        @Schema(description = "Participants invited to the meeting (excluding organizer)")
        List<UserResponse> participants,

        @Schema(description = "All consumed time slots associated with this meeting")
        List<TimeSlotResponse> timeSlots
) {
}
