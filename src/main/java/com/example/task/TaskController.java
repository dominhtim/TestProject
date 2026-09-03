package com.example.task;

import com.example.shared.web.SortablePropertyValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Set;

/** HTTP in, HTTP out. Every decision belongs to TaskService; this never touches Task. */
@RestController
@RequiredArgsConstructor
public class TaskController implements TaskApi {

    /** Allowlist: anything else is a 400, not a 500. Package-private so
     *  SortablePropertiesTest can check the names against the entity. */
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
