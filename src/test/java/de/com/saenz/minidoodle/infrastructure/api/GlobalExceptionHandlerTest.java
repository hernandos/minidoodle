package de.com.saenz.minidoodle.infrastructure.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleIllegalArgumentException should return 400 Bad Request with ProblemDetail")
    void handleIllegalArgumentException_shouldReturnBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid user ID");

        ProblemDetail problem = exceptionHandler.handleIllegalArgumentException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Invalid Argument");
        assertThat(problem.getDetail()).isEqualTo("Invalid user ID");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("handleIllegalStateException should return 409 Conflict with ProblemDetail")
    void handleIllegalStateException_shouldReturnConflict() {
        IllegalStateException ex = new IllegalStateException("Slot is already busy");

        ProblemDetail problem = exceptionHandler.handleIllegalStateException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Business State Conflict");
        assertThat(problem.getDetail()).isEqualTo("Slot is already busy");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("handleOptimisticLockingFailureException should return 409 Conflict with retry recommendation")
    void handleOptimisticLockingFailureException_shouldReturnConflict() {
        OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Row updated by another transaction");

        ProblemDetail problem = exceptionHandler.handleOptimisticLockingFailureException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Concurrent Modification Conflict");
        assertThat(problem.getDetail()).contains("concurrently by another transaction");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("handleDataIntegrityViolationException should return 409 Conflict with ProblemDetail")
    void handleDataIntegrityViolationException_shouldReturnConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Unique index violation");

        ProblemDetail problem = exceptionHandler.handleDataIntegrityViolationException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Data Integrity Violation");
        assertThat(problem.getDetail()).isEqualTo("The request conflicts with existing data constraints");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("handleGeneralException should return 500 Internal Server Error with ProblemDetail")
    void handleGeneralException_shouldReturnInternalServerError() {
        RuntimeException ex = new RuntimeException("Unexpected NPE in underlying subsystem");

        ProblemDetail problem = exceptionHandler.handleGeneralException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred. Please contact support.");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }
}
