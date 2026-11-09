package de.com.saenz.minidoodle.application.service;

import de.com.saenz.minidoodle.domain.model.Meeting;
import de.com.saenz.minidoodle.domain.model.SlotStatus;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.repository.MeetingRepository;
import de.com.saenz.minidoodle.domain.repository.TimeSlotRepository;
import de.com.saenz.minidoodle.domain.repository.UserRepository;
import de.com.saenz.minidoodle.domain.usecase.ScheduleMeetingCommand;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingApplicationServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private MeetingApplicationService meetingApplicationService;

    private final Instant start = Instant.parse("2026-11-10T14:00:00Z");
    private final Instant end = start.plus(Duration.ofMinutes(45));

    private User organizer;
    private User participant;

    @BeforeEach
    void setUp() {
        meetingApplicationService = new MeetingApplicationService(
                meetingRepository,
                timeSlotRepository,
                userRepository,
                Optional.of(meterRegistry)
        );

        organizer = User.builder().id(1L).username("org").build();
        participant = User.builder().id(2L).username("part").build();
    }

    @Test
    @DisplayName(
            "scheduleMeeting should mark slots as busy, persist meeting, and increment counter when all participants available")
    void scheduleMeeting_shouldSucceed_whenAllParticipantsAvailable() {
        ScheduleMeetingCommand command = new ScheduleMeetingCommand(
                "Sprint Planning",
                "Discuss backlog items",
                1L,
                List.of(2L),
                start,
                end
        );

        TimeSlot orgSlot = TimeSlot.builder()
                                   .id(101L)
                                   .user(organizer)
                                   .startTime(start)
                                   .endTime(end)
                                   .status(SlotStatus.FREE)
                                   .build();
        TimeSlot partSlot = TimeSlot.builder()
                                    .id(102L)
                                    .user(participant)
                                    .startTime(start)
                                    .endTime(end)
                                    .status(SlotStatus.FREE)
                                    .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(organizer));
        when(userRepository.existsById(2L)).thenReturn(true);
        when(timeSlotRepository.findByUserIdAndStartTimeAndEndTime(1L, start, end)).thenReturn(Optional.of(orgSlot));
        when(timeSlotRepository.findByUserIdAndStartTimeAndEndTime(2L, start, end)).thenReturn(Optional.of(partSlot));
        when(timeSlotRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));
        when(meterRegistry.counter(anyString())).thenReturn(counter);

        Meeting meeting = meetingApplicationService.scheduleMeeting(command);

        assertThat(meeting).isNotNull();
        assertThat(meeting.getTitle()).isEqualTo("Sprint Planning");
        assertThat(meeting.getOrganizer()).isEqualTo(organizer);
        assertThat(meeting.getTimeSlots()).hasSize(2);
        assertThat(meeting.getTimeSlots()).allMatch(TimeSlot::isBusy);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TimeSlot>> slotCaptor = ArgumentCaptor.forClass(List.class);
        verify(timeSlotRepository).saveAll(slotCaptor.capture());
        assertThat(slotCaptor.getValue()).containsExactly(orgSlot, partSlot);
        assertThat(slotCaptor.getValue()).allMatch(slot -> slot.getStatus() == SlotStatus.BUSY);

        verify(meetingRepository).save(any(Meeting.class));
        verify(counter).increment();
    }

    @Test
    @DisplayName("scheduleMeeting should throw IllegalStateException when participant has no matching slot")
    void scheduleMeeting_shouldThrowIllegalStateException_whenParticipantHasNoSlot() {
        ScheduleMeetingCommand command = new ScheduleMeetingCommand(
                "1-on-1",
                null,
                1L,
                List.of(2L),
                start,
                end
        );

        TimeSlot orgSlot = TimeSlot.builder()
                                   .id(101L)
                                   .user(organizer)
                                   .startTime(start)
                                   .endTime(end)
                                   .status(SlotStatus.FREE)
                                   .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(organizer));
        when(userRepository.existsById(2L)).thenReturn(true);
        when(timeSlotRepository.findByUserIdAndStartTimeAndEndTime(1L, start, end)).thenReturn(Optional.of(orgSlot));
        when(timeSlotRepository.findByUserIdAndStartTimeAndEndTime(2L, start, end)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingApplicationService.scheduleMeeting(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User 2 is not available: no matching slot found");

        verify(timeSlotRepository, never()).saveAll(anyList());
        verify(meetingRepository, never()).save(any());
    }

    @Test
    @DisplayName("scheduleMeeting should throw IllegalStateException when participant slot is already busy")
    void scheduleMeeting_shouldThrowIllegalStateException_whenParticipantSlotIsAlreadyBusy() {
        ScheduleMeetingCommand command = new ScheduleMeetingCommand(
                "1-on-1",
                null,
                1L,
                List.of(2L),
                start,
                end
        );

        TimeSlot orgSlot = TimeSlot.builder()
                                   .id(101L)
                                   .user(organizer)
                                   .startTime(start)
                                   .endTime(end)
                                   .status(SlotStatus.FREE)
                                   .build();
        TimeSlot partSlot = TimeSlot.builder()
                                    .id(102L)
                                    .user(participant)
                                    .startTime(start)
                                    .endTime(end)
                                    .status(SlotStatus.BUSY)
                                    .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(organizer));
        when(userRepository.existsById(2L)).thenReturn(true);
        when(timeSlotRepository.findByUserIdAndStartTimeAndEndTime(1L, start, end)).thenReturn(Optional.of(orgSlot));
        when(timeSlotRepository.findByUserIdAndStartTimeAndEndTime(2L, start, end)).thenReturn(Optional.of(partSlot));

        assertThatThrownBy(() -> meetingApplicationService.scheduleMeeting(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slot for timeframe")
                .hasMessageContaining("is already busy");

        verify(timeSlotRepository, never()).saveAll(anyList());
        verify(meetingRepository, never()).save(any());
    }

    @Test
    @DisplayName("scheduleMeeting should throw IllegalArgumentException when organizer is not found")
    void scheduleMeeting_shouldThrowIllegalArgumentException_whenOrganizerNotFound() {
        ScheduleMeetingCommand command = new ScheduleMeetingCommand("Sync", null, 99L, List.of(), start, end);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingApplicationService.scheduleMeeting(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Organizer not found with id: 99");
    }

    @Test
    @DisplayName("scheduleMeeting should throw IllegalArgumentException when participant user does not exist")
    void scheduleMeeting_shouldThrowIllegalArgumentException_whenParticipantNotFound() {
        ScheduleMeetingCommand command = new ScheduleMeetingCommand("Sync", null, 1L, List.of(99L), start, end);
        when(userRepository.findById(1L)).thenReturn(Optional.of(organizer));
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> meetingApplicationService.scheduleMeeting(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Participant not found with id: 99");
    }
}
