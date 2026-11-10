package de.com.saenz.minidoodle.domain.repository;

import de.com.saenz.minidoodle.domain.model.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Domain repository port for User entities.
 * Pure interface without Spring, JPA, or database framework dependencies.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    List<User> findAll();

    boolean existsById(Long id);

    List<Long> findMissingIds(Collection<Long> ids);

    void deleteById(Long id);
}
