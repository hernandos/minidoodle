package de.com.saenz.minidoodle.domain.usecase;

import de.com.saenz.minidoodle.domain.model.Meeting;

/**
 * Port interface for scheduling a meeting, consuming free slots of organizer and participants.
 */
public interface ScheduleMeetingUseCase {

    Meeting scheduleMeeting(ScheduleMeetingCommand command);
}
