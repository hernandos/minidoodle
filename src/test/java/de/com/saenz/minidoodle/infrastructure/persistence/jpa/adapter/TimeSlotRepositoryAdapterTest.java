package de.com.saenz.minidoodle.infrastructure.persistence.jpa.adapter;

import de.com.saenz.minidoodle.PostgresIntegrationTest;
import de.com.saenz.minidoodle.domain.model.SlotStatus;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.repository.TimeSlotRepository;
import de.com.saenz.minidoodle.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
class TimeSlotRepositoryAdapterTest {

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private UserRepository userRepository;

    private User createUniqueUser(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                                       .username(prefix + "_" + uid)
                                       .name("Test User " + uid)
                                       .email(prefix + "_" + uid + "@example.com")
                                       .build());
    }

    @Test
    @DisplayName("should save and retrieve a time slot successfully")
    void save_shouldPersistAndRetrieveTimeSlot_whenValid() {
        User user = createUniqueUser("adapter_user");

        Instant now = Instant.parse("2026-11-09T10:00:00Z");
        TimeSlot slot = TimeSlot.builder()
                                .user(user)
                                .startTime(now)
                                .endTime(now.plus(Duration.ofHours(1)))
                                .status(SlotStatus.FREE)
                                .build();

        TimeSlot saved = timeSlotRepository.save(slot);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(SlotStatus.FREE);

        Optional<TimeSlot> found = timeSlotRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("should trigger optimistic locking failure when updating stale version")
    void save_shouldThrowOptimisticLockException_whenConcurrentModificationOccurs() {
        User user = createUniqueUser("locking_user");

        Instant now = Instant.parse("2026-11-09T14:00:00Z");
        TimeSlot initialSlot = timeSlotRepository.save(TimeSlot.builder()
                                                               .user(user)
                                                               .startTime(now)
                                                               .endTime(now.plus(Duration.ofHours(1)))
                                                               .status(SlotStatus.FREE)
                                                               .build());

        // First update (e.g. booked by user A)
        TimeSlot slotTx1 = timeSlotRepository.findById(initialSlot.getId()).orElseThrow();
        slotTx1.markAsBusy();
        timeSlotRepository.save(slotTx1);

        // Second update attempt using original stale version (e.g. concurrent attempt by user B)
        TimeSlot staleSlot = TimeSlot.builder()
                                     .id(initialSlot.getId())
                                     .user(user)
                                     .startTime(now)
                                     .endTime(now.plus(Duration.ofHours(1)))
                                     .status(SlotStatus.BUSY)
                                     .version(initialSlot.getVersion()) // stale version!
                                     .build();

        assertThatThrownBy(() -> timeSlotRepository.save(staleSlot))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("should enforce database-level exclusion constraint when overlapping slots are saved for same user")
    void save_shouldFailAtDatabaseLevel_whenTimeSlotsOverlapForSameUser() {
        User user = createUniqueUser("overlap_db");

        Instant start = Instant.parse("2026-11-09T09:00:00Z");
        Instant end = start.plus(Duration.ofHours(1));

        timeSlotRepository.save(TimeSlot.builder()
                                        .user(user)
                                        .startTime(start)
                                        .endTime(end)
                                        .status(SlotStatus.FREE)
                                        .build());

        TimeSlot overlapping = TimeSlot.builder()
                                       .user(user)
                                       .startTime(start.plus(Duration.ofMinutes(30)))
                                       .endTime(end.plus(Duration.ofMinutes(30)))
                                       .status(SlotStatus.FREE)
                                       .build();

        assertThatThrownBy(() -> timeSlotRepository.save(overlapping))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
