package de.com.saenz.minidoodle.infrastructure.persistence.jpa.adapter;

import de.com.saenz.minidoodle.PostgresIntegrationTest;
import de.com.saenz.minidoodle.domain.model.Calendar;
import de.com.saenz.minidoodle.domain.model.SlotStatus;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.repository.CalendarRepository;
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
class CalendarRepositoryAdapterTest {

    @Autowired
    private CalendarRepository calendarRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    private User createUniqueUser(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .username(prefix + "_" + uid)
                .name("Calendar User " + uid)
                .email(prefix + "_" + uid + "@example.com")
                .build());
    }

    @Test
    @DisplayName("should reconstruct user Calendar aggregate from persisted TimeSlots")
    void findByUserId_shouldReconstructCalendar_withUserSlots() {
        User user = createUniqueUser("cal_user");

        Instant t0 = Instant.parse("2026-11-09T08:00:00Z");
        Instant t1 = t0.plus(Duration.ofHours(1));
        Instant t2 = t1.plus(Duration.ofHours(1));

        timeSlotRepository.save(TimeSlot.builder()
                .user(user)
                .startTime(t0)
                .endTime(t1)
                .status(SlotStatus.FREE)
                .build());

        timeSlotRepository.save(TimeSlot.builder()
                .user(user)
                .startTime(t1)
                .endTime(t2)
                .status(SlotStatus.BUSY)
                .build());

        Optional<Calendar> calendarOpt = calendarRepository.findByUserId(user.getId());

        assertThat(calendarOpt).isPresent();
        Calendar calendar = calendarOpt.get();
        assertThat(calendar.getUser().getId()).isEqualTo(user.getId());
        assertThat(calendar.getTimeSlots()).hasSize(2);
        assertThat(calendar.getFreeSlots()).hasSize(1);
        assertThat(calendar.getBusySlots()).hasSize(1);
    }
}
