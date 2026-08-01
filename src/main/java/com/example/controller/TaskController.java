package com.example.controller;

import com.example.dto.TaskDto;
import com.example.model.Task;
import com.example.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Implements {@link TaskApi} - see there for the mappings, request binding,
 * and OpenAPI response documentation. Speaks {@link TaskDto} over the wire,
 * never the {@link Task} JPA entity directly (see TaskDto's Javadoc for why),
 * mapping to/from the entity internally.
 */
@RestController
@RequiredArgsConstructor
public class TaskController implements TaskApi {

    private final TaskRepository taskRepository;

    @Override
    public ResponseEntity<TaskDto> createTask(TaskDto request) {
        Task newTask = Task.builder()
                .title(request.getTitle())
                .completed(request.isCompleted())
                .build();

        Task savedTask = taskRepository.save(newTask);
        return ResponseEntity.ok(TaskDto.from(savedTask));
    }

    @Override
    public List<TaskDto> getAllTasks() {
        return taskRepository.findAll().stream()
                .map(TaskDto::from)
                .toList();
    }

    @Override
    public ResponseEntity<TaskDto> getTaskById(Long id) {
        Optional<Task> found = taskRepository.findById(id);
        return found.map(task -> ResponseEntity.ok(TaskDto.from(task)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<TaskDto> updateTask(Long id, TaskDto request) {
        return taskRepository.findById(id)
                .map(existingTask -> {
                    existingTask.setTitle(request.getTitle());
                    existingTask.setCompleted(request.isCompleted());
                    Task updatedTask = taskRepository.save(existingTask);
                    return ResponseEntity.ok(TaskDto.from(updatedTask));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<Void> deleteTask(Long id) {
        return taskRepository.findById(id)
                .map(task -> {
                    taskRepository.delete(task);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
