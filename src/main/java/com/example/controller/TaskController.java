package com.example.controller;

import com.example.dto.TaskDto;
import com.example.model.Task;
import com.example.repository.TaskRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST Controller for Task CRUD operations.
 * Base mapping is set to /api/v1/tasks.
 *
 * Speaks {@link TaskDto} over the wire, never the {@link Task} JPA entity
 * directly (see TaskDto's javadoc for why), mapping to/from the entity
 * internally.
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskRepository taskRepository;

    // Constructor Injection is the modern Spring standard
    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @PostMapping
    public ResponseEntity<TaskDto> createTask(@Valid @RequestBody TaskDto request) {
        // Validation is triggered by @Valid. Ignore any client-supplied id -
        // creation always produces a new row with a generated id.
        Task newTask = new Task();
        newTask.setTitle(request.getTitle());
        newTask.setCompleted(request.isCompleted());

        Task savedTask = taskRepository.save(newTask);
        return ResponseEntity.ok(toDto(savedTask));
    }

    @GetMapping
    public List<TaskDto> getAllTasks() {
        return taskRepository.findAll().stream()
                .map(TaskController::toDto)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDto> getTaskById(@PathVariable Long id) {
        Optional<Task> task = taskRepository.findById(id);

        // Return 404 if the task is not found
        return task.map(t -> ResponseEntity.ok(toDto(t)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskDto> updateTask(@PathVariable Long id, @Valid @RequestBody TaskDto request) {
        return taskRepository.findById(id)
                .map(existingTask -> {
                    // Update fields
                    existingTask.setTitle(request.getTitle());
                    existingTask.setCompleted(request.isCompleted());

                    // Save and return the updated task
                    Task updatedTask = taskRepository.save(existingTask);
                    return ResponseEntity.ok(toDto(updatedTask));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        return taskRepository.findById(id)
                .map(task -> {
                    taskRepository.delete(task);
                    // Return 204 No Content for a successful deletion
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static TaskDto toDto(Task task) {
        return new TaskDto(task.getId(), task.getTitle(), task.isCompleted());
    }
}
