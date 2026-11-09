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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

        log.info("Attempting to schedule meeting '{}' for organizer {} with {} participants",
                 command.title(), command.organizerId(), command.participantIds().size());

        User organizer = userRepository.findById(command.organizerId())
                                       .orElseThrow(() -> {
                                           log.warn("Meeting schedule failed: Organizer not found with id {}", command.organizerId());
                                           return new IllegalArgumentException("Organizer not found with id: " + command.organizerId());
                                       });

        Set<Long> allUserIds = new LinkedHashSet<>();
        allUserIds.add(command.organizerId());
        allUserIds.addAll(command.participantIds());

        log.debug("Verifying availability of {} total users: {}", allUserIds.size(), allUserIds);

        // Validate that all participant users exist
        for (Long participantId : command.participantIds()) {
            if (!participantId.equals(command.organizerId()) && !userRepository.existsById(participantId)) {
                log.warn("Meeting schedule failed: Participant user not found with id {}", participantId);
                throw new IllegalArgumentException("Participant not found with id: " + participantId);
            }
        }

        // Collect and validate matching FREE slots for organizer and all participants
        List<TimeSlot> slotsToConsume = new ArrayList<>();
        Instant start = command.startTime();
        Instant end = command.endTime();

        for (Long userId : allUserIds) {
            TimeSlot slot = timeSlotRepository.findByUserIdAndStartTimeAndEndTime(userId, start, end)
                                              .orElseThrow(() -> {
                                                  log.warn("Meeting schedule failed: User {} has no matching slot for [{} - {}]", userId, start, end);
                                                  return new IllegalStateException(
                                                          String.format("User %d is not available: no matching slot found for timeframe [%s - %s]",
                                                                        userId, start, end)
                                                  );
                                              });

            if (!slot.isFree()) {
                log.warn("Meeting schedule failed: Slot for user {} is already busy in [{} - {}]", userId, start, end);
                throw new IllegalStateException(
                        String.format("User %d is not available: slot for timeframe [%s - %s] is already busy",
                                      userId, start, end)
                );
            }

            log.debug("Found matching FREE slot id: {} for user: {} in [{} - {}]", slot.getId(), userId, start, end);
            slot.markAsBusy();
            slotsToConsume.add(slot);
        }

        // Persist slot state transitions (optimistic locking @Version protects against concurrent booking)
        log.debug("Persisting {} transitioned BUSY slots with optimistic locking check", slotsToConsume.size());
        List<TimeSlot> persistedSlots = timeSlotRepository.saveAll(slotsToConsume);

        // Build and persist Meeting
        log.debug("Building Meeting entity '{}' and linking {} consumed slots", command.title(), persistedSlots.size());
        Meeting meeting = Meeting.of(command.title(), command.description(), organizer);
        for (TimeSlot slot : persistedSlots) {
            meeting.addConsumedSlot(slot);
        }

        Meeting savedMeeting = meetingRepository.save(meeting);
        log.info("Successfully scheduled meeting id: {} ('{}') consuming {} slots",
                 savedMeeting.getId(), savedMeeting.getTitle(), savedMeeting.getTimeSlots().size());

        meterRegistry.ifPresent(registry -> registry.counter("meetings.scheduled.total").increment());

        return savedMeeting;
    }

    @Override
    public void cancelMeeting(Long meetingId) {
        if (meetingId == null) {
            log.warn("cancelMeeting failed: meetingId is null");
            throw new IllegalArgumentException("Meeting ID must not be null");
        }

        log.info("Attempting to cancel meeting id: {}", meetingId);
        Meeting meeting = meetingRepository.findById(meetingId)
                                           .orElseThrow(() -> {
                                               log.warn("cancelMeeting failed: Meeting not found with id {}", meetingId);
                                               return new IllegalArgumentException("Meeting not found with id: " + meetingId);
                                           });

        log.debug("Reverting {} consumed slots back to FREE status for meeting id: {}", meeting.getTimeSlots().size(), meetingId);
        for (TimeSlot slot : meeting.getTimeSlots()) {
            slot.markAsFree();
        }
        timeSlotRepository.saveAll(meeting.getTimeSlots());
        meetingRepository.deleteById(meetingId);

        log.info("Cancelled meeting id: {} and reverted {} slots to FREE", meetingId, meeting.getTimeSlots().size());
    }

    private void validateCommand(ScheduleMeetingCommand command) {
        if (command == null) {
            log.warn("Command validation failed: command is null");
            throw new IllegalArgumentException("ScheduleMeetingCommand must not be null");
        }
        if (command.title() == null || command.title().isBlank()) {
            log.warn("Command validation failed: title is blank");
            throw new IllegalArgumentException("Meeting title must not be blank");
        }
        if (command.organizerId() == null) {
            log.warn("Command validation failed: organizerId is null");
            throw new IllegalArgumentException("Organizer ID must not be null");
        }
        if (command.startTime() == null || command.endTime() == null) {
            log.warn("Command validation failed: null start or end time");
            throw new IllegalArgumentException("Meeting start time and end time must not be null");
        }
        if (!command.startTime().isBefore(command.endTime())) {
            log.warn("Command validation failed: start [{}] is not before end [{}]", command.startTime(), command.endTime());
            throw new IllegalArgumentException("Meeting start time must be strictly before end time");
        }
        if (command.participantIds() != null && command.participantIds().stream().anyMatch(Objects::isNull)) {
            log.warn("Command validation failed: participantIds contains null");
            throw new IllegalArgumentException("Participant IDs must not contain null elements");
        }
    }
}
