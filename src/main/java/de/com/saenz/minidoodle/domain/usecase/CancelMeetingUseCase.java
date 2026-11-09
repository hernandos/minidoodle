package de.com.saenz.minidoodle.domain.usecase;

/**
 * Port interface for cancelling a meeting and releasing its consumed slots back to FREE status.
 */
public interface CancelMeetingUseCase {

    void cancelMeeting(Long meetingId);
}
