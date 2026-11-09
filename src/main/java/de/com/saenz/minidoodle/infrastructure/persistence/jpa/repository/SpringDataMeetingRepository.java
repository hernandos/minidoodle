package de.com.saenz.minidoodle.infrastructure.persistence.jpa.repository;

import de.com.saenz.minidoodle.infrastructure.persistence.jpa.entity.MeetingEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for MeetingEntity.
 * Uses @EntityGraph to eagerly fetch organizer and consumed time slots in a single query,
 * preventing N+1 problems and LazyInitializationExceptions.
 */
@Repository
public interface SpringDataMeetingRepository extends JpaRepository<MeetingEntity, Long> {

    @Override
    @EntityGraph(attributePaths = {"organizer", "timeSlots", "timeSlots.user"})
    Optional<MeetingEntity> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"organizer", "timeSlots", "timeSlots.user"})
    List<MeetingEntity> findAll();

    @EntityGraph(attributePaths = {"organizer", "timeSlots", "timeSlots.user"})
    List<MeetingEntity> findByOrganizerId(Long organizerId);

    @EntityGraph(attributePaths = {"organizer", "timeSlots", "timeSlots.user"})
    List<MeetingEntity> findDistinctByTimeSlots_User_Id(Long userId);
}
