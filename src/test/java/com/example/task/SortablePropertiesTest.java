package com.example.task;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code SORTABLE_PROPERTIES} is a set of string literals that must match
 * {@link Task}'s field names, and nothing in the compiler ties the two
 * together. Rename a field and the endpoint starts rejecting a sort that
 * used to work, silently and only at runtime.
 * <p>
 * This turns that into a build failure. It is the cheap alternative to the
 * JPA static metamodel, which would give real compile-time safety at the
 * cost of an annotation processor.
 */
class SortablePropertiesTest {

    @Test
    void everyAllowlistedSortPropertyIsARealFieldOnTheEntity() {
        Set<String> entityFields = Arrays.stream(Task.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(entityFields)
                .as("a sort key with no matching entity field can only fail at query-build time")
                .containsAll(TaskController.SORTABLE_PROPERTIES);
    }

    @Test
    void versionIsExcludedDeliberatelyRatherThanByOversight() {
        assertThat(TaskController.SORTABLE_PROPERTIES)
                .as("version is a concurrency token, not an ordering users should depend on")
                .doesNotContain("version");
    }
}
