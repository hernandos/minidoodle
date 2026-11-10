package de.com.saenz.minidoodle.infrastructure.persistence.jpa.adapter;

import de.com.saenz.minidoodle.domain.model.Meeting;
import de.com.saenz.minidoodle.domain.repository.MeetingRepository;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.entity.MeetingEntity;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.mapper.MeetingEntityMapper;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.repository.SpringDataMeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Persistence adapter connecting domain MeetingRepository to Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class MeetingRepositoryAdapter implements MeetingRepository {

    private final SpringDataMeetingRepository springDataMeetingRepository;
    private final MeetingEntityMapper meetingEntityMapper;

    @Override
    public Meeting save(Meeting meeting) {
        MeetingEntity entity = meetingEntityMapper.toEntity(meeting);
        MeetingEntity saved = springDataMeetingRepository.save(entity);
        return meetingEntityMapper.toDomain(saved);
    }

    @Override
    public Optional<Meeting> findById(Long id) {
        return springDataMeetingRepository.findById(id).map(meetingEntityMapper::toDomain);
    }

    @Override
    public List<Meeting> findByOrganizerId(Long organizerId) {
        return meetingEntityMapper.toDomainList(springDataMeetingRepository.findByOrganizerId(organizerId));
    }

    @Override
    public List<Meeting> findByParticipantUserId(Long userId) {
        return meetingEntityMapper.toDomainList(springDataMeetingRepository.findDistinctByTimeSlots_User_Id(userId));
    }

    @Override
    public List<Meeting> findByOrganizerIdAndParticipantUserId(Long organizerId, Long participantId) {
        return meetingEntityMapper.toDomainList(
                springDataMeetingRepository.findDistinctByOrganizer_IdAndTimeSlots_User_Id(organizerId, participantId));
    }

    @Override
    public void deleteById(Long id) {
        springDataMeetingRepository.deleteById(id);
    }

}
