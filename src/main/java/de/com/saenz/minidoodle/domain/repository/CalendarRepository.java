package de.com.saenz.minidoodle.domain.repository;

import de.com.saenz.minidoodle.domain.model.Calendar;

import java.time.Instant;
import java.util.Optional;

/**
 * Domain repository port for the logical Calendar aggregate view.
 * Pure interface without Spring, JPA, or database framework dependencies.
 */
public interface CalendarRepository {

    Optional<Calendar> findByUserId(Long userId);

    Optional<Calendar> findByUserIdAndTimeRange(Long userId, Instant startTime, Instant endTime);
}
