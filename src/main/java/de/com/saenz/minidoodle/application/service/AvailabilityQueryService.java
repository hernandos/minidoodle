package de.com.saenz.minidoodle.application.service;

import de.com.saenz.minidoodle.domain.model.Calendar;
import de.com.saenz.minidoodle.domain.model.SlotStatus;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.repository.CalendarRepository;
import de.com.saenz.minidoodle.domain.repository.TimeSlotRepository;
import de.com.saenz.minidoodle.domain.usecase.GetAggregatedAvailabilityQuery;
import de.com.saenz.minidoodle.domain.usecase.GetUserCalendarQuery;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Query service for retrieving aggregated availability across users and individual user calendars.
 * Monitors execution time of complex queries with the 'slots.aggregated.view.time' timer.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AvailabilityQueryService implements GetAggregatedAvailabilityQuery, GetUserCalendarQuery {

    private final TimeSlotRepository timeSlotRepository;
    private final CalendarRepository calendarRepository;
    private final Optional<MeterRegistry> meterRegistry;

    @Override
    public List<TimeSlot> getAggregatedAvailability(Instant startTime, Instant endTime) {
        validateTimeframe(startTime, endTime);
        return recordTimer(() -> timeSlotRepository.findByTimeRange(startTime, endTime));
    }

    @Override
    public List<TimeSlot> getAggregatedAvailability(List<Long> userIds, Instant startTime, Instant endTime) {
        validateTimeframe(startTime, endTime);
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return recordTimer(() -> timeSlotRepository.findByUserIdsAndTimeRange(userIds, startTime, endTime));
    }

    @Override
    public List<TimeSlot> getAggregatedFreeAvailability(Instant startTime, Instant endTime) {
        validateTimeframe(startTime, endTime);
        return recordTimer(() -> timeSlotRepository.findByTimeRangeAndStatus(startTime, endTime, SlotStatus.FREE));
    }

    @Override
    public List<TimeSlot> getAggregatedFreeAvailability(List<Long> userIds, Instant startTime, Instant endTime) {
        validateTimeframe(startTime, endTime);
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return recordTimer(() -> timeSlotRepository.findByUserIdsAndTimeRangeAndStatus(userIds,
                                                                                       startTime,
                                                                                       endTime,
                                                                                       SlotStatus.FREE));
    }

    @Override
    public Optional<Calendar> getUserCalendar(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return calendarRepository.findByUserId(userId);
    }

    @Override
    public Optional<Calendar> getUserCalendar(Long userId, Instant startTime, Instant endTime) {
        if (userId == null) {
            return Optional.empty();
        }
        validateTimeframe(startTime, endTime);
        return calendarRepository.findByUserIdAndTimeRange(userId, startTime, endTime);
    }

    private <T> T recordTimer(Supplier<T> supplier) {
        if (meterRegistry.isEmpty()) {
            return supplier.get();
        }
        MeterRegistry registry = meterRegistry.get();
        Timer.Sample sample = Timer.start(registry);
        try {
            return supplier.get();
        } finally {
            sample.stop(registry.timer("slots.aggregated.view.time"));
        }
    }

    private void validateTimeframe(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time must not be null");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Start time must be strictly before end time");
        }
    }
}
