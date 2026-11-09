package de.com.saenz.minidoodle.infrastructure.persistence.jpa.mapper;

import de.com.saenz.minidoodle.domain.model.TimeSlot;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.entity.TimeSlotEntity;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MapStruct mapper converting between TimeSlot domain model and TimeSlotEntity JPA entity.
 */
@Mapper(componentModel = "spring", uses = {UserEntityMapper.class})
public interface TimeSlotEntityMapper {

    TimeSlot toDomain(TimeSlotEntity entity);

    TimeSlotEntity toEntity(TimeSlot domain);

    List<TimeSlot> toDomainList(List<TimeSlotEntity> entities);

    List<TimeSlotEntity> toEntityList(List<TimeSlot> domains);
}
