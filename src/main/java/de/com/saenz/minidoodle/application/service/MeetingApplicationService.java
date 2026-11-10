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
import java.util.stream.Collectors;

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
                                           log.warn("Meeting schedule failed: Organizer not found with id {}",
                                                    command.organizerId());
                                           return new IllegalArgumentException("Organizer not found with id: " + command.organizerId());
                                       });

        Set<Long> allUserIds = new LinkedHashSet<>();
        allUserIds.add(command.organizerId());
        allUserIds.addAll(command.participantIds());

        log.debug("Verifying availability of {} total users: {}", allUserIds.size(), allUserIds);

        validateAllUserExists(allUserIds);

        List<TimeSlot> slotsToConsume = validateAndMarkSlotsAsBusy(allUserIds, command.startTime(), command.endTime());

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

    private List<TimeSlot> validateAndMarkSlotsAsBusy(Set<Long> allUserIds, Instant start, Instant end) {
        List<TimeSlot> slots = timeSlotRepository.findByUserIdsAndStartTimeAndEndTime(List.copyOf(allUserIds),
                                                                                      start,
                                                                                      end);

        validateUsersHaveMatchingSlots(allUserIds, start, end, slots);
        validateSlotsAreFree(start, end, slots);

        slots.forEach(slot -> {
            log.trace("Found matching FREE slot id: {} for user: {} in [{} - {}]",
                      slot.getId(), slot.getUser().getId(), start, end);
            slot.markAsBusy();
        });
        return slots;
    }

    private static void validateSlotsAreFree(Instant start, Instant end, List<TimeSlot> slots) {
        List<TimeSlot> busySlots = slots.stream()
                                        .filter(slot -> !slot.isFree())
                                        .toList();
        if (!busySlots.isEmpty()) {
            log.warn("Meeting schedule failed: Slots already busy for users {} in [{} - {}]",
                     busySlots.stream().map(slot -> slot.getUser().getId()).toList(), start, end);
            throw new IllegalStateException(
                    String.format("Users %s are not available: slots for timeframe [%s - %s] are already busy",
                                  busySlots.stream().map(slot -> slot.getUser().getId()).toList(), start, end)
            );
        }
    }

    private static void validateUsersHaveMatchingSlots(Set<Long> allUserIds, Instant start, Instant end, List<TimeSlot> slots) {
        Set<Long> usersWithSlots = slots.stream()
                                        .map(slot -> slot.getUser().getId())
                                        .collect(Collectors.toUnmodifiableSet());


        List<Long> usersWithoutSlots = allUserIds.stream()
                                                 .filter(userId -> !usersWithSlots.contains(userId))
                                                 .toList();
        if (!usersWithoutSlots.isEmpty()) {
            log.warn("Meeting schedule failed: Users {} have no matching slot for [{} - {}]",
                     usersWithoutSlots, start, end);
            throw new IllegalStateException(
                    String.format("Users %s are not available: no matching slot found for timeframe [%s - %s]",
                                  usersWithoutSlots, start, end)
            );
        }
    }

    private void validateAllUserExists(Collection<Long> userIds) {
        List<Long> missingIds = userRepository.findMissingIds(userIds);
        if (!missingIds.isEmpty()) {
            log.warn("Meeting schedule failed: Users not found with ids {}", missingIds);
            throw new IllegalArgumentException("Users not found with ids: " + missingIds);
        }
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
                                               log.warn("cancelMeeting failed: Meeting not found with id {}",
                                                        meetingId);
                                               return new IllegalArgumentException("Meeting not found with id: " + meetingId);
                                           });

        log.debug("Reverting {} consumed slots back to FREE status for meeting id: {}",
                  meeting.getTimeSlots().size(),
                  meetingId);
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
            log.warn("Command validation failed: start [{}] is not before end [{}]",
                     command.startTime(),
                     command.endTime());
            throw new IllegalArgumentException("Meeting start time must be strictly before end time");
        }
        if (command.participantIds() != null && command.participantIds().stream().anyMatch(Objects::isNull)) {
            log.warn("Command validation failed: participantIds contains null");
            throw new IllegalArgumentException("Participant IDs must not contain null elements");
        }
    }
}
