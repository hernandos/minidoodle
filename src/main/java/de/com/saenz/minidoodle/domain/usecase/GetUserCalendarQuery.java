package de.com.saenz.minidoodle.domain.usecase;

import de.com.saenz.minidoodle.domain.model.Calendar;

import java.time.Instant;
import java.util.Optional;

/**
 * Port query interface for retrieving an individual user's logical Calendar view.
 */
public interface GetUserCalendarQuery {

    Optional<Calendar> getUserCalendar(Long userId);

    Optional<Calendar> getUserCalendar(Long userId, Instant startTime, Instant endTime);
}
