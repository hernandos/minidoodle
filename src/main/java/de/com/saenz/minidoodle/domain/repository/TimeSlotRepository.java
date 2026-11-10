package de.com.saenz.minidoodle.domain.repository;

import de.com.saenz.minidoodle.domain.model.SlotStatus;
import de.com.saenz.minidoodle.domain.model.TimeSlot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Domain repository port for TimeSlot entities.
 * Pure interface without Spring, JPA, or database framework dependencies.
 */
public interface TimeSlotRepository {

    TimeSlot save(TimeSlot timeSlot);

    List<TimeSlot> saveAll(List<TimeSlot> timeSlots);

    Optional<TimeSlot> findById(Long id);

    List<TimeSlot> findByUserId(Long userId);

    List<TimeSlot> findByUserIdAndStatus(Long userId, SlotStatus status);

    List<TimeSlot> findByUserIdAndTimeRange(Long userId, Instant startTime, Instant endTime);

    List<TimeSlot> findByTimeRange(Instant startTime, Instant endTime);

    List<TimeSlot> findByTimeRangeAndStatus(Instant startTime, Instant endTime, SlotStatus status);

    List<TimeSlot> findByUserIdsAndTimeRange(List<Long> userIds, Instant startTime, Instant endTime);

    List<TimeSlot> findByUserIdsAndTimeRangeAndStatus(List<Long> userIds,
                                                      Instant startTime,
                                                      Instant endTime,
                                                      SlotStatus status);

    Optional<TimeSlot> findByUserIdAndStartTimeAndEndTime(Long userId, Instant startTime, Instant endTime);

    List<TimeSlot> findByUserIdsAndStartTimeAndEndTime(List<Long> userIds, Instant startTime, Instant endTime);

    void deleteById(Long id);

    boolean existsById(Long id);
}
