package com.example.task;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Ties SORTABLE_PROPERTIES' string literals to Task's field names, so a rename is a
 *  build failure instead of a silent runtime one. */
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
