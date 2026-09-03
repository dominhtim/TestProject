package com.example.task;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression guard: every assertion holds trivially today and breaks if field-based
 *  equality returns to Task. TaskPersistenceIT checks the same against Hibernate. */
class TaskEqualityTest {

    @Test
    void hashCodeIsStableAcrossIdAssignment() {
        Task task = Task.builder().title("Write the migration plan").build();

        int hashWhileTransient = task.hashCode();
        task.setId(42L);

        assertThat(task.hashCode())
                .as("hashCode must not depend on mutable state, or hash-based collections break on persist")
                .isEqualTo(hashWhileTransient);
    }

    @Test
    void hashCodeIsStableAcrossFieldMutation() {
        Task task = Task.builder().id(1L).title("Original").completed(false).build();

        int before = task.hashCode();
        task.setTitle("Renamed");
        task.setCompleted(true);

        assertThat(task.hashCode()).isEqualTo(before);
    }

    @Test
    void transientEntityRemainsFindableInAHashSetAfterIdAssignment() {
        // The exact @Data failure: searched in the old hash's bucket, entity has moved.
        Task task = Task.builder().title("Ship it").build();
        Set<Task> tasks = new HashSet<>();
        tasks.add(task);

        task.setId(7L);

        assertThat(tasks).contains(task);
    }

    @Test
    void twoDistinctInstancesAreNeverEqualHoweverAlikeTheirFields() {
        Task first = Task.builder().title("Identical").completed(true).build();
        Task second = Task.builder().title("Identical").completed(true).build();

        assertThat(first)
                .as("an instance is always equal to itself")
                .isEqualTo(first)
                .as("field-based equality would call these the same task")
                .isNotEqualTo(second);
    }

    @Test
    void toStringIncludesTheIdentifyingFields() {
        Task task = Task.builder().id(3L).title("Readable in logs").completed(true).version(2L).build();

        assertThat(task.toString())
                .contains("id=3")
                .contains("Readable in logs")
                .contains("completed=true");
    }
}
