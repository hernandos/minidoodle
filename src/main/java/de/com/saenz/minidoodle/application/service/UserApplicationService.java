package de.com.saenz.minidoodle.application.service;

import de.com.saenz.minidoodle.domain.model.User;
import de.com.saenz.minidoodle.domain.repository.UserRepository;
import de.com.saenz.minidoodle.domain.usecase.CreateUserUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service implementing user registration.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserApplicationService implements CreateUserUseCase {

    private final UserRepository userRepository;

    @Override
    public User createUser(String username, String name, String email) {
        log.debug("Creating user: username='{}', name='{}', email='{}'", username, name, email);

        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalStateException("Username '" + username + "' is already taken");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalStateException("Email '" + email + "' is already registered");
        }

        User user = User.builder()
                        .username(username)
                        .name(name)
                        .email(email)
                        .build();

        User saved = userRepository.save(user);
        log.debug("User successfully created with id: {}", saved.getId());
        return saved;
    }
}
