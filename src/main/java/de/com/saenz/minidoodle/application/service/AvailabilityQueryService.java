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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        log.debug("Querying aggregated availability for timeframe [{} - {}]", startTime, endTime);
        return recordTimer(() -> timeSlotRepository.findByTimeRange(startTime, endTime));
    }

    @Override
    public List<TimeSlot> getAggregatedAvailability(List<Long> userIds, Instant startTime, Instant endTime) {
        validateTimeframe(startTime, endTime);
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        log.debug("Querying aggregated availability for {} users in timeframe [{} - {}]",
                  userIds.size(),
                  startTime,
                  endTime);
        return recordTimer(() -> timeSlotRepository.findByUserIdsAndTimeRange(userIds, startTime, endTime));
    }

    @Override
    public List<TimeSlot> getAggregatedFreeAvailability(Instant startTime, Instant endTime) {
        validateTimeframe(startTime, endTime);
        log.debug("Querying aggregated FREE slots for timeframe [{} - {}]", startTime, endTime);
        return recordTimer(() -> timeSlotRepository.findByTimeRangeAndStatus(startTime, endTime, SlotStatus.FREE));
    }

    @Override
    public List<TimeSlot> getAggregatedFreeAvailability(List<Long> userIds, Instant startTime, Instant endTime) {
        validateTimeframe(startTime, endTime);
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        log.debug("Querying aggregated FREE slots for {} users in timeframe [{} - {}]",
                  userIds.size(),
                  startTime,
                  endTime);
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
        log.debug("Fetching full calendar for user id: {}", userId);
        return calendarRepository.findByUserId(userId);
    }

    @Override
    public Optional<Calendar> getUserCalendar(Long userId, Instant startTime, Instant endTime) {
        if (userId == null) {
            return Optional.empty();
        }
        validateTimeframe(startTime, endTime);
        log.debug("Fetching calendar for user id: {} in timeframe [{} - {}]", userId, startTime, endTime);
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
            log.warn("Timeframe validation failed: null start or end time");
            throw new IllegalArgumentException("Start time and end time must not be null");
        }
        if (!startTime.isBefore(endTime)) {
            log.warn("Timeframe validation failed: start [{}] is not before end [{}]", startTime, endTime);
            throw new IllegalArgumentException("Start time must be strictly before end time");
        }
    }
}
