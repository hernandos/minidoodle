package de.com.saenz.minidoodle.infrastructure.api;

import de.com.saenz.minidoodle.domain.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String REQUEST_CONFLICTS_WITH_EXISTING_DATA = "The request conflicts with existing data constraints";
    private static final String CONCURRENT_MODIFICATION = "The resource was modified concurrently by another transaction. Please retry.";
    private static final String UNEXPECTED_ERROR = "An unexpected error occurred. Please contact support.";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {

        String errors = ex.getBindingResult().getFieldErrors().stream()
                          .map(err -> err.getField() + ": " + err.getDefaultMessage())
                          .reduce((a, b) -> a + "; " + b)
                          .orElse("Validation failed");

        log.warn("Method argument validation failed (400 Bad Request): {}", errors);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, errors);
        problemDetail.setTitle("Validation Failed");
        problemDetail.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problemDetail);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(@NonNull MissingServletRequestParameterException ex,
                                                                          @NonNull HttpHeaders headers,
                                                                          @NonNull HttpStatusCode status,
                                                                          @NonNull WebRequest request) {
        log.warn("Missing required request parameter (400 Bad Request): {}", ex.getMessage());
        return ResponseEntity.badRequest()
                             .body(problemDetail(BAD_REQUEST, "Missing Parameter", ex.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(@NonNull TypeMismatchException ex,
                                                        @NonNull HttpHeaders headers,
                                                        @NonNull HttpStatusCode status,
                                                        @NonNull WebRequest request) {
        log.warn("Request parameter type mismatch (400 Bad Request): {}", ex.getMessage());
        String paramName = ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName() : "unknown";
        return ResponseEntity.badRequest()
                             .body(problemDetail(BAD_REQUEST, "Invalid Parameter",
                                                 "Invalid value for parameter '" + paramName + "'"));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull HttpMessageNotReadableException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        log.warn("Malformed request body (400 Bad Request): {}", ex.getMessage());
        return ResponseEntity.badRequest()
                             .body(problemDetail(BAD_REQUEST, "Malformed Request",
                                                 "Request body is missing or not valid JSON"));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(@NonNull HttpMediaTypeNotSupportedException ex,
                                                                     @NonNull HttpHeaders headers,
                                                                     @NonNull HttpStatusCode status,
                                                                     @NonNull WebRequest request) {
        log.warn("Unsupported media type (415 Unsupported Media Type): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                             .body(problemDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                                                 "Unsupported Media Type",
                                                 ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found (404 Not Found): {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Resource Not Found");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolationException(ConstraintViolationException ex) {
        log.warn("Constraint violation error (400 Bad Request): {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Validation Failed");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Client validation error (400 Bad Request): {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Invalid Argument");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalStateException(IllegalStateException ex) {
        log.warn("Business state conflict (409 Conflict): {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(CONFLICT, ex.getMessage());
        problemDetail.setTitle("Business State Conflict");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailureException(OptimisticLockingFailureException ex) {
        log.warn("Concurrent modification detected (409 Conflict): {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(CONFLICT, CONCURRENT_MODIFICATION);
        problemDetail.setTitle("Concurrent Modification Conflict");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.error("Data integrity violation encountered (409 Conflict): {}", ex.getMessage(), ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(CONFLICT, REQUEST_CONFLICTS_WITH_EXISTING_DATA);
        problemDetail.setTitle("Data Integrity Violation");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    private ProblemDetail problemDetail(HttpStatusCode status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralException(Exception ex) {
        log.error("Unhandled server exception occurred (500 Internal Server Error): {}", ex.getMessage(), ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR);
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
