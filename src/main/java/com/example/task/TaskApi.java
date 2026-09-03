package com.example.task;

import com.example.shared.web.ConflictApiResponse;
import com.example.shared.web.InvalidSortApiResponse;
import com.example.shared.web.NotFoundApiResponse;
import com.example.shared.web.ValidationFailedApiResponse;
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

/** Mappings, binding and OpenAPI docs, kept off TaskController. Failure statuses are
 *  documented here but produced by GlobalExceptionHandler. See CLAUDE.md. */
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

    /** Can conflict: a concurrent write bumping the version between read and flush. */
    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204", description = "Task deleted")
    @NotFoundApiResponse
    @ConflictApiResponse
    ResponseEntity<Void> deleteTask(@PathVariable Long id);
}
