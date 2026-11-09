package de.com.saenz.minidoodle.infrastructure.persistence.jpa.mapper;

import de.com.saenz.minidoodle.domain.model.Meeting;
import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.entity.MeetingEntity;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.entity.TimeSlotEntity;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.entity.UserEntity;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MapStruct mapper converting between Meeting domain model and MeetingEntity JPA entity.
 * Domain reconstruction goes through {@link Meeting#reconstitute} so the
 * same-timeframe invariant is validated on every load.
 */
@Mapper(componentModel = "spring", uses = {UserEntityMapper.class, TimeSlotEntityMapper.class})
public interface MeetingEntityMapper {

    User toDomainUser(UserEntity entity);

    List<TimeSlot> toDomainSlots(List<TimeSlotEntity> entities);

    default Meeting toDomain(MeetingEntity entity) {
        return Meeting.reconstitute(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                toDomainUser(entity.getOrganizer()),
                toDomainSlots(entity.getTimeSlots())
        );
    }

    MeetingEntity toEntity(Meeting domain);

    List<Meeting> toDomainList(List<MeetingEntity> entities);
}
