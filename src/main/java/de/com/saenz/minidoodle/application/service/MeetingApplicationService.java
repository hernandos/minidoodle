package de.com.saenz.minidoodle.application.service;

import de.com.saenz.minidoodle.domain.model.Meeting;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.repository.MeetingRepository;
import de.com.saenz.minidoodle.domain.repository.TimeSlotRepository;
import de.com.saenz.minidoodle.domain.repository.UserRepository;
import de.com.saenz.minidoodle.domain.usecase.CancelMeetingUseCase;
import de.com.saenz.minidoodle.domain.usecase.ScheduleMeetingCommand;
import de.com.saenz.minidoodle.domain.usecase.ScheduleMeetingUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Application service orchestrating meeting scheduling and cancellation.
 * Enforces proactive availability by requiring pre-existing FREE slots for organizer and all participants,
 * atomically transitioning them to BUSY, and persisting the meeting.
 * Releasing slots back to FREE upon meeting cancellation.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MeetingApplicationService implements ScheduleMeetingUseCase, CancelMeetingUseCase {

    private final MeetingRepository meetingRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final Optional<MeterRegistry> meterRegistry;

    @Override
    public Meeting scheduleMeeting(ScheduleMeetingCommand command) {
        validateCommand(command);

        User organizer = userRepository.findById(command.organizerId())
                                       .orElseThrow(() -> new IllegalArgumentException("Organizer not found with id: " + command.organizerId()));

        Set<Long> allUserIds = new LinkedHashSet<>();
        allUserIds.add(command.organizerId());
        allUserIds.addAll(command.participantIds());

        // Validate that all participant users exist
        for (Long participantId : command.participantIds()) {
            if (!participantId.equals(command.organizerId()) && !userRepository.existsById(participantId)) {
                throw new IllegalArgumentException("Participant not found with id: " + participantId);
            }
        }

        // Collect and validate matching FREE slots for organizer and all participants
        List<TimeSlot> slotsToConsume = new ArrayList<>();
        Instant start = command.startTime();
        Instant end = command.endTime();

        for (Long userId : allUserIds) {
            TimeSlot slot = timeSlotRepository.findByUserIdAndStartTimeAndEndTime(userId, start, end)
                                              .orElseThrow(() -> new IllegalStateException(
                                                      String.format(
                                                              "User %d is not available: no matching slot found for timeframe [%s - %s]",
                                                              userId,
                                                              start,
                                                              end)
                                              ));

            if (!slot.isFree()) {
                throw new IllegalStateException(
                        String.format("User %d is not available: slot for timeframe [%s - %s] is already busy",
                                      userId, start, end)
                );
            }

            slot.markAsBusy();
            slotsToConsume.add(slot);
        }

        // Persist slot state transitions (optimistic locking @Version protects against concurrent booking)
        List<TimeSlot> persistedSlots = timeSlotRepository.saveAll(slotsToConsume);

        // Build and persist Meeting
        Meeting meeting = Meeting.of(command.title(), command.description(), organizer);
        for (TimeSlot slot : persistedSlots) {
            meeting.addConsumedSlot(slot);
        }

        Meeting savedMeeting = meetingRepository.save(meeting);

        meterRegistry.ifPresent(registry -> registry.counter("meetings.scheduled.total").increment());

        return savedMeeting;
    }

    @Override
    public void cancelMeeting(Long meetingId) {
        if (meetingId == null) {
            throw new IllegalArgumentException("Meeting ID must not be null");
        }
        Meeting meeting = meetingRepository.findById(meetingId)
                                           .orElseThrow(() -> new IllegalArgumentException("Meeting not found with id: " + meetingId));

        for (TimeSlot slot : meeting.getTimeSlots()) {
            slot.markAsFree();
        }
        timeSlotRepository.saveAll(meeting.getTimeSlots());
        meetingRepository.deleteById(meetingId);
    }

    private void validateCommand(ScheduleMeetingCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("ScheduleMeetingCommand must not be null");
        }
        if (command.title() == null || command.title().isBlank()) {
            throw new IllegalArgumentException("Meeting title must not be blank");
        }
        if (command.organizerId() == null) {
            throw new IllegalArgumentException("Organizer ID must not be null");
        }
        if (command.startTime() == null || command.endTime() == null) {
            throw new IllegalArgumentException("Meeting start time and end time must not be null");
        }
        if (!command.startTime().isBefore(command.endTime())) {
            throw new IllegalArgumentException("Meeting start time must be strictly before end time");
        }
        if (command.participantIds() != null && command.participantIds().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Participant IDs must not contain null elements");
        }
    }
}
