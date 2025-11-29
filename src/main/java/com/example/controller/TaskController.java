package com.example.controller;

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
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskRepository taskRepository;

    // Constructor Injection is the modern Spring standard
    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // 1. CREATE: POST /api/v1/tasks
    @PostMapping
    public ResponseEntity<Task> createTask(@Valid @RequestBody Task task) {
        // Validation is triggered by @Valid
        Task savedTask = taskRepository.save(task);
        return ResponseEntity.ok(savedTask);
    }

    // 2. READ ALL: GET /api/v1/tasks
    @GetMapping
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    // 3. READ ONE: GET /api/v1/tasks/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable Long id) {
        Optional<Task> task = taskRepository.findById(id);

        // Return 404 if the task is not found
        return task.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 4. UPDATE: PUT /api/v1/tasks/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable Long id, @Valid @RequestBody Task taskDetails) {
        return taskRepository.findById(id)
                .map(existingTask -> {
                    // Update fields
                    existingTask.setTitle(taskDetails.getTitle());
                    existingTask.setCompleted(taskDetails.isCompleted());

                    // Save and return the updated task
                    Task updatedTask = taskRepository.save(existingTask);
                    return ResponseEntity.ok(updatedTask);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 5. DELETE: DELETE /api/v1/tasks/{id}
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
}
