package de.com.saenz.minidoodle.domain.usecase;

import de.com.saenz.minidoodle.domain.model.TimeSlot;

import java.time.Instant;

/**
 * Port interface for proactively creating a new TimeSlot for a user.
 */
public interface CreateTimeSlotUseCase {

    TimeSlot createTimeSlot(Long userId, Instant startTime, Instant endTime);
}
