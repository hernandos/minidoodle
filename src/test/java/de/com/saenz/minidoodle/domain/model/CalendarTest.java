package de.com.saenz.minidoodle.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalendarTest {

    @Test
    @DisplayName("should enforce non-overlapping invariant when adding slots to Calendar")
    void addTimeSlot_shouldThrowException_whenSlotOverlaps() {
        User user = User.builder().id(1L).username("johndoe").build();
        Calendar calendar = Calendar.of(user);

        Instant start1 = Instant.parse("2026-11-09T09:00:00Z");
        Instant end1 = start1.plus(Duration.ofHours(1));

        TimeSlot slot1 = TimeSlot.builder()
                .id(100L)
                .user(user)
                .startTime(start1)
                .endTime(end1)
                .status(SlotStatus.FREE)
                .build();

        calendar.addTimeSlot(slot1);

        TimeSlot overlappingSlot = TimeSlot.builder()
                .id(101L)
                .user(user)
                .startTime(start1.plus(Duration.ofMinutes(30)))
                .endTime(end1.plus(Duration.ofMinutes(30)))
                .status(SlotStatus.FREE)
                .build();

        assertThatThrownBy(() -> calendar.addTimeSlot(overlappingSlot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    @DisplayName("should reject overlapping slot even when it shares the id of an existing slot")
    void addTimeSlot_shouldThrowException_whenSlotWithSameIdOverlaps() {
        User user = User.builder().id(1L).username("johndoe").build();
        Calendar calendar = Calendar.of(user);

        Instant start = Instant.parse("2026-11-09T09:00:00Z");
        TimeSlot slot = TimeSlot.builder()
                .id(100L)
                .user(user)
                .startTime(start)
                .endTime(start.plus(Duration.ofHours(1)))
                .status(SlotStatus.FREE)
                .build();
        calendar.addTimeSlot(slot);

        TimeSlot movedSlot = TimeSlot.builder()
                .id(100L)
                .user(user)
                .startTime(start.plus(Duration.ofMinutes(30)))
                .endTime(start.plus(Duration.ofMinutes(90)))
                .status(SlotStatus.FREE)
                .build();

        assertThatThrownBy(() -> calendar.addTimeSlot(movedSlot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    @DisplayName("should reject slot belonging to a different user")
    void addTimeSlot_shouldThrowException_whenUserMismatches() {
        User user = User.builder().id(1L).username("johndoe").build();
        User anotherUser = User.builder().id(2L).username("janedoe").build();
        Calendar calendar = Calendar.of(user);

        Instant start = Instant.parse("2026-11-09T09:00:00Z");
        TimeSlot slot = TimeSlot.builder()
                .user(anotherUser)
                .startTime(start)
                .endTime(start.plus(Duration.ofHours(1)))
                .status(SlotStatus.FREE)
                .build();

        assertThatThrownBy(() -> calendar.addTimeSlot(slot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to user");
    }

    @Test
    @DisplayName("should find matching free slot with exact timeframe")
    void findMatchingFreeSlot_shouldReturnSlot_whenTimeframeMatchesAndStatusIsFree() {
        User user = User.builder().id(1L).build();
        Calendar calendar = Calendar.of(user);

        Instant start = Instant.parse("2026-11-09T10:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(30));

        TimeSlot freeSlot = TimeSlot.builder()
                .id(1L)
                .user(user)
                .startTime(start)
                .endTime(end)
                .status(SlotStatus.FREE)
                .build();

        calendar.addTimeSlot(freeSlot);

        Optional<TimeSlot> found = calendar.findMatchingFreeSlot(start, end);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(1L);

        Optional<TimeSlot> notFound = calendar.findMatchingFreeSlot(start, end.plus(Duration.ofMinutes(15)));
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("should filter free slots in a timeframe")
    void getFreeSlotsBetween_shouldReturnOnlyFreeSlots_withinTimeWindow() {
        User user = User.builder().id(1L).build();
        Calendar calendar = Calendar.of(user);

        Instant t0 = Instant.parse("2026-11-09T08:00:00Z");
        Instant t1 = t0.plus(Duration.ofHours(1));
        Instant t2 = t1.plus(Duration.ofHours(1));
        Instant t3 = t2.plus(Duration.ofHours(1));

        TimeSlot slot1 = TimeSlot.builder().id(1L).user(user).startTime(t0).endTime(t1).status(SlotStatus.FREE).build();
        TimeSlot slot2 = TimeSlot.builder().id(2L).user(user).startTime(t1).endTime(t2).status(SlotStatus.BUSY).build();
        TimeSlot slot3 = TimeSlot.builder().id(3L).user(user).startTime(t2).endTime(t3).status(SlotStatus.FREE).build();

        calendar.addTimeSlot(slot1);
        calendar.addTimeSlot(slot2);
        calendar.addTimeSlot(slot3);

        List<TimeSlot> freeSlots = calendar.getFreeSlotsBetween(t0, t3);
        assertThat(freeSlots).extracting(TimeSlot::getId).containsExactly(1L, 3L);
    }
}
