package de.com.saenz.minidoodle.infrastructure.persistence.jpa.adapter;

import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.repository.UserRepository;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.entity.UserEntity;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.mapper.UserEntityMapper;
import de.com.saenz.minidoodle.infrastructure.persistence.jpa.repository.SpringDataUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Persistence adapter connecting domain UserRepository to Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository springDataUserRepository;
    private final UserEntityMapper userEntityMapper;

    @Override
    public User save(User user) {
        UserEntity entity = userEntityMapper.toEntity(user);
        UserEntity saved = springDataUserRepository.save(entity);
        return userEntityMapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(Long id) {
        return springDataUserRepository.findById(id).map(userEntityMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return springDataUserRepository.findByEmail(email).map(userEntityMapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return springDataUserRepository.findByUsername(username).map(userEntityMapper::toDomain);
    }

    @Override
    public List<User> findAll() {
        return springDataUserRepository.findAll().stream()
                                       .map(userEntityMapper::toDomain)
                                       .toList();
    }

    @Override
    public boolean existsById(Long id) {
        return springDataUserRepository.existsById(id);
    }

    @Override
    public List<Long> findMissingIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Set<Long> existingIds = Set.copyOf(springDataUserRepository.findIdsByIdIn(ids));
        return ids.stream()
                  .filter(id -> !existingIds.contains(id))
                  .distinct()
                  .toList();
    }

    @Override
    public void deleteById(Long id) {
        springDataUserRepository.deleteById(id);
    }
}
