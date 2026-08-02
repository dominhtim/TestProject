package com.example.exception;

import com.example.dto.TaskDto;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Handlers driven directly, for the paths HTTP tests cannot reach.
 * <p>
 * Two of these are branches rather than endpoints: a {@code FieldError} with
 * no default message, and two violations landing on the same field. Neither
 * can be produced by sending a request, because the constraints on TaskDto
 * always supply a message and never both fire on one field. The third,
 * {@link DataIntegrityViolationException}, is only reachable through the web
 * layer if validation and schema have already drifted apart - which is
 * exactly when you want the handler to have been tested.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final WebRequest request = new ServletWebRequest(new MockHttpServletRequest());

    /** Only exists to give {@link MethodParameter} something real to point at. */
    @SuppressWarnings("unused")
    private void bindingTarget(TaskDto body) {
        // no-op
    }

    private MethodArgumentNotValidException validationFailureOn(FieldError... fieldErrors) throws Exception {
        BindingResult bindingResult = new BeanPropertyBindingResult(new TaskDto(), "taskDto");
        for (FieldError fieldError : fieldErrors) {
            bindingResult.addError(fieldError);
        }
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("bindingTarget", TaskDto.class);
        return new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> errorsOf(ResponseEntity<Object> response) {
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem).isNotNull();
        return (Map<String, String>) problem.getProperties().get("errors");
    }

    @Test
    void dataIntegrityViolationBecomesABadRequestWithTheCauseKeptOutOfTheBody() {
        ProblemDetail problem = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("could not execute statement; constraint [TITLE_NOT_NULL]"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Constraint violation");
        assertThat(problem.getDetail())
                .as("the constraint name and SQL fragment belong in the log, not the response")
                .isEqualTo("The request violates a data constraint.");
    }

    @Test
    void aFieldErrorWithNoDefaultMessageStillProducesAnEntry() throws Exception {
        FieldError withoutMessage =
                new FieldError("taskDto", "title", null, false, null, null, null);

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                validationFailureOn(withoutMessage), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);

        assertThat(errorsOf(response))
                .as("a field must never vanish from the response just because its message was null")
                .containsEntry("title", "is invalid");
    }

    @Test
    void twoViolationsOnOneFieldAreJoinedRatherThanOneReplacingTheOther() throws Exception {
        FieldError first = new FieldError("taskDto", "title", "Title is mandatory");
        FieldError second = new FieldError("taskDto", "title", "Title must be at most 255 characters");

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                validationFailureOn(first, second), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);

        assertThat(errorsOf(response))
                .containsEntry("title", "Title is mandatory; Title must be at most 255 characters");
    }

    @Test
    void separateFieldsAreReportedSeparately() throws Exception {
        FieldError onTitle = new FieldError("taskDto", "title", "Title is mandatory");
        FieldError onVersion = new FieldError("taskDto", "version", "must not be negative");

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                validationFailureOn(onTitle, onVersion), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);

        assertThat(errorsOf(response))
                .containsEntry("title", "Title is mandatory")
                .containsEntry("version", "must not be negative");
    }
}
