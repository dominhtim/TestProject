package com.example.controller;

import com.example.dto.TaskDto;
import com.example.model.Task;
import com.example.repository.TaskRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST Controller for Task CRUD operations, mapped to /api/v1/tasks.
 *
 * Speaks {@link TaskDto} over the wire, never the {@link Task} JPA entity
 * directly (see TaskDto's javadoc for why), mapping to/from the entity
 * internally.
 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskRepository taskRepository;

    @PostMapping
    public ResponseEntity<TaskDto> createTask(@Valid @RequestBody TaskDto request) {
        // Ignore any client-supplied id - creation always generates a new one.
        Task newTask = Task.builder()
                .title(request.getTitle())
                .completed(request.isCompleted())
                .build();

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
        Optional<Task> found = taskRepository.findById(id);
        return found.map(task -> ResponseEntity.ok(toDto(task)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskDto> updateTask(@PathVariable Long id, @Valid @RequestBody TaskDto request) {
        return taskRepository.findById(id)
                .map(existingTask -> {
                    existingTask.setTitle(request.getTitle());
                    existingTask.setCompleted(request.isCompleted());
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
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static TaskDto toDto(Task task) {
        return TaskDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .completed(task.isCompleted())
                .build();
    }
}
