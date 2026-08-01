package com.example.controller;

import com.example.dto.TaskDto;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Task API contract: mappings, request binding, and OpenAPI response
 * documentation live here so {@link TaskController} can stay focused on the
 * implementation. Spring resolves {@code @RequestMapping} and parameter
 * annotations declared on an interface just as if they were on the
 * implementing class's own methods.
 */
@RequestMapping("/api/v1/tasks")
public interface TaskApi {

    @PostMapping
    @ApiResponse(responseCode = "200", description = "Task created")
    @ApiResponse(responseCode = "400", description = "Validation failed (e.g. blank title)")
    ResponseEntity<TaskDto> createTask(@Valid @RequestBody TaskDto request);

    @GetMapping
    List<TaskDto> getAllTasks();

    @GetMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "Task found")
    @ApiResponse(responseCode = "404", description = "No task with the given id")
    ResponseEntity<TaskDto> getTaskById(@PathVariable Long id);

    @PutMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "Task updated")
    @ApiResponse(responseCode = "400", description = "Validation failed (e.g. blank title)")
    @ApiResponse(responseCode = "404", description = "No task with the given id")
    ResponseEntity<TaskDto> updateTask(@PathVariable Long id, @Valid @RequestBody TaskDto request);

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204", description = "Task deleted")
    @ApiResponse(responseCode = "404", description = "No task with the given id")
    ResponseEntity<Void> deleteTask(@PathVariable Long id);
}
