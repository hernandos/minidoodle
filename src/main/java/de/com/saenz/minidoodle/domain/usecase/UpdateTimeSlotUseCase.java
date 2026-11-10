package de.com.saenz.minidoodle.domain.usecase;

import de.com.saenz.minidoodle.domain.model.TimeSlot;

import java.time.Instant;

/**
 * Port interface for modifying an existing TimeSlot's timeframe.
 */
public interface UpdateTimeSlotUseCase {

    TimeSlot updateTimeSlot(Long slotId, Instant newStartTime, Instant newEndTime);
}
