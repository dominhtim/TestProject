package com.example.controller;

import com.example.dto.TaskDto;
import com.example.model.Task;
import com.example.service.TaskService;
import com.example.web.SortablePropertyValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Set;

/**
 * Implements {@link TaskApi}: HTTP in, HTTP out. Every decision belongs to
 * {@link TaskService}, which owns the transaction boundary, the write rules
 * and the entity mapping - this class never touches {@link Task}.
 */
@RestController
@RequiredArgsConstructor
public class TaskController implements TaskApi {

    /**
     * Sort keys this API exposes. Anything else is a 400, not a 500 - and
     * sorting by a column the DTO does not expose would leak its ordering.
     * {@code version} is omitted deliberately: it is a concurrency token, not
     * a meaningful ordering.
     * <p>
     * Package-private so {@code SortablePropertiesTest} can check every name
     * against the entity; these are string literals the compiler cannot
     * otherwise tie to {@link Task}'s fields.
     */
    static final Set<String> SORTABLE_PROPERTIES = Set.of("id", "title", "completed");

    private final TaskService taskService;

    @Override
    public ResponseEntity<TaskDto> createTask(TaskDto request) {
        TaskDto created = taskService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Override
    public ResponseEntity<PagedModel<TaskDto>> getAllTasks(Pageable pageable) {
        SortablePropertyValidator.assertSortableOnly(pageable.getSort(), SORTABLE_PROPERTIES);
        return ResponseEntity.ok(new PagedModel<>(taskService.findAll(pageable)));
    }

    @Override
    public ResponseEntity<TaskDto> getTaskById(Long id) {
        return ResponseEntity.ok(taskService.findById(id));
    }

    @Override
    public ResponseEntity<TaskDto> updateTask(Long id, TaskDto request) {
        return ResponseEntity.ok(taskService.update(id, request));
    }

    @Override
    public ResponseEntity<Void> deleteTask(Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
