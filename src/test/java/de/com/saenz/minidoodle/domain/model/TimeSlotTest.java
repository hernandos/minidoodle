package de.com.saenz.minidoodle.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotTest {

    @Test
    @DisplayName("should accurately detect overlapping time slots")
    void overlapsWith_shouldReturnTrue_whenSlotsOverlap() {
        Instant now = Instant.parse("2026-11-09T10:00:00Z");
        TimeSlot slot1 = TimeSlot.builder()
                .startTime(now)
                .endTime(now.plus(Duration.ofHours(1)))
                .status(SlotStatus.FREE)
                .build();

        TimeSlot overlappingSlot = TimeSlot.builder()
                .startTime(now.plus(Duration.ofMinutes(30)))
                .endTime(now.plus(Duration.ofHours(2)))
                .status(SlotStatus.FREE)
                .build();

        TimeSlot nonOverlappingSlot = TimeSlot.builder()
                .startTime(now.plus(Duration.ofHours(1)))
                .endTime(now.plus(Duration.ofHours(2)))
                .status(SlotStatus.FREE)
                .build();

        assertThat(slot1.overlapsWith(overlappingSlot)).isTrue();
        assertThat(slot1.overlapsWith(nonOverlappingSlot)).isFalse();
    }

    @Test
    @DisplayName("should update status when marked as busy or free")
    void markAsBusy_shouldUpdateStatus_whenCalled() {
        TimeSlot slot = TimeSlot.builder()
                .status(SlotStatus.FREE)
                .build();

        assertThat(slot.isFree()).isTrue();
        assertThat(slot.isBusy()).isFalse();

        slot.markAsBusy();
        assertThat(slot.isBusy()).isTrue();
        assertThat(slot.isFree()).isFalse();

        slot.markAsFree();
        assertThat(slot.isFree()).isTrue();
    }

    @Test
    @DisplayName("should calculate slot duration correctly")
    void getDuration_shouldCalculateDifference_betweenStartAndEndTime() {
        Instant start = Instant.parse("2026-11-09T10:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(45));

        TimeSlot slot = TimeSlot.builder()
                .startTime(start)
                .endTime(end)
                .build();

        assertThat(slot.getDuration()).isEqualTo(Duration.ofMinutes(45));
    }

    @Test
    @DisplayName("should update timeframe when new start and end times are valid")
    void updateTimeframe_shouldUpdateTimes_whenTimesAreValid() {
        Instant start = Instant.parse("2026-11-09T10:00:00Z");
        Instant end = start.plus(Duration.ofHours(1));

        TimeSlot slot = TimeSlot.builder()
                .startTime(start)
                .endTime(end)
                .build();

        Instant newStart = start.plus(Duration.ofHours(2));
        Instant newEnd = newStart.plus(Duration.ofHours(1));

        slot.updateTimeframe(newStart, newEnd);

        assertThat(slot.getStartTime()).isEqualTo(newStart);
        assertThat(slot.getEndTime()).isEqualTo(newEnd);
    }

    @Test
    @DisplayName("should reject updateTimeframe when new start is after new end")
    void updateTimeframe_shouldThrowException_whenStartTimeAfterEndTime() {
        Instant start = Instant.parse("2026-11-09T10:00:00Z");
        Instant end = start.plus(Duration.ofHours(1));

        TimeSlot slot = TimeSlot.builder()
                .startTime(start)
                .endTime(end)
                .build();

        assertThatThrownBy(() -> slot.updateTimeframe(end, start))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly before");
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when start time is not before end time")
    void validate_shouldThrowException_whenStartTimeAfterEndTime() {
        Instant now = Instant.parse("2026-11-09T10:00:00Z");
        TimeSlot invalidSlot = TimeSlot.builder()
                .startTime(now)
                .endTime(now.minus(Duration.ofMinutes(30)))
                .build();

        assertThatThrownBy(invalidSlot::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly before");
    }
}
