package de.com.saenz.minidoodle.domain.model;

import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure domain entity representing a scheduled meeting.
 * Created when converting available FREE slots of participants into booked BUSY events.
 * Meetings inherit the timeframe of the consumed slots and cannot have an independent duration.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class Meeting {
    private Long id;
    private String title;
    private String description;
    private User organizer;

    @Builder.Default
    private List<TimeSlot> timeSlots = new ArrayList<>();

    Instant getStartTime() {
        if (timeSlots == null || timeSlots.isEmpty()) {
            return null;
        }
        return timeSlots.getFirst().getStartTime();
    }

    Instant getEndTime() {
        if (timeSlots == null || timeSlots.isEmpty()) {
            return null;
        }
        return timeSlots.getFirst().getEndTime();
    }

    List<User> getParticipants() {
        if (timeSlots == null) {
            return Collections.emptyList();
        }
        return timeSlots.stream()
                        .map(TimeSlot::getUser)
                        .filter(u -> u != null && (organizer == null || !u.getId().equals(organizer.getId())))
                        .distinct()
                        .toList();
    }

    void addConsumedSlot(TimeSlot slot) {
        if (slot == null) {
            throw new IllegalArgumentException("Consumed time slot cannot be null");
        }
        if (timeSlots == null) {
            timeSlots = new ArrayList<>();
        }
        if (!timeSlots.isEmpty()) {
            TimeSlot first = timeSlots.getFirst();
            if (!first.matchesTimeframe(slot.getStartTime(), slot.getEndTime())) {
                throw new IllegalArgumentException("All consumed slots in a meeting must match the exact same timeframe");
            }
        }
        timeSlots.add(slot);
    }
}
