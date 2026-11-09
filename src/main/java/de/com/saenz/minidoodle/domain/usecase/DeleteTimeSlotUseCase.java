package de.com.saenz.minidoodle.domain.usecase;

/**
 * Port interface for deleting an existing TimeSlot.
 */
public interface DeleteTimeSlotUseCase {

    void deleteTimeSlot(Long slotId);
}
