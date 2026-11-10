package de.com.saenz.minidoodle.infrastructure.persistence.jpa.adapter;

import de.com.saenz.minidoodle.PostgresIntegrationTest;
import de.com.saenz.minidoodle.domain.model.Meeting;
import de.com.saenz.minidoodle.domain.model.SlotStatus;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.repository.MeetingRepository;
import de.com.saenz.minidoodle.domain.repository.TimeSlotRepository;
import de.com.saenz.minidoodle.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
class MeetingRepositoryAdapterTest {

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    private User createUniqueUser(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                                       .username(prefix + "_" + uid)
                                       .name("Meeting User " + uid)
                                       .email(prefix + "_" + uid + "@example.com")
                                       .build());
    }

    @Test
    @DisplayName("should save and retrieve meeting with consumed time slots")
    void save_shouldPersistAndRetrieveMeeting_withConsumedSlots() {
        User organizer = createUniqueUser("meeting_org");
        User participant = createUniqueUser("meeting_part");

        Instant start = Instant.parse("2026-11-09T16:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(45));

        TimeSlot orgSlot = timeSlotRepository.save(TimeSlot.builder()
                                                           .user(organizer)
                                                           .startTime(start)
                                                           .endTime(end)
                                                           .status(SlotStatus.BUSY)
                                                           .build());

        TimeSlot partSlot = timeSlotRepository.save(TimeSlot.builder()
                                                            .user(participant)
                                                            .startTime(start)
                                                            .endTime(end)
                                                            .status(SlotStatus.BUSY)
                                                            .build());

        Meeting meeting = Meeting.of("Design Sync", "Reviewing JPA design", organizer);
        meeting.addConsumedSlot(orgSlot);
        meeting.addConsumedSlot(partSlot);

        Meeting saved = meetingRepository.save(meeting);

        assertThat(saved.getId()).isNotNull();

        Optional<Meeting> retrieved = meetingRepository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getTitle()).isEqualTo("Design Sync");
        assertThat(retrieved.get().getOrganizer().getId()).isEqualTo(organizer.getId());
        assertThat(retrieved.get().getTimeSlots()).hasSize(2);
        assertThat(retrieved.get().getParticipants()).extracting(User::getId).containsExactly(participant.getId());
    }

    @Test
    @DisplayName("should find meetings by participant user id")
    void findByParticipantUserId_shouldReturnMeetings_whereUserParticipates() {
        User organizer = createUniqueUser("org_find");
        User attendee = createUniqueUser("att_find");

        Instant start = Instant.parse("2026-11-09T17:00:00Z");
        Instant end = start.plus(Duration.ofHours(1));

        TimeSlot attendeeSlot = timeSlotRepository.save(TimeSlot.builder()
                                                                .user(attendee)
                                                                .startTime(start)
                                                                .endTime(end)
                                                                .status(SlotStatus.BUSY)
                                                                .build());

        Meeting meeting = Meeting.of("1-on-1 Catchup", null, organizer);
        meeting.addConsumedSlot(attendeeSlot);
        Meeting saved = meetingRepository.save(meeting);

        var results = meetingRepository.findByParticipantUserId(attendee.getId());
        assertThat(results).extracting(Meeting::getId).contains(saved.getId());
    }

    @Test
    @DisplayName("should find meetings by organizer and participant user ids combined")
    void findByOrganizerIdAndParticipantUserId_shouldReturnOnlyMatchingMeetings() {
        User organizer = createUniqueUser("org_comb");
        User participant = createUniqueUser("part_comb");
        User otherParticipant = createUniqueUser("other_comb");

        Instant start = Instant.parse("2026-11-09T18:00:00Z");
        Instant end = start.plus(Duration.ofHours(1));

        TimeSlot orgSlot = timeSlotRepository.save(TimeSlot.builder()
                                                           .user(organizer)
                                                           .startTime(start)
                                                           .endTime(end)
                                                           .status(SlotStatus.BUSY)
                                                           .build());
        TimeSlot partSlot = timeSlotRepository.save(TimeSlot.builder()
                                                            .user(participant)
                                                            .startTime(start)
                                                            .endTime(end)
                                                            .status(SlotStatus.BUSY)
                                                            .build());

        Meeting meeting = Meeting.of("Combined Filter Sync", null, organizer);
        meeting.addConsumedSlot(orgSlot);
        meeting.addConsumedSlot(partSlot);
        Meeting saved = meetingRepository.save(meeting);

        var matching = meetingRepository.findByOrganizerIdAndParticipantUserId(organizer.getId(),
                                                                               participant.getId());
        assertThat(matching).extracting(Meeting::getId).contains(saved.getId());

        var nonMatching = meetingRepository.findByOrganizerIdAndParticipantUserId(organizer.getId(),
                                                                                  otherParticipant.getId());
        assertThat(nonMatching).extracting(Meeting::getId).doesNotContain(saved.getId()).isEmpty();
    }
}
