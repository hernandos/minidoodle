package de.com.saenz.minidoodle.application.service;

import de.com.saenz.minidoodle.domain.model.SlotStatus;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.repository.TimeSlotRepository;
import de.com.saenz.minidoodle.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeSlotApplicationServiceTest {

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TimeSlotApplicationService timeSlotApplicationService;

    private final Instant start = Instant.parse("2026-11-10T10:00:00Z");
    private final Instant end = start.plus(Duration.ofHours(1));

    @Test
    @DisplayName("createTimeSlot should persist and return new free slot when valid")
    void createTimeSlot_shouldPersistAndReturn_whenValid() {
        User user = User.builder().id(1L).username("alice").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(timeSlotRepository.findByUserIdAndTimeRange(1L, start, end)).thenReturn(List.of());
        when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        TimeSlot result = timeSlotApplicationService.createTimeSlot(1L, start, end);

        assertThat(result).isNotNull();
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getStartTime()).isEqualTo(start);
        assertThat(result.getEndTime()).isEqualTo(end);
        assertThat(result.getStatus()).isEqualTo(SlotStatus.FREE);
        verify(timeSlotRepository).save(any(TimeSlot.class));
    }

    @Test
    @DisplayName("createTimeSlot should throw IllegalArgumentException when user does not exist")
    void createTimeSlot_shouldThrowIllegalArgumentException_whenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timeSlotApplicationService.createTimeSlot(99L, start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found with id: 99");
    }

    @Test
    @DisplayName("createTimeSlot should throw IllegalArgumentException when start time is not before end time")
    void createTimeSlot_shouldThrowIllegalArgumentException_whenInvalidTimeframe() {
        assertThatThrownBy(() -> timeSlotApplicationService.createTimeSlot(1L, end, start))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly before end time");
    }

    @Test
    @DisplayName("createTimeSlot should throw IllegalArgumentException when overlapping slot exists for user")
    void createTimeSlot_shouldThrowIllegalArgumentException_whenOverlappingSlotExists() {
        User user = User.builder().id(1L).username("alice").build();
        TimeSlot existing = TimeSlot.builder().id(10L).user(user).startTime(start).endTime(end).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(timeSlotRepository.findByUserIdAndTimeRange(1L, start, end)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> timeSlotApplicationService.createTimeSlot(1L, start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlaps with existing slot");

        verify(timeSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTimeSlot should update timeframe and save slot when valid")
    void updateTimeSlot_shouldUpdateAndSave_whenValid() {
        User user = User.builder().id(1L).username("alice").build();
        TimeSlot slot = TimeSlot.builder()
                                .id(5L)
                                .user(user)
                                .startTime(start)
                                .endTime(end)
                                .status(SlotStatus.FREE)
                                .build();

        Instant newStart = start.plus(Duration.ofHours(2));
        Instant newEnd = end.plus(Duration.ofHours(2));

        when(timeSlotRepository.findById(5L)).thenReturn(Optional.of(slot));
        when(timeSlotRepository.findByUserIdAndTimeRange(1L, newStart, newEnd)).thenReturn(List.of());
        when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        TimeSlot updated = timeSlotApplicationService.updateTimeSlot(5L, newStart, newEnd);

        assertThat(updated.getStartTime()).isEqualTo(newStart);
        assertThat(updated.getEndTime()).isEqualTo(newEnd);
        verify(timeSlotRepository).save(slot);
    }

    @Test
    @DisplayName("updateTimeSlot should throw IllegalStateException when slot is already busy/booked")
    void updateTimeSlot_shouldThrowIllegalStateException_whenSlotIsBusy() {
        User user = User.builder().id(1L).username("alice").build();
        TimeSlot slot = TimeSlot.builder()
                                .id(5L)
                                .user(user)
                                .startTime(start)
                                .endTime(end)
                                .status(SlotStatus.BUSY)
                                .build();

        when(timeSlotRepository.findById(5L)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> timeSlotApplicationService.updateTimeSlot(5L,
                                                                           start.plus(Duration.ofHours(1)),
                                                                           end.plus(Duration.ofHours(1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot modify a booked or busy time slot");

        verify(timeSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTimeSlot should throw IllegalArgumentException when new timeframe overlaps another slot")
    void updateTimeSlot_shouldThrowIllegalArgumentException_whenNewTimeframeOverlaps() {
        User user = User.builder().id(1L).username("alice").build();
        TimeSlot slotToUpdate = TimeSlot.builder()
                                        .id(5L)
                                        .user(user)
                                        .startTime(start)
                                        .endTime(end)
                                        .status(SlotStatus.FREE)
                                        .build();
        TimeSlot otherSlot = TimeSlot.builder()
                                     .id(6L)
                                     .user(user)
                                     .startTime(end)
                                     .endTime(end.plus(Duration.ofHours(1)))
                                     .status(SlotStatus.FREE)
                                     .build();

        Instant newEnd = end.plus(Duration.ofMinutes(30));

        when(timeSlotRepository.findById(5L)).thenReturn(Optional.of(slotToUpdate));
        when(timeSlotRepository.findByUserIdAndTimeRange(1L, start, newEnd)).thenReturn(List.of(slotToUpdate,
                                                                                                otherSlot));

        assertThatThrownBy(() -> timeSlotApplicationService.updateTimeSlot(5L, start, newEnd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlaps with an existing slot");

        verify(timeSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteTimeSlot should delete slot when slot is free")
    void deleteTimeSlot_shouldDelete_whenSlotIsFree() {
        TimeSlot slot = TimeSlot.builder().id(5L).status(SlotStatus.FREE).build();
        when(timeSlotRepository.findById(5L)).thenReturn(Optional.of(slot));

        timeSlotApplicationService.deleteTimeSlot(5L);

        verify(timeSlotRepository).deleteById(5L);
    }

    @Test
    @DisplayName("deleteTimeSlot should throw IllegalStateException when slot is busy")
    void deleteTimeSlot_shouldThrowIllegalStateException_whenSlotIsBusy() {
        TimeSlot slot = TimeSlot.builder().id(5L).status(SlotStatus.BUSY).build();
        when(timeSlotRepository.findById(5L)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> timeSlotApplicationService.deleteTimeSlot(5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot delete a booked or busy time slot");

        verify(timeSlotRepository, never()).deleteById(any());
    }
}
