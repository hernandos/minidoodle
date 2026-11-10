package de.com.saenz.minidoodle.application.service;

import de.com.saenz.minidoodle.domain.model.SlotStatus;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.repository.TimeSlotRepository;
import de.com.saenz.minidoodle.domain.repository.UserRepository;
import de.com.saenz.minidoodle.domain.usecase.CreateTimeSlotUseCase;
import de.com.saenz.minidoodle.domain.usecase.DeleteTimeSlotUseCase;
import de.com.saenz.minidoodle.domain.usecase.UpdateTimeSlotUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Application service orchestrating TimeSlot lifecycle operations (create, update, delete).
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TimeSlotApplicationService implements CreateTimeSlotUseCase, UpdateTimeSlotUseCase, DeleteTimeSlotUseCase {

    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;

    @Override
    public TimeSlot createTimeSlot(Long userId, Instant startTime, Instant endTime) {
        validateTimeframe(startTime, endTime);
        if (userId == null) {
            log.warn("createTimeSlot failed: userId is null");
            throw new IllegalArgumentException("User ID must not be null");
        }

        log.debug("Checking user {} existence and slot overlap in range [{} - {}]", userId, startTime, endTime);

        User user = userRepository.findById(userId)
                                  .orElseThrow(() -> {
                                      log.warn("createTimeSlot failed: User not found with id {}", userId);
                                      return new IllegalArgumentException("User not found with id: " + userId);
                                  });

        List<TimeSlot> overlapping = timeSlotRepository.findByUserIdAndTimeRange(userId, startTime, endTime);
        if (!overlapping.isEmpty()) {
            TimeSlot conflict = overlapping.getFirst();
            log.warn("createTimeSlot failed: Overlap detected for user {} in timeframe [{} - {}]", userId, startTime, endTime);
            throw new IllegalArgumentException(String.format(
                    "Time slot [%s - %s] overlaps with existing slot [%s - %s] for user %d",
                    startTime, endTime, conflict.getStartTime(), conflict.getEndTime(), userId
            ));
        }

        TimeSlot timeSlot = TimeSlot.builder()
                                    .user(user)
                                    .startTime(startTime)
                                    .endTime(endTime)
                                    .status(SlotStatus.FREE)
                                    .build();

        TimeSlot saved = timeSlotRepository.save(timeSlot);
        log.info("Created time slot id: {} for user: {} [{} - {}]", saved.getId(), userId, startTime, endTime);
        return saved;
    }

    @Override
    public TimeSlot updateTimeSlot(Long slotId, Instant newStartTime, Instant newEndTime) {
        validateTimeframe(newStartTime, newEndTime);
        if (slotId == null) {
            log.warn("updateTimeSlot failed: slotId is null");
            throw new IllegalArgumentException("Time slot ID must not be null");
        }

        log.debug("Verifying slot {} status and checking overlap with new timeframe [{} - {}]", slotId, newStartTime, newEndTime);

        TimeSlot slot = timeSlotRepository.findById(slotId)
                                          .orElseThrow(() -> {
                                              log.warn("updateTimeSlot failed: Time slot not found with id {}", slotId);
                                              return new IllegalArgumentException("Time slot not found with id: " + slotId);
                                          });

        if (slot.isBusy()) {
            log.warn("updateTimeSlot failed: Cannot modify booked/busy slot id {}", slotId);
            throw new IllegalStateException("Cannot modify a booked or busy time slot: " + slotId);
        }

        Long userId = slot.getUser().getId();
        List<TimeSlot> overlapping = timeSlotRepository.findByUserIdAndTimeRange(userId, newStartTime, newEndTime);
        boolean hasOverlap = overlapping.stream().anyMatch(other -> !other.getId().equals(slotId));
        if (hasOverlap) {
            log.warn("updateTimeSlot failed: New timeframe [{} - {}] overlaps with another slot for user {}",
                     newStartTime, newEndTime, userId);
            throw new IllegalArgumentException(String.format(
                    "New timeframe [%s - %s] overlaps with an existing slot for user %d",
                    newStartTime, newEndTime, userId
            ));
        }

        slot.updateTimeframe(newStartTime, newEndTime);
        TimeSlot updated = timeSlotRepository.save(slot);
        log.info("Updated time slot id: {} to [{} - {}]", slotId, newStartTime, newEndTime);
        return updated;
    }

    @Override
    public void deleteTimeSlot(Long slotId) {
        if (slotId == null) {
            log.warn("deleteTimeSlot failed: slotId is null");
            throw new IllegalArgumentException("Time slot ID must not be null");
        }

        log.debug("Checking slot {} status before deletion", slotId);

        TimeSlot slot = timeSlotRepository.findById(slotId)
                                          .orElseThrow(() -> {
                                              log.warn("deleteTimeSlot failed: Time slot not found with id {}", slotId);
                                              return new IllegalArgumentException("Time slot not found with id: " + slotId);
                                          });

        if (slot.isBusy()) {
            log.warn("deleteTimeSlot failed: Cannot delete booked/busy slot id {}", slotId);
            throw new IllegalStateException("Cannot delete a booked or busy time slot: " + slotId);
        }

        timeSlotRepository.deleteById(slotId);
        log.info("Deleted time slot id: {}", slotId);
    }

    private void validateTimeframe(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            log.warn("Timeframe validation failed: null start or end time");
            throw new IllegalArgumentException("Start time and end time must not be null");
        }
        if (!startTime.isBefore(endTime)) {
            log.warn("Timeframe validation failed: start [{}] is not before end [{}]", startTime, endTime);
            throw new IllegalArgumentException("Start time must be strictly before end time");
        }
    }
}
