package de.com.saenz.minidoodle.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure domain entity representing a scheduled meeting.
 * Created when converting available FREE slots of participants into booked BUSY events.
 * Meetings inherit the timeframe of the consumed slots and cannot have an independent duration.
 * <p>
 * Invariant: all consumed slots must match the exact same timeframe.
 * Enforced exclusively through {@link #addConsumedSlot(TimeSlot)}; the slot list is
 * exposed read-only and construction is only possible via the static factories.
 */
@Getter
public class Meeting {
    private final Long id;
    private final String title;
    private final String description;
    private final User organizer;
    private final List<TimeSlot> timeSlots = new ArrayList<>();

    private Meeting(Long id, String title, String description, User organizer) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.organizer = organizer;
    }

    public static Meeting of(String title, String description, User organizer) {
        return new Meeting(null, title, description, organizer);
    }

    public static Meeting reconstitute(Long id,
                                       String title,
                                       String description,
                                       User organizer,
                                       List<TimeSlot> slots) {
        Meeting meeting = new Meeting(id, title, description, organizer);
        if (slots != null) {
            for (TimeSlot slot : slots) {
                meeting.addConsumedSlot(slot);
            }
        }
        return meeting;
    }

    public List<TimeSlot> getTimeSlots() {
        return Collections.unmodifiableList(timeSlots);
    }

    public Instant getStartTime() {
        if (timeSlots == null || timeSlots.isEmpty()) {
            return null;
        }
        return timeSlots.getFirst().getStartTime();
    }

    public Instant getEndTime() {
        if (timeSlots == null || timeSlots.isEmpty()) {
            return null;
        }
        return timeSlots.getFirst().getEndTime();
    }

    public List<User> getParticipants() {
        if (timeSlots == null) {
            return Collections.emptyList();
        }
        return timeSlots.stream()
                        .map(TimeSlot::getUser)
                        .filter(u -> u != null && (organizer == null || !u.getId().equals(organizer.getId())))
                        .distinct()
                        .toList();
    }

    public void addConsumedSlot(TimeSlot slot) {
        if (slot == null) {
            throw new IllegalArgumentException("Consumed time slot cannot be null");
        }
        if (slot.isFree()) {
            throw new IllegalArgumentException("Cannot consume a FREE slot. Slot must be marked as BUSY");
        }
        if (!timeSlots.isEmpty()) {
            TimeSlot first = timeSlots.getFirst();
            if (!first.matchesTimeframe(slot.getStartTime(), slot.getEndTime())) {
                throw new IllegalArgumentException("All consumed slots in a meeting must match the exact same timeframe");
            }
        }
        if (timeSlots.stream().anyMatch(existing ->
                (slot.getId() != null && java.util.Objects.equals(existing.getId(), slot.getId())) || existing.equals(slot))) {
            throw new IllegalArgumentException("Slot is already added to this meeting");
        }
        if (slot.getUser() != null && slot.getUser().getId() != null) {
            boolean userAlreadyPresent = timeSlots.stream()
                    .anyMatch(existing -> existing.getUser() != null && java.util.Objects.equals(existing.getUser().getId(), slot.getUser().getId()));
            if (userAlreadyPresent) {
                throw new IllegalArgumentException("A slot for user " + slot.getUser().getId() + " is already present in this meeting");
            }
        }
        timeSlots.add(slot);
    }
}
