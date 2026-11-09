package de.com.saenz.minidoodle.infrastructure.persistence.jpa.mapper;

import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.entity.UserEntity;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper converting between User domain model and UserEntity JPA entity.
 */
@Mapper(componentModel = "spring")
public interface UserEntityMapper {

    User toDomain(UserEntity entity);

    UserEntity toEntity(User domain);
}
