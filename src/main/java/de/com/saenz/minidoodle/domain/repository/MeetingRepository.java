package de.com.saenz.minidoodle.domain.repository;

import de.com.saenz.minidoodle.domain.model.Meeting;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository port for Meeting entities.
 * Pure interface without Spring, JPA, or database framework dependencies.
 */
public interface MeetingRepository {

    Meeting save(Meeting meeting);

    Optional<Meeting> findById(Long id);

    List<Meeting> findAll();

    List<Meeting> findByOrganizerId(Long organizerId);

    List<Meeting> findByParticipantUserId(Long userId);

    List<Meeting> findByOrganizerIdAndParticipantUserId(Long organizerId, Long participantId);

    void deleteById(Long id);

    boolean existsById(Long id);
}
