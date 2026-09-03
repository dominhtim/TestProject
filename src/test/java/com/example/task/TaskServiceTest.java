package com.example.task;

import com.example.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The write rules with the repository mocked. A controller test can only assert a 409
 *  renders, never that the rule deciding it is right. */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    private final Task persisted = Task.builder()
            .id(1L).title("As stored").completed(false).version(3L).build();

    private static TaskDto request(String title, boolean completed, Long version) {
        return TaskDto.builder().title(title).completed(completed).version(version).build();
    }

    @Test
    void createBuildsAFreshEntityAndReturnsItMapped() {
        when(taskRepository.saveAndFlush(any(Task.class)))
                .thenReturn(Task.builder().id(9L).title("New").completed(true).version(0L).build());

        TaskDto created = taskService.create(request("New", true, null));

        assertThat(created.getId()).isEqualTo(9L);
        assertThat(created.getTitle()).isEqualTo("New");
        assertThat(created.isCompleted()).isTrue();
        assertThat(created.getVersion()).isZero();
    }

    @Test
    void createIgnoresAnyIdOrVersionOnTheRequestSoItCannotMergeIntoAnExistingRow() {
        when(taskRepository.saveAndFlush(any(Task.class)))
                .thenReturn(Task.builder().id(9L).title("New").completed(false).version(0L).build());

        TaskDto hostile = TaskDto.builder().id(777L).title("New").completed(false).version(42L).build();
        taskService.create(hostile);

        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getId())
                .as("a null id is what makes save() an insert rather than a merge")
                .isNull();
        assertThat(saved.getValue().getVersion()).isNull();
    }

    @Test
    void findAllMapsEveryElementOfThePage() {
        Page<Task> page = new PageImpl<>(List.of(persisted), PageRequest.of(0, 20), 1);
        when(taskRepository.findAll(PageRequest.of(0, 20))).thenReturn(page);

        Page<TaskDto> mapped = taskService.findAll(PageRequest.of(0, 20));

        assertThat(mapped.getTotalElements()).isEqualTo(1);
        assertThat(mapped.getContent()).singleElement().satisfies(dto -> {
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getTitle()).isEqualTo("As stored");
            assertThat(dto.getVersion()).isEqualTo(3L);
        });
    }

    @Test
    void findByIdReturnsTheEntityMapped() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(persisted));

        TaskDto found = taskService.findById(1L);

        assertThat(found.getId()).isEqualTo(1L);
        assertThat(found.getTitle()).isEqualTo("As stored");
    }

    @Test
    void findByIdReportsTheResourceTypeAndIdWhenAbsent() {
        when(taskRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> taskService.findById(404L))
                .satisfies(exception -> {
                    assertThat(exception.getResourceType()).isEqualTo("Task");
                    assertThat(exception.getResourceId()).isEqualTo(404L);
                })
                .withMessage("No Task with id 404");
    }

    @Test
    void updateReplacesTheMutableFieldsAndLeavesIdentityAlone() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(persisted));
        when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(call -> call.getArgument(0));

        taskService.update(1L, request("Renamed", true, null));

        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("Renamed");
        assertThat(saved.getValue().isCompleted()).isTrue();
        assertThat(saved.getValue().getId())
                .as("a PUT replaces fields, it does not re-key the row")
                .isEqualTo(1L);
    }

    @Test
    void updateProceedsWhenTheSuppliedVersionMatches() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(persisted));
        when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(call -> call.getArgument(0));

        // Built outside the lambda so only the call under test can throw.
        TaskDto matchingVersion = request("Renamed", true, 3L);

        assertThatNoException().isThrownBy(() -> taskService.update(1L, matchingVersion));
    }

    @Test
    void updateRejectsAStaleSuppliedVersionWithoutWriting() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(persisted));

        TaskDto staleVersion = request("Renamed", true, 1L);

        assertThatExceptionOfType(OptimisticLockingFailureException.class)
                .isThrownBy(() -> taskService.update(1L, staleVersion));

        verify(taskRepository, never()).saveAndFlush(any(Task.class));
    }

    @Test
    void updateWaivesThePreconditionWhenNoVersionIsSupplied() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(persisted));
        when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(call -> call.getArgument(0));

        TaskDto withoutVersion = request("Renamed", true, null);

        // Waived here, still enforced by the @Version column at flush.
        assertThatNoException().isThrownBy(() -> taskService.update(1L, withoutVersion));
        verify(taskRepository, times(1)).saveAndFlush(any(Task.class));
    }

    @Test
    void updateReportsNotFoundBeforeCheckingTheVersion() {
        when(taskRepository.findById(404L)).thenReturn(Optional.empty());

        TaskDto changes = request("Renamed", true, 99L);

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> taskService.update(404L, changes));

        verify(taskRepository, never()).saveAndFlush(any(Task.class));
    }

    @Test
    void deleteRemovesTheEntityItLoaded() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(persisted));

        taskService.delete(1L);

        verify(taskRepository, times(1)).delete(persisted);
    }

    @Test
    void deleteReportsNotFoundWithoutTouchingTheRepositoryFurther() {
        when(taskRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> taskService.delete(404L));

        verify(taskRepository, never()).delete(any(Task.class));
    }
}
