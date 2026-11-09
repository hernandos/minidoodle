package de.com.saenz.minidoodle.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeetingTest {

    @Test
    @DisplayName("should inherit timeframe from consumed slots")
    void getStartTimeAndEndTime_shouldInheritFromSlots_whenSlotsExist() {
        Instant start = Instant.parse("2026-11-09T14:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(45));

        TimeSlot slot = TimeSlot.builder()
                                .startTime(start)
                                .endTime(end)
                                .status(SlotStatus.BUSY)
                                .build();

        Meeting meeting = Meeting.of("Architecture Review", null, null);

        meeting.addConsumedSlot(slot);

        assertThat(meeting.getStartTime()).isEqualTo(start);
        assertThat(meeting.getEndTime()).isEqualTo(end);
    }

    @Test
    @DisplayName("should reject slot with mismatching timeframe")
    void addConsumedSlot_shouldThrowException_whenTimeframeMismatches() {
        Instant start = Instant.parse("2026-11-09T14:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(30));

        TimeSlot slot1 = TimeSlot.builder()
                                 .startTime(start)
                                 .endTime(end)
                                 .status(SlotStatus.BUSY)
                                 .build();

        TimeSlot slot2 = TimeSlot.builder()
                                 .startTime(start)
                                 .endTime(end.plus(Duration.ofMinutes(15)))
                                 .status(SlotStatus.BUSY)
                                 .build();

        Meeting meeting = Meeting.of("Sprint Planning", null, null);

        meeting.addConsumedSlot(slot1);

        assertThatThrownBy(() -> meeting.addConsumedSlot(slot2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same timeframe");
    }

    @Test
    @DisplayName("should reject slot if it is FREE")
    void addConsumedSlot_shouldThrowException_whenSlotIsFree() {
        Instant start = Instant.parse("2026-11-09T14:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(30));

        TimeSlot slot = TimeSlot.builder().startTime(start).endTime(end).status(SlotStatus.FREE).build();
        Meeting meeting = Meeting.of("Sprint Planning", null, null);

        assertThatThrownBy(() -> meeting.addConsumedSlot(slot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot consume a FREE slot");
    }

    @Test
    @DisplayName("should reject duplicate slot or duplicate user in meeting")
    void addConsumedSlot_shouldRejectDuplicateSlotAndUser() {
        User user = User.builder().id(10L).name("Alice").build();
        Instant start = Instant.parse("2026-11-09T14:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(30));

        TimeSlot slot1 = TimeSlot.builder().id(1L).user(user).startTime(start).endTime(end).status(SlotStatus.BUSY).build();
        TimeSlot slot2 = TimeSlot.builder().id(2L).user(user).startTime(start).endTime(end).status(SlotStatus.BUSY).build();

        Meeting meeting = Meeting.of("Sync", null, null);
        meeting.addConsumedSlot(slot1);

        assertThatThrownBy(() -> meeting.addConsumedSlot(slot1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already added");

        assertThatThrownBy(() -> meeting.addConsumedSlot(slot2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already present");
    }

    @Test
    @DisplayName("should list participants excluding organizer")
    void getParticipants_shouldExcludeOrganizer_whenParticipantsAreQueried() {
        User organizer = User.builder().id(1L).name("Alice").build();
        User participant1 = User.builder().id(2L).name("Bob").build();
        User participant2 = User.builder().id(3L).name("Charlie").build();

        Instant start = Instant.parse("2026-11-09T14:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(30));

        TimeSlot slotOrg = TimeSlot.builder().user(organizer).startTime(start).endTime(end).status(SlotStatus.BUSY).build();
        TimeSlot slotBob = TimeSlot.builder().user(participant1).startTime(start).endTime(end).status(SlotStatus.BUSY).build();
        TimeSlot slotCharlie = TimeSlot.builder().user(participant2).startTime(start).endTime(end).status(SlotStatus.BUSY).build();

        Meeting meeting = Meeting.of(null, null, organizer);

        meeting.addConsumedSlot(slotOrg);
        meeting.addConsumedSlot(slotBob);
        meeting.addConsumedSlot(slotCharlie);

        List<User> participants = meeting.getParticipants();
        assertThat(participants).containsExactlyInAnyOrder(participant1, participant2);
        assertThat(participants).doesNotContain(organizer);
    }
}
