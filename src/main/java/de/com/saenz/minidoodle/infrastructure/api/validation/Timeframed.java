package de.com.saenz.minidoodle.infrastructure.api.validation;

import java.time.Instant;

/**
 * Contract for request DTOs carrying a start/end timeframe pair.
 * Implemented by records so {@link ValidTimeframeValidator} can inspect them uniformly.
 */
public interface Timeframed {

    Instant startTime();

    Instant endTime();
}
