package com.example.task;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behaviour only a real Hibernate session shows: the {@code @Version} column
 * advancing, the schema enforcing what Bean Validation claims, and entity
 * identity surviving the transient-to-managed transition.
 * <p>
 * {@code @SpringBootTest} rather than {@code @DataJpaTest} because this
 * project doesn't declare Boot 4's {@code spring-boot-starter-data-jpa-test}.
 * Deliberately not {@code @Transactional}: one test-managed transaction would
 * share a persistence context across every operation, which is exactly what
 * hides version increments and flush-time failures.
 */
@SpringBootTest
class TaskPersistenceIT {

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void clearDatabase() {
        taskRepository.deleteAll();
    }

    @Test
    void versionStartsAtZeroAndAdvancesOnEveryWrite() {
        Task saved = taskRepository.saveAndFlush(
                Task.builder().title("Version me").completed(false).build());

        assertThat(saved.getVersion()).isZero();

        saved.setCompleted(true);
        Task updated = taskRepository.saveAndFlush(saved);

        assertThat(updated.getVersion()).isEqualTo(1L);

        updated.setTitle("Version me again");
        assertThat(taskRepository.saveAndFlush(updated).getVersion()).isEqualTo(2L);
    }

    @Test
    void aStaleWriteIsRejectedRatherThanSilentlyOverwriting() {
        Task persisted = taskRepository.saveAndFlush(
                Task.builder().title("Contended").completed(false).build());
        Long id = persisted.getId();

        // Simulate the classic lost update: two callers read the same row,
        // both edit their own copy, both write back.
        Task firstWriterCopy = taskRepository.findById(id).orElseThrow();
        Task secondWriterCopy = Task.builder()
                .id(id)
                .title("Second writer's edit")
                .completed(true)
                .version(firstWriterCopy.getVersion())
                .build();

        firstWriterCopy.setTitle("First writer's edit");
        taskRepository.saveAndFlush(firstWriterCopy);

        assertThatThrownBy(() -> taskRepository.saveAndFlush(secondWriterCopy))
                .as("the second write is based on a version that no longer exists")
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(taskRepository.findById(id).orElseThrow().getTitle())
                .isEqualTo("First writer's edit");
    }

    @Test
    void aBlankTitleIsRefusedAtThePersistenceLayerToo() {
        // The entity and column carry the same constraint as the DTO, so a
        // future path bypassing the API can't write a row the API would
        // reject. Which exception surfaces depends on whether Hibernate's
        // validation listener fires before the INSERT - both are correct.
        Task noTitle = Task.builder().completed(false).build();

        assertThatThrownBy(() -> taskRepository.saveAndFlush(noTitle))
                .isInstanceOfAny(ConstraintViolationException.class, DataIntegrityViolationException.class);
    }

    @Test
    void aPersistedEntityIsStillFindableInACollectionItJoinedBeforeItHadAnId() {
        Task task = Task.builder().title("Added to a set while transient").build();
        Set<Task> tasks = new HashSet<>();
        tasks.add(task);

        Task persisted = taskRepository.saveAndFlush(task);

        assertThat(persisted.getId()).isNotNull();
        assertThat(tasks)
                .as("with Lombok's @Data this lookup missed, because persisting changed the hash")
                .contains(task);
    }

    @Test
    void findAllHonoursThePageBoundaryAndSort() {
        List<Task> batch = List.of(
                Task.builder().title("Alpha").build(),
                Task.builder().title("Bravo").build(),
                Task.builder().title("Charlie").build(),
                Task.builder().title("Delta").build(),
                Task.builder().title("Echo").build());
        taskRepository.saveAllAndFlush(batch);

        var firstPage = taskRepository.findAll(PageRequest.of(0, 2, Sort.by("title")));

        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.getContent()).extracting(Task::getTitle).containsExactly("Alpha", "Bravo");

        var lastPage = taskRepository.findAll(PageRequest.of(2, 2, Sort.by("title")));

        assertThat(lastPage.getContent()).extracting(Task::getTitle).containsExactly("Echo");
        assertThat(lastPage.isLast()).isTrue();
    }

    @Test
    void deleteRemovesOnlyTheTargetedRow() {
        Task keep = taskRepository.saveAndFlush(Task.builder().title("Keep").build());
        Task remove = taskRepository.saveAndFlush(Task.builder().title("Remove").build());

        taskRepository.delete(remove);
        taskRepository.flush();

        assertThat(taskRepository.findById(remove.getId())).isEmpty();
        assertThat(taskRepository.findById(keep.getId())).isPresent();
    }
}
