package de.com.saenz.minidoodle.infrastructure.persistence.jpa.adapter;

import de.com.saenz.minidoodle.domain.model.Calendar;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.repository.CalendarRepository;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.mapper.TimeSlotEntityMapper;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.mapper.UserEntityMapper;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.repository.SpringDataTimeSlotRepository;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.repository.SpringDataUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence adapter connecting domain CalendarRepository to Spring Data JPA.
 * Reconstructs the logical domain aggregate Calendar from the user and their TimeSlots.
 */
@Component
@RequiredArgsConstructor
public class CalendarRepositoryAdapter implements CalendarRepository {

    private final SpringDataUserRepository springDataUserRepository;
    private final SpringDataTimeSlotRepository springDataTimeSlotRepository;
    private final UserEntityMapper userEntityMapper;
    private final TimeSlotEntityMapper timeSlotEntityMapper;

    @Override
    public Optional<Calendar> findByUserId(Long userId) {
        return springDataUserRepository.findById(userId).map(userEntity -> {
            User user = userEntityMapper.toDomain(userEntity);
            List<TimeSlot> slots = timeSlotEntityMapper.toDomainList(
                    springDataTimeSlotRepository.findByUserId(userId)
            );
            return Calendar.of(user, slots);
        });
    }

    @Override
    public Optional<Calendar> findByUserIdAndTimeRange(Long userId, Instant startTime, Instant endTime) {
        return springDataUserRepository.findById(userId).map(userEntity -> {
            User user = userEntityMapper.toDomain(userEntity);
            List<TimeSlot> slots = timeSlotEntityMapper.toDomainList(
                    springDataTimeSlotRepository.findByUserIdAndStartTimeLessThanAndEndTimeGreaterThan(userId,
                                                                                                       endTime,
                                                                                                       startTime)
            );
            return Calendar.of(user, slots);
        });
    }
}
