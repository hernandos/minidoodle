package de.com.saenz.minidoodle.application.service;

import de.com.saenz.minidoodle.domain.model.Meeting;
import de.com.saenz.minidoodle.domain.repository.MeetingRepository;
import de.com.saenz.minidoodle.domain.usecase.GetMeetingQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Query service for retrieving scheduled meetings.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MeetingQueryService implements GetMeetingQuery {

    private final MeetingRepository meetingRepository;

    @Override
    public Optional<Meeting> getMeetingById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return meetingRepository.findById(id);
    }

    @Override
    public List<Meeting> getAllMeetings() {
        return meetingRepository.findAll();
    }

    @Override
    public List<Meeting> getMeetingsByOrganizer(Long organizerId) {
        if (organizerId == null) {
            return List.of();
        }
        return meetingRepository.findByOrganizerId(organizerId);
    }

    @Override
    public List<Meeting> getMeetingsByParticipant(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return meetingRepository.findByParticipantUserId(userId);
    }

    @Override
    public List<Meeting> getMeetingsByOrganizerAndParticipant(Long organizerId, Long participantId) {
        if (organizerId == null || participantId == null) {
            return List.of();
        }
        return meetingRepository.findByOrganizerIdAndParticipantUserId(organizerId, participantId);
    }
}
