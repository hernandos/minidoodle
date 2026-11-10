package de.com.saenz.minidoodle.domain.usecase;

import de.com.saenz.minidoodle.domain.model.TimeSlot;

import java.time.Instant;
import java.util.List;

/**
 * Port query interface for inspecting aggregated calendar availability across users.
 */
public interface GetAggregatedAvailabilityQuery {

    List<TimeSlot> getAggregatedAvailability(Instant startTime, Instant endTime);

    List<TimeSlot> getAggregatedAvailability(List<Long> userIds, Instant startTime, Instant endTime);

    List<TimeSlot> getAggregatedFreeAvailability(Instant startTime, Instant endTime);

    List<TimeSlot> getAggregatedFreeAvailability(List<Long> userIds, Instant startTime, Instant endTime);
}
