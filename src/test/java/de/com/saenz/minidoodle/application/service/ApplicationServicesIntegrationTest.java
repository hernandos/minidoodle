package de.com.saenz.minidoodle.application.service;

import de.com.saenz.minidoodle.PostgresIntegrationTest;
import de.com.saenz.minidoodle.domain.model.Meeting;
import de.com.saenz.minidoodle.domain.model.SlotStatus;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.repository.MeetingRepository;
import de.com.saenz.minidoodle.domain.repository.TimeSlotRepository;
import de.com.saenz.minidoodle.domain.repository.UserRepository;
import de.com.saenz.minidoodle.domain.usecase.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
class ApplicationServicesIntegrationTest {

    @Autowired
    private CreateTimeSlotUseCase createTimeSlotUseCase;

    @Autowired
    private UpdateTimeSlotUseCase updateTimeSlotUseCase;

    @Autowired
    private DeleteTimeSlotUseCase deleteTimeSlotUseCase;

    @Autowired
    private ScheduleMeetingUseCase scheduleMeetingUseCase;

    @Autowired
    private CancelMeetingUseCase cancelMeetingUseCase;

    @Autowired
    private GetAggregatedAvailabilityQuery aggregatedAvailabilityQuery;

    @Autowired
    private GetMeetingQuery getMeetingQuery;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    private User createUniqueUser(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                                       .username(prefix + "_" + uid)
                                       .name("User " + uid)
                                       .email(prefix + "_" + uid + "@example.com")
                                       .build());
    }

    @Test
    @DisplayName(
            "full lifecycle: create slots, query aggregated availability, schedule meeting, and verify state transition to busy")
    void fullSchedulingLifecycle_shouldTransitionSlotsToBusy_andPersistMeeting() {
        User organizer = createUniqueUser("lifecycle_org");
        User participant1 = createUniqueUser("lifecycle_p1");
        User participant2 = createUniqueUser("lifecycle_p2");

        Instant start = Instant.parse("2026-11-12T10:00:00Z");
        Instant end = start.plus(Duration.ofHours(1));

        // 1. Proactively create free slots
        TimeSlot orgSlot = createTimeSlotUseCase.createTimeSlot(organizer.getId(), start, end);
        TimeSlot p1Slot = createTimeSlotUseCase.createTimeSlot(participant1.getId(), start, end);
        TimeSlot p2Slot = createTimeSlotUseCase.createTimeSlot(participant2.getId(), start, end);

        assertThat(orgSlot.getStatus()).isEqualTo(SlotStatus.FREE);
        assertThat(p1Slot.getStatus()).isEqualTo(SlotStatus.FREE);
        assertThat(p2Slot.getStatus()).isEqualTo(SlotStatus.FREE);

        // 2. Query aggregated free availability for the 3 users
        List<Long> userIds = List.of(organizer.getId(), participant1.getId(), participant2.getId());
        List<TimeSlot> freeSlots = aggregatedAvailabilityQuery.getAggregatedFreeAvailability(userIds, start, end);
        assertThat(freeSlots).hasSize(3);

        // 3. Schedule meeting
        ScheduleMeetingCommand command = new ScheduleMeetingCommand(
                "Architecture Review",
                "Deep dive on DDD & CQRS-lite",
                organizer.getId(),
                List.of(participant1.getId(), participant2.getId()),
                start,
                end
        );

        Meeting scheduled = scheduleMeetingUseCase.scheduleMeeting(command);

        assertThat(scheduled.getId()).isNotNull();
        assertThat(scheduled.getTitle()).isEqualTo("Architecture Review");
        assertThat(scheduled.getTimeSlots()).hasSize(3);
        assertThat(scheduled.getParticipants()).extracting(User::getId)
                                               .containsExactlyInAnyOrder(participant1.getId(), participant2.getId());

        // 4. Verify in DB that all 3 slots transitioned to BUSY
        Optional<TimeSlot> reloadedOrgSlot = timeSlotRepository.findById(orgSlot.getId());
        Optional<TimeSlot> reloadedP1Slot = timeSlotRepository.findById(p1Slot.getId());
        Optional<TimeSlot> reloadedP2Slot = timeSlotRepository.findById(p2Slot.getId());

        assertThat(reloadedOrgSlot).isPresent().get().extracting(TimeSlot::getStatus).isEqualTo(SlotStatus.BUSY);
        assertThat(reloadedP1Slot).isPresent().get().extracting(TimeSlot::getStatus).isEqualTo(SlotStatus.BUSY);
        assertThat(reloadedP2Slot).isPresent().get().extracting(TimeSlot::getStatus).isEqualTo(SlotStatus.BUSY);

        // 5. Query aggregated free availability now yields zero free slots
        List<TimeSlot> updatedFreeSlots = aggregatedAvailabilityQuery.getAggregatedFreeAvailability(userIds,
                                                                                                    start,
                                                                                                    end);
        assertThat(updatedFreeSlots).isEmpty();

        // 6. Attempting to schedule another meeting for the same timeframe fails because slots are now BUSY
        ScheduleMeetingCommand conflictCommand = new ScheduleMeetingCommand(
                "Conflicting Meeting",
                null,
                organizer.getId(),
                List.of(participant1.getId()),
                start,
                end
        );
        assertThatThrownBy(() -> scheduleMeetingUseCase.scheduleMeeting(conflictCommand))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is already busy");

        // 7. Modifying a booked slot fails
        assertThatThrownBy(() -> updateTimeSlotUseCase.updateTimeSlot(orgSlot.getId(),
                                                                      start.plus(Duration.ofHours(1)),
                                                                      end.plus(Duration.ofHours(1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot modify a booked or busy time slot");

        // 8. Deleting a booked slot fails
        assertThatThrownBy(() -> deleteTimeSlotUseCase.deleteTimeSlot(orgSlot.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot delete a booked or busy time slot");
    }

    @Test
    @DisplayName("should allow modifying and deleting free slots")
    void slotManagement_shouldAllowUpdateAndDelete_whenSlotIsFree() {
        User user = createUniqueUser("slot_mgmt");
        Instant start = Instant.parse("2026-11-15T08:00:00Z");
        Instant end = start.plus(Duration.ofHours(2));

        TimeSlot slot = createTimeSlotUseCase.createTimeSlot(user.getId(), start, end);
        assertThat(slot.getId()).isNotNull();

        Instant newStart = start.plus(Duration.ofHours(4));
        Instant newEnd = end.plus(Duration.ofHours(4));
        TimeSlot updated = updateTimeSlotUseCase.updateTimeSlot(slot.getId(), newStart, newEnd);
        assertThat(updated.getStartTime()).isEqualTo(newStart);
        assertThat(updated.getEndTime()).isEqualTo(newEnd);

        deleteTimeSlotUseCase.deleteTimeSlot(slot.getId());
        assertThat(timeSlotRepository.findById(slot.getId())).isEmpty();
    }

    @Test
    @DisplayName("cancelMeeting should free all consumed slots and delete meeting")
    void cancelMeeting_shouldFreeAllSlots_andDeleteMeeting() {
        User organizer = createUniqueUser("cancel_org");
        User participant = createUniqueUser("cancel_part");

        Instant start = Instant.parse("2026-11-20T11:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(30));

        TimeSlot orgSlot = createTimeSlotUseCase.createTimeSlot(organizer.getId(), start, end);
        TimeSlot partSlot = createTimeSlotUseCase.createTimeSlot(participant.getId(), start, end);

        Meeting meeting = scheduleMeetingUseCase.scheduleMeeting(new ScheduleMeetingCommand(
                "Quick Sync", null, organizer.getId(), List.of(participant.getId()), start, end
        ));

        assertThat(timeSlotRepository.findById(orgSlot.getId())).get().extracting(TimeSlot::getStatus).isEqualTo(SlotStatus.BUSY);
        assertThat(timeSlotRepository.findById(partSlot.getId())).get().extracting(TimeSlot::getStatus).isEqualTo(SlotStatus.BUSY);

        // Cancel meeting
        cancelMeetingUseCase.cancelMeeting(meeting.getId());

        // Verify meeting is deleted
        assertThat(meetingRepository.findById(meeting.getId())).isEmpty();

        // Verify slots are freed back to FREE
        assertThat(timeSlotRepository.findById(orgSlot.getId())).get().extracting(TimeSlot::getStatus).isEqualTo(SlotStatus.FREE);
        assertThat(timeSlotRepository.findById(partSlot.getId())).get().extracting(TimeSlot::getStatus).isEqualTo(SlotStatus.FREE);
    }
}
