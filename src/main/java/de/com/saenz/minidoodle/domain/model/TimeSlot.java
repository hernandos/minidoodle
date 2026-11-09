package de.com.saenz.minidoodle.domain.model;

import lombok.*;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure domain entity representing a block of time proactively created by a user.
 * Contains start_time, end_time, and status (FREE or BUSY).
 * Slots belonging to the same user must never overlap.
 */
@Getter
@EqualsAndHashCode
@Builder
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class TimeSlot {
    private Long id;
    private User user;
    private Instant startTime;
    private Instant endTime;
    private SlotStatus status;
    @EqualsAndHashCode.Exclude
    private Long version;

    public boolean isFree() {
        return SlotStatus.FREE.equals(this.status);
    }

    public boolean isBusy() {
        return SlotStatus.BUSY.equals(this.status);
    }

    public void markAsBusy() {
        this.status = SlotStatus.BUSY;
    }

    public void markAsFree() {
        this.status = SlotStatus.FREE;
    }

    public void updateTimeframe(Instant newStartTime, Instant newEndTime) {
        if (newStartTime == null || newEndTime == null) {
            throw new IllegalArgumentException("Start time and end time must not be null");
        }
        if (!newStartTime.isBefore(newEndTime)) {
            throw new IllegalArgumentException("Start time must be strictly before end time");
        }
        this.startTime = newStartTime;
        this.endTime = newEndTime;
    }

    public Duration getDuration() {
        if (startTime == null || endTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, endTime);
    }

    public boolean overlapsWith(TimeSlot other) {
        if (other == null || other.getStartTime() == null || other.getEndTime() == null) {
            return false;
        }
        return overlapsWith(other.getStartTime(), other.getEndTime());
    }

    public boolean overlapsWith(Instant otherStart, Instant otherEnd) {
        if (startTime == null || endTime == null || otherStart == null || otherEnd == null) {
            return false;
        }
        return startTime.isBefore(otherEnd) && endTime.isAfter(otherStart);
    }

    public boolean matchesTimeframe(Instant targetStart, Instant targetEnd) {
        if (startTime == null || endTime == null || targetStart == null || targetEnd == null) {
            return false;
        }
        return startTime.equals(targetStart) && endTime.equals(targetEnd);
    }

    public void validate() {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time must not be null");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Start time must be strictly before end time");
        }
    }
}
