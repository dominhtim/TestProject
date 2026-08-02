package com.example.controller;

import com.example.dto.TaskDto;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Mappings, request binding and OpenAPI documentation, kept off
 * {@link TaskController} so it holds only the implementation. Spring resolves
 * these annotations from an interface as if they were on the implementing
 * class's own methods.
 * <p>
 * Every method returns {@code ResponseEntity}, including the three whose
 * status never varies from 200. Two of them need it - createTask sets a
 * Location header, deleteTask answers 204 - and one uniform signature is
 * easier to hold in your head than a rule with exceptions in it.
 * <p>
 * {@code @ResponseStatus} would be the tidier way to express deleteTask's 204
 * and is not an option: Spring reads it from the implementation method or the
 * controller class, never from an interface method, so declaring it here
 * would be silently ignored and DELETE would answer 200.
 * <p>
 * Failure statuses appear only in the {@code @ApiResponse} documentation -
 * 400, 404 and 409 are all produced by
 * {@link com.example.exception.GlobalExceptionHandler}, never returned from
 * these methods.
 */
@RequestMapping("/api/v1/tasks")
public interface TaskApi {

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Task created; URI in the Location header")
    @ValidationFailedApiResponse
    ResponseEntity<TaskDto> createTask(@Valid @RequestBody TaskDto request);

    @GetMapping
    @ApiResponse(responseCode = "200", description = "Page of tasks retrieved")
    @InvalidSortApiResponse
    ResponseEntity<PagedModel<TaskDto>> getAllTasks(@ParameterObject Pageable pageable);

    @GetMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "Task found")
    @NotFoundApiResponse
    ResponseEntity<TaskDto> getTaskById(@PathVariable Long id);

    @PutMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "Task updated")
    @ValidationFailedApiResponse
    @NotFoundApiResponse
    @ConflictApiResponse
    ResponseEntity<TaskDto> updateTask(@PathVariable Long id, @Valid @RequestBody TaskDto request);

    /**
     * Can conflict: the versioned DELETE fails to match if a concurrent write
     * bumps the version between our read and our flush.
     */
    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204", description = "Task deleted")
    @NotFoundApiResponse
    @ConflictApiResponse
    ResponseEntity<Void> deleteTask(@PathVariable Long id);
}
