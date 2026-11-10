package de.com.saenz.minidoodle.domain.usecase;

import de.com.saenz.minidoodle.domain.model.Meeting;

import java.util.List;
import java.util.Optional;

/**
 * Port query interface for retrieving scheduled meetings.
 */
public interface GetMeetingQuery {

    Optional<Meeting> getMeetingById(Long id);

    List<Meeting> getAllMeetings();

    List<Meeting> getMeetingsByOrganizer(Long organizerId);

    List<Meeting> getMeetingsByParticipant(Long userId);

    List<Meeting> getMeetingsByOrganizerAndParticipant(Long organizerId, Long participantId);
}
