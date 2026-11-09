package de.com.saenz.minidoodle.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.*;

/**
 * Logical domain view / aggregate representing a user's calendar.
 * Following Domain-Driven Design, there is no monolithic Calendar database entity or table.
 * Instead, a Calendar is logically represented by an aggregate of non-overlapping
 * TimeSlots belonging to a specific user.
 * <p>
 * Invariant: slots belonging to the same user must never overlap.
 * Enforced exclusively through {@link #addTimeSlot(TimeSlot)}; the slot set is
 * exposed read-only and construction is only possible via the static factories.
 */
@Getter
public class Calendar {
    private static final Comparator<TimeSlot> SLOT_ORDER = Comparator.comparing(TimeSlot::getStartTime)
                                                                     .thenComparing(TimeSlot::getEndTime)
                                                                     .thenComparing(TimeSlot::getId,
                                                                                    Comparator.nullsFirst(Comparator.naturalOrder()));

    private final User user;
    private final SortedSet<TimeSlot> timeSlots = new TreeSet<>(SLOT_ORDER);

    private Calendar(User user) {
        this.user = user;
    }

    static Calendar of(User user) {
        return new Calendar(user);
    }

    static Calendar of(User user, List<TimeSlot> slots) {
        Calendar calendar = new Calendar(user);
        if (slots != null) {
            for (TimeSlot slot : slots) {
                calendar.addTimeSlot(slot);
            }
        }
        return calendar;
    }

    SortedSet<TimeSlot> getTimeSlots() {
        return Collections.unmodifiableSortedSet(timeSlots);
    }

    void addTimeSlot(TimeSlot newSlot) {
        if (newSlot == null) {
            throw new IllegalArgumentException("Time slot cannot be null");
        }
        newSlot.validate();

        if (user != null && newSlot.getUser() != null && !Objects.equals(user.getId(), newSlot.getUser().getId())) {
            throw new IllegalArgumentException("Time slot does not belong to user: " + user.getId());
        }

        if (hasOverlappingSlot(newSlot)) {
            throw new IllegalArgumentException(
                    String.format("Time slot [%s - %s] overlaps with an existing slot in the calendar",
                                  newSlot.getStartTime(), newSlot.getEndTime())
            );
        }

        timeSlots.add(newSlot);
    }

    private boolean hasOverlappingSlot(TimeSlot candidate) {
        if (candidate == null) {
            return false;
        }
        return timeSlots.stream().anyMatch(existing -> existing.overlapsWith(candidate));
    }

    Optional<TimeSlot> findMatchingFreeSlot(Instant start, Instant end) {
        return timeSlots.stream()
                        .filter(TimeSlot::isFree)
                        .filter(slot -> slot.matchesTimeframe(start, end))
                        .findFirst();
    }

    List<TimeSlot> getFreeSlots() {
        return timeSlots.stream().filter(TimeSlot::isFree).toList();
    }

    List<TimeSlot> getBusySlots() {
        return timeSlots.stream().filter(TimeSlot::isBusy).toList();
    }

    List<TimeSlot> getSlotsBetween(Instant start, Instant end) {
        return timeSlots.stream()
                        .filter(slot -> slot.overlapsWith(start, end))
                        .toList();
    }

    List<TimeSlot> getFreeSlotsBetween(Instant start, Instant end) {
        return timeSlots.stream()
                        .filter(TimeSlot::isFree)
                        .filter(slot -> slot.overlapsWith(start, end))
                        .toList();
    }
}
