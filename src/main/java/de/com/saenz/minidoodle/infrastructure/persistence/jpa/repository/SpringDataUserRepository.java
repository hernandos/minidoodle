package de.com.saenz.minidoodle.infrastructure.persistence.jpa.repository;

import de.com.saenz.minidoodle.infrastructure.persistence.jpa.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for UserEntity.
 */
@Repository
public interface SpringDataUserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByUsername(String username);

    @Query("select u.id from UserEntity u where u.id in :ids")
    List<Long> findIdsByIdIn(@Param("ids") Collection<Long> ids);
}
