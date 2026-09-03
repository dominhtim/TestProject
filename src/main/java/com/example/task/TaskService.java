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

/** Transaction boundary and write rules. Returns TaskDto, never Task. */
@Service
@RequiredArgsConstructor
public class TaskService {

    private static final String RESOURCE_TYPE = "Task";

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;

    /** Always inserts: request id/version are ignored, so this cannot merge into a row. */
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

    /** Full replacement; request version is a precondition. saveAndFlush so the response
     *  carries the incremented version and the lock failure lands here. */
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

    /** Omitting the version waives the precondition, not optimistic locking: the column
     *  still guards the flush, so a later writer still gets the same 409. */
    private static void assertVersionIsCurrent(Task task, Long expectedVersion) {
        if (expectedVersion != null && !Objects.equals(expectedVersion, task.getVersion())) {
            throw new OptimisticLockingFailureException(
                    "Task " + task.getId() + " is at version " + task.getVersion()
                            + " but version " + expectedVersion + " was supplied");
        }
    }
}
