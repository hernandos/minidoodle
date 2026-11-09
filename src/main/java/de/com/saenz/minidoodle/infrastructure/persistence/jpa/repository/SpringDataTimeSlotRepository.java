package de.com.saenz.minidoodle.infrastructure.persistence.jpa.repository;

import de.com.saenz.minidoodle.domain.model.SlotStatus;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.entity.TimeSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for TimeSlotEntity.
 * Employs derived queries to leverage database indexes and avoid custom JPQL.
 */
@Repository
public interface SpringDataTimeSlotRepository extends JpaRepository<TimeSlotEntity, Long> {

    List<TimeSlotEntity> findByUserId(Long userId);

    List<TimeSlotEntity> findByUserIdAndStatus(Long userId, SlotStatus status);

    List<TimeSlotEntity> findByUserIdAndStartTimeLessThanAndEndTimeGreaterThan(Long userId, Instant end, Instant start);

    List<TimeSlotEntity> findByUserIdAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(Long userId,
                                                                                       Instant end,
                                                                                       Instant start,
                                                                                       Long excludeId);

    Optional<TimeSlotEntity> findByUserIdAndStartTimeAndEndTime(Long userId, Instant start, Instant end);
}
