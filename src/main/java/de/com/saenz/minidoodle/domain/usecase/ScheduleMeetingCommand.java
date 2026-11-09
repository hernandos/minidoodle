package de.com.saenz.minidoodle.domain.usecase;

import java.time.Instant;
import java.util.List;

/**
 * Command payload for scheduling a meeting.
 */
public record ScheduleMeetingCommand(
        String title,
        String description,
        Long organizerId,
        List<Long> participantIds,
        Instant startTime,
        Instant endTime
) {
    public ScheduleMeetingCommand {
        if (participantIds == null) {
            participantIds = List.of();
        }
        if (participantIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Participant IDs must not contain null elements");
        }
    }
}
