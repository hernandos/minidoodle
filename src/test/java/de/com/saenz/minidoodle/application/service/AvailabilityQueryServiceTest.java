package de.com.saenz.minidoodle.application.service;

import de.com.saenz.minidoodle.domain.model.Calendar;
import de.com.saenz.minidoodle.domain.model.SlotStatus;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.repository.CalendarRepository;
import de.com.saenz.minidoodle.domain.repository.TimeSlotRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityQueryServiceTest {

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private CalendarRepository calendarRepository;

    private SimpleMeterRegistry meterRegistry;

    private AvailabilityQueryService availabilityQueryService;

    private final Instant start = Instant.parse("2026-11-10T09:00:00Z");
    private final Instant end = start.plus(Duration.ofHours(3));

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        availabilityQueryService = new AvailabilityQueryService(
                timeSlotRepository,
                calendarRepository,
                Optional.of(meterRegistry)
        );
    }

    @Test
    @DisplayName("getAggregatedAvailability should return slots across all users in timeframe and record timer")
    void getAggregatedAvailability_shouldReturnSlots_whenTimeframeValid() {
        User u1 = User.builder().id(1L).username("u1").build();
        User u2 = User.builder().id(2L).username("u2").build();

        TimeSlot s1 = TimeSlot.builder()
                              .id(11L)
                              .user(u1)
                              .startTime(start)
                              .endTime(start.plus(Duration.ofHours(1)))
                              .status(SlotStatus.FREE)
                              .build();
        TimeSlot s2 = TimeSlot.builder()
                              .id(12L)
                              .user(u2)
                              .startTime(start)
                              .endTime(start.plus(Duration.ofHours(1)))
                              .status(SlotStatus.BUSY)
                              .build();

        when(timeSlotRepository.findByTimeRange(start, end)).thenReturn(List.of(s1, s2));

        List<TimeSlot> result = availabilityQueryService.getAggregatedAvailability(start, end);

        assertThat(result).containsExactly(s1, s2);
        verify(timeSlotRepository).findByTimeRange(start, end);
        assertThat(meterRegistry.get("slots.aggregated.view.time").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("getAggregatedFreeAvailability should return only free slots across all users")
    void getAggregatedFreeAvailability_shouldReturnOnlyFreeSlots() {
        User u1 = User.builder().id(1L).username("u1").build();
        TimeSlot s1 = TimeSlot.builder()
                              .id(11L)
                              .user(u1)
                              .startTime(start)
                              .endTime(start.plus(Duration.ofHours(1)))
                              .status(SlotStatus.FREE)
                              .build();

        when(timeSlotRepository.findByTimeRangeAndStatus(start, end, SlotStatus.FREE)).thenReturn(List.of(s1));

        List<TimeSlot> result = availabilityQueryService.getAggregatedFreeAvailability(start, end);

        assertThat(result).containsExactly(s1);
        verify(timeSlotRepository).findByTimeRangeAndStatus(start, end, SlotStatus.FREE);
        assertThat(meterRegistry.get("slots.aggregated.view.time").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("getAggregatedAvailability with userIds should filter by specified users")
    void getAggregatedAvailability_withUserIds_shouldFilter() {
        List<Long> userIds = List.of(1L, 2L);
        User u1 = User.builder().id(1L).username("u1").build();
        TimeSlot s1 = TimeSlot.builder()
                              .id(11L)
                              .user(u1)
                              .startTime(start)
                              .endTime(start.plus(Duration.ofHours(1)))
                              .status(SlotStatus.FREE)
                              .build();

        when(timeSlotRepository.findByUserIdsAndTimeRange(userIds, start, end)).thenReturn(List.of(s1));

        List<TimeSlot> result = availabilityQueryService.getAggregatedAvailability(userIds, start, end);

        assertThat(result).containsExactly(s1);
        verify(timeSlotRepository).findByUserIdsAndTimeRange(userIds, start, end);
        assertThat(meterRegistry.get("slots.aggregated.view.time").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("getAggregatedAvailability should throw IllegalArgumentException when start is after end")
    void getAggregatedAvailability_shouldThrow_whenInvalidTimeframe() {
        assertThatThrownBy(() -> availabilityQueryService.getAggregatedAvailability(end, start))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly before end time");
    }

    @Test
    @DisplayName("getUserCalendar should delegate to calendarRepository")
    void getUserCalendar_shouldDelegate() {
        User u1 = User.builder().id(1L).username("u1").build();
        Calendar cal = Calendar.of(u1);
        when(calendarRepository.findByUserId(1L)).thenReturn(Optional.of(cal));

        Optional<Calendar> result = availabilityQueryService.getUserCalendar(1L);

        assertThat(result).contains(cal);
        verify(calendarRepository).findByUserId(1L);
    }
}
