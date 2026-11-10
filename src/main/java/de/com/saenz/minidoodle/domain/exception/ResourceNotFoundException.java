package de.com.saenz.minidoodle.domain.exception;

/**
 * Domain exception thrown when a requested domain resource (User, TimeSlot, Meeting, Calendar) is not found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
