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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Application service orchestrating TimeSlot lifecycle operations (create, update, delete).
 */
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
            throw new IllegalArgumentException("User ID must not be null");
        }

        User user = userRepository.findById(userId)
                                  .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        List<TimeSlot> overlapping = timeSlotRepository.findByUserIdAndTimeRange(userId, startTime, endTime);
        if (!overlapping.isEmpty()) {
            TimeSlot conflict = overlapping.getFirst();
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

        return timeSlotRepository.save(timeSlot);
    }

    @Override
    public TimeSlot updateTimeSlot(Long slotId, Instant newStartTime, Instant newEndTime) {
        validateTimeframe(newStartTime, newEndTime);
        if (slotId == null) {
            throw new IllegalArgumentException("Time slot ID must not be null");
        }

        TimeSlot slot = timeSlotRepository.findById(slotId)
                                          .orElseThrow(() -> new IllegalArgumentException(
                                                  "Time slot not found with id: " + slotId));

        if (slot.isBusy()) {
            throw new IllegalStateException("Cannot modify a booked or busy time slot: " + slotId);
        }

        Long userId = slot.getUser().getId();
        List<TimeSlot> overlapping = timeSlotRepository.findByUserIdAndTimeRange(userId, newStartTime, newEndTime);
        boolean hasOverlap = overlapping.stream().anyMatch(other -> !other.getId().equals(slotId));
        if (hasOverlap) {
            throw new IllegalArgumentException(String.format(
                    "New timeframe [%s - %s] overlaps with an existing slot for user %d",
                    newStartTime, newEndTime, userId
            ));
        }

        slot.updateTimeframe(newStartTime, newEndTime);
        return timeSlotRepository.save(slot);
    }

    @Override
    public void deleteTimeSlot(Long slotId) {
        if (slotId == null) {
            throw new IllegalArgumentException("Time slot ID must not be null");
        }

        TimeSlot slot = timeSlotRepository.findById(slotId)
                                          .orElseThrow(() -> new IllegalArgumentException(
                                                  "Time slot not found with id: " + slotId));

        if (slot.isBusy()) {
            throw new IllegalStateException("Cannot delete a booked or busy time slot: " + slotId);
        }

        timeSlotRepository.deleteById(slotId);
    }

    private void validateTimeframe(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time must not be null");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Start time must be strictly before end time");
        }
    }
}
