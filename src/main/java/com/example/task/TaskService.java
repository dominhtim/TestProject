package com.example.task;

import com.example.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Owns the transaction boundary and the write rules for tasks.
 * <p>
 * Returns {@link TaskDto}, not {@link Task}, and that is the point rather
 * than a convenience: a transaction ends when the method returns, so an
 * entity handed back to a caller is detached. Mapping it out there works
 * today only because Task is all basic columns - the first lazy association
 * would turn it into a LazyInitializationException, with
 * {@code open-in-view=false} leaving no session to fall back on. Mapping
 * inside the boundary makes that structurally impossible.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private static final String RESOURCE_TYPE = "Task";

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;

    /**
     * Always inserts. The request's {@code id} and {@code version} are
     * ignored rather than rejected - a fresh entity is built from the
     * writable fields, so there is no path by which this can merge into an
     * existing row.
     */
    @Transactional
    public TaskDto create(TaskDto request) {
        Task savedTask = taskRepository.saveAndFlush(Task.builder()
                .title(request.getTitle())
                .completed(request.isCompleted())
                .build());

        log.info("Created task id={} completed={}", savedTask.getId(), savedTask.isCompleted());
        return TaskDto.from(savedTask);
    }

    @Transactional(readOnly = true)
    public Page<TaskDto> findAll(Pageable pageable) {
        return taskRepository.findAll(pageable).map(TaskDto::from);
    }

    @Transactional(readOnly = true)
    public TaskDto findById(Long id) {
        return TaskDto.from(requireTask(id));
    }

    /**
     * Full replacement of the mutable fields, inside one transaction so the
     * read and the write cannot be interleaved with another writer's. The
     * request's {@code version}, if present, is a precondition.
     * <p>
     * {@code saveAndFlush}, not {@code save}: Hibernate increments the version
     * column when it flushes, which for a plain {@code save} is at commit -
     * after this method has already mapped the entity. The response would
     * then carry the pre-increment version, telling the caller its write had
     * not happened. Flushing here also moves the optimistic-lock failure
     * inside this method rather than into transaction commit.
     */
    @Transactional
    public TaskDto update(Long id, TaskDto request) {
        Task existingTask = requireTask(id);
        assertVersionIsCurrent(existingTask, request.getVersion());

        existingTask.setTitle(request.getTitle());
        existingTask.setCompleted(request.isCompleted());

        Task updatedTask = taskRepository.saveAndFlush(existingTask);
        log.info("Updated task id={} completed={} version={}",
                updatedTask.getId(), updatedTask.isCompleted(), updatedTask.getVersion());
        return TaskDto.from(updatedTask);
    }

    @Transactional
    public void delete(Long id) {
        taskRepository.delete(requireTask(id));
        log.info("Deleted task id={}", id);
    }

    private Task requireTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_TYPE, id));
    }

    /**
     * A caller that supplies a version is asking for a conditional update.
     * Omitting one waives that precondition but does not opt out of optimistic
     * locking: the {@code @Version} column still guards the flush, so a writer
     * that arrives after this check is caught by Hibernate and surfaces as the
     * same 409.
     */
    private static void assertVersionIsCurrent(Task task, Long expectedVersion) {
        if (expectedVersion != null && !Objects.equals(expectedVersion, task.getVersion())) {
            throw new OptimisticLockingFailureException(
                    "Task " + task.getId() + " is at version " + task.getVersion()
                            + " but version " + expectedVersion + " was supplied");
        }
    }
}
