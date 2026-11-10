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
        when(userRepository.findMissingIds(anyCollection())).thenReturn(List.of());
        when(timeSlotRepository.findByUserIdsAndStartTimeAndEndTime(List.of(1L, 2L), start, end))
                .thenReturn(List.of(orgSlot, partSlot));
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
        verify(timeSlotRepository).findByUserIdsAndStartTimeAndEndTime(List.of(1L, 2L), start, end);
        verify(timeSlotRepository, never()).findByUserIdAndStartTimeAndEndTime(anyLong(), any(), any());
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
        when(userRepository.findMissingIds(anyCollection())).thenReturn(List.of());
        when(timeSlotRepository.findByUserIdsAndStartTimeAndEndTime(List.of(1L, 2L), start, end))
                .thenReturn(List.of(orgSlot));

        assertThatThrownBy(() -> meetingApplicationService.scheduleMeeting(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Users [2] are not available: no matching slot found");

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
        when(userRepository.findMissingIds(anyCollection())).thenReturn(List.of());
        when(timeSlotRepository.findByUserIdsAndStartTimeAndEndTime(List.of(1L, 2L), start, end))
                .thenReturn(List.of(orgSlot, partSlot));

        assertThatThrownBy(() -> meetingApplicationService.scheduleMeeting(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Users [2] are not available")
                .hasMessageContaining("already busy");

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
        when(userRepository.findMissingIds(anyCollection())).thenReturn(List.of(99L));

        assertThatThrownBy(() -> meetingApplicationService.scheduleMeeting(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Users not found with ids: [99]");
    }

    @Test
    @DisplayName("scheduleMeeting should throw IllegalArgumentException when participantIds list contains null")
    void scheduleMeeting_shouldThrowIllegalArgumentException_whenParticipantIdsContainsNull() {
        List<Long> participantsWithNull = new java.util.ArrayList<>();
        participantsWithNull.add(2L);
        participantsWithNull.add(null);

        assertThatThrownBy(() -> new ScheduleMeetingCommand("Sync", null, 1L, participantsWithNull, start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Participant IDs must not contain null elements");
    }

    @Test
    @DisplayName("cancelMeeting should mark consumed slots as free and delete meeting")
    void cancelMeeting_shouldRevertSlotsToFree_andDeleteMeeting() {
        TimeSlot slot1 = TimeSlot.builder().id(101L).startTime(start).endTime(end).status(SlotStatus.BUSY).build();
        TimeSlot slot2 = TimeSlot.builder().id(102L).startTime(start).endTime(end).status(SlotStatus.BUSY).build();
        Meeting meeting = Meeting.of("Sync", null, organizer);
        meeting.addConsumedSlot(slot1);
        meeting.addConsumedSlot(slot2);

        when(meetingRepository.findById(42L)).thenReturn(Optional.of(meeting));

        meetingApplicationService.cancelMeeting(42L);

        assertThat(slot1.getStatus()).isEqualTo(SlotStatus.FREE);
        assertThat(slot2.getStatus()).isEqualTo(SlotStatus.FREE);
        verify(timeSlotRepository).saveAll(meeting.getTimeSlots());
        verify(meetingRepository).deleteById(42L);
    }

    @Test
    @DisplayName("cancelMeeting should throw IllegalArgumentException when meeting does not exist")
    void cancelMeeting_shouldThrowIllegalArgumentException_whenMeetingNotFound() {
        when(meetingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingApplicationService.cancelMeeting(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Meeting not found with id: 99");
    }
}
