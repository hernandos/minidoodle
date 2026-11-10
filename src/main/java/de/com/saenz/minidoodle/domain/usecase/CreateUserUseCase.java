package de.com.saenz.minidoodle.domain.usecase;

import de.com.saenz.minidoodle.domain.model.User;

/**
 * Port use case interface for registering new users.
 */
public interface CreateUserUseCase {

    User createUser(String username, String name, String email);
}
