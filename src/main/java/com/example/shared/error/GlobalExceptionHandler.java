package com.example.shared.error;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Single exception boundary: every error leaves as RFC 9457 problem+json, with fixed
 *  detail text. Resource-agnostic, so it does not grow with the API. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final URI TYPE_NOT_FOUND = URI.create("https://api.example.com/problems/task-not-found");
    private static final URI TYPE_VALIDATION = URI.create("https://api.example.com/problems/validation-failed");
    private static final URI TYPE_CONFLICT = URI.create("https://api.example.com/problems/concurrent-modification");
    private static final URI TYPE_CONSTRAINT = URI.create("https://api.example.com/problems/constraint-violation");
    private static final URI TYPE_BAD_SORT = URI.create("https://api.example.com/problems/invalid-sort-property");
    private static final URI TYPE_INTERNAL = URI.create("https://api.example.com/problems/internal-error");

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException exception) {
        log.debug("{} lookup miss for id {}", exception.getResourceType(), exception.getResourceId());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle(exception.getResourceType() + " not found");
        problem.setType(TYPE_NOT_FOUND);
        problem.setProperty("resourceType", exception.getResourceType());
        problem.setProperty("resourceId", exception.getResourceId());
        return problem;
    }

    @ExceptionHandler(InvalidSortPropertyException.class)
    public ProblemDetail handleInvalidSortProperty(InvalidSortPropertyException exception) {
        log.debug("Rejected sort by unknown property '{}'", exception.getProperty());

        ProblemDetail problem = badRequest("Invalid sort property", exception.getMessage(), TYPE_BAD_SORT);
        problem.setProperty("property", exception.getProperty());
        problem.setProperty("sortableProperties", exception.getSortableProperties());
        return problem;
    }

    /** Two races land here - Hibernate's and TaskService's. Same remedy either way. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(OptimisticLockingFailureException exception) {
        log.warn("Concurrent modification rejected: {}", exception.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The task was modified by another request. Re-read it and retry with the current version.");
        problem.setTitle("Concurrent modification");
        problem.setType(TYPE_CONFLICT);
        return problem;
    }

    /** Reaching here means validation and schema have drifted apart - hence WARN. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Database constraint violation", exception);

        return badRequest("Constraint violation", "The request violates a data constraint.", TYPE_CONSTRAINT);
    }

    /** Last resort - anything with its own status is matched earlier. Adding Spring
     *  Security needs an AccessDeniedException handler here. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled exception serving request", exception);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.");
        problem.setTitle("Internal server error");
        problem.setType(TYPE_INTERNAL);
        return problem;
    }

    /** Field names come from the DTO, never the submitted value. The @Nullable return is
     *  copied from the supertype; none of the three overrides returns null. */
    @Override
    @SuppressWarnings("java:S2638")
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : fieldErrors) {
            errors.merge(fieldError.getField(), defaultMessageOf(fieldError), (first, second) -> first + "; " + second);
        }
        log.debug("Validation failed for {} field(s): {}", errors.size(), errors.keySet());

        ProblemDetail problem = badRequest("Validation failed", "One or more fields are invalid.", TYPE_VALIDATION);
        problem.setProperty("errors", errors);
        return asResponse(problem);
    }

    /** The default detail can leak a fragment of the JSON and the target Java type. */
    @Override
    @SuppressWarnings("java:S2638")
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        log.debug("Unreadable request body: {}", exception.getMessage());

        return asResponse(badRequest("Malformed request body",
                "The request body is missing or is not valid JSON.", TYPE_VALIDATION));
    }

    /** Covers e.g. GET /api/v1/tasks/abc, whose default detail names internals. */
    @Override
    @SuppressWarnings("java:S2638")
    protected @Nullable ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        log.debug("Type mismatch binding request parameter: {}", exception.getMessage());

        return asResponse(badRequest("Invalid parameter",
                "A request parameter has the wrong type.", TYPE_VALIDATION));
    }

    /** One place to change if the 400 envelope ever grows a field. */
    private static ProblemDetail badRequest(String title, String detail, URI type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle(title);
        problem.setType(type);
        return problem;
    }

    private static ResponseEntity<Object> asResponse(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private static String defaultMessageOf(FieldError fieldError) {
        String message = fieldError.getDefaultMessage();
        return message != null ? message : "is invalid";
    }
}
