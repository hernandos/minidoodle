package de.com.saenz.minidoodle.infrastructure.persistence.jpa.adapter;

import de.com.saenz.minidoodle.domain.model.SlotStatus;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.repository.TimeSlotRepository;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.entity.TimeSlotEntity;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.mapper.TimeSlotEntityMapper;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.repository.SpringDataTimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence adapter connecting domain TimeSlotRepository to Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class TimeSlotRepositoryAdapter implements TimeSlotRepository {

    private final SpringDataTimeSlotRepository springDataTimeSlotRepository;
    private final TimeSlotEntityMapper timeSlotEntityMapper;

    @Override
    public TimeSlot save(TimeSlot timeSlot) {
        TimeSlotEntity entity = timeSlotEntityMapper.toEntity(timeSlot);
        TimeSlotEntity saved = springDataTimeSlotRepository.save(entity);
        return timeSlotEntityMapper.toDomain(saved);
    }

    @Override
    public List<TimeSlot> saveAll(List<TimeSlot> timeSlots) {
        List<TimeSlotEntity> entities = timeSlotEntityMapper.toEntityList(timeSlots);
        List<TimeSlotEntity> saved = springDataTimeSlotRepository.saveAll(entities);
        return timeSlotEntityMapper.toDomainList(saved);
    }

    @Override
    public Optional<TimeSlot> findById(Long id) {
        return springDataTimeSlotRepository.findById(id).map(timeSlotEntityMapper::toDomain);
    }

    @Override
    public List<TimeSlot> findByUserId(Long userId) {
        return timeSlotEntityMapper.toDomainList(springDataTimeSlotRepository.findByUserId(userId));
    }

    @Override
    public List<TimeSlot> findByUserIdAndStatus(Long userId, SlotStatus status) {
        return timeSlotEntityMapper.toDomainList(springDataTimeSlotRepository.findByUserIdAndStatus(userId, status));
    }

    @Override
    public List<TimeSlot> findByUserIdAndTimeRange(Long userId, Instant startTime, Instant endTime) {
        return timeSlotEntityMapper.toDomainList(
                springDataTimeSlotRepository.findByUserIdAndStartTimeLessThanAndEndTimeGreaterThan(userId,
                                                                                                   endTime,
                                                                                                   startTime)
        );
    }

    @Override
    public List<TimeSlot> findByTimeRange(Instant startTime, Instant endTime) {
        return timeSlotEntityMapper.toDomainList(
                springDataTimeSlotRepository.findByStartTimeLessThanAndEndTimeGreaterThan(endTime, startTime));
    }

    @Override
    public List<TimeSlot> findByTimeRangeAndStatus(Instant startTime, Instant endTime, SlotStatus status) {
        return timeSlotEntityMapper.toDomainList(
                springDataTimeSlotRepository.findByStartTimeLessThanAndEndTimeGreaterThanAndStatus(endTime,
                                                                                                   startTime,
                                                                                                   status));
    }

    @Override
    public List<TimeSlot> findByUserIdsAndTimeRange(List<Long> userIds, Instant startTime, Instant endTime) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return timeSlotEntityMapper.toDomainList(
                springDataTimeSlotRepository.findByUserIdInAndStartTimeLessThanAndEndTimeGreaterThan(userIds,
                                                                                                     endTime,
                                                                                                     startTime));
    }

    @Override
    public List<TimeSlot> findByUserIdsAndTimeRangeAndStatus(List<Long> userIds,
                                                             Instant startTime,
                                                             Instant endTime,
                                                             SlotStatus status) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return timeSlotEntityMapper.toDomainList(
                springDataTimeSlotRepository.findByUserIdInAndStartTimeLessThanAndEndTimeGreaterThanAndStatus(userIds,
                                                                                                              endTime,
                                                                                                              startTime,
                                                                                                              status));
    }

    @Override
    public Optional<TimeSlot> findByUserIdAndStartTimeAndEndTime(Long userId, Instant startTime, Instant endTime) {
        return springDataTimeSlotRepository.findByUserIdAndStartTimeAndEndTime(userId, startTime, endTime)
                                           .map(timeSlotEntityMapper::toDomain);
    }

    @Override
    public List<TimeSlot> findByUserIdsAndStartTimeAndEndTime(List<Long> userIds,
                                                              Instant startTime,
                                                              Instant endTime) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return timeSlotEntityMapper.toDomainList(
                springDataTimeSlotRepository.findByUserIdInAndStartTimeAndEndTime(userIds, startTime, endTime)
        );
    }


    @Override
    public void deleteById(Long id) {
        springDataTimeSlotRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return springDataTimeSlotRepository.existsById(id);
    }
}
