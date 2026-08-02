package com.example.task;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Validation constraints and entity mapping, driven through {@link Validator}
 * directly. {@link TaskControllerUnitTest} reaches the same constraints, but
 * only one violation at a time; going direct makes the boundary cases cheap
 * enough to cover exhaustively.
 */
class TaskDtoTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void openValidatorFactory() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    private Set<ConstraintViolation<TaskDto>> validate(TaskDto dto) {
        return validator.validate(dto);
    }

    @ParameterizedTest(name = "title=[{0}] is rejected as blank")
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    void rejectsBlankTitles(String title) {
        TaskDto dto = TaskDto.builder().title(title).completed(false).build();

        assertThat(validate(dto))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.getPropertyPath()).hasToString("title");
                    assertThat(violation.getMessage()).isEqualTo("Title is mandatory");
                });
    }

    @Test
    void acceptsATitleExactlyAtTheLengthLimit() {
        TaskDto dto = TaskDto.builder().title("x".repeat(Task.TITLE_MAX_LENGTH)).build();

        assertThat(validate(dto)).isEmpty();
    }

    @Test
    void rejectsATitleOneCharacterOverTheLimit() {
        TaskDto dto = TaskDto.builder().title("x".repeat(Task.TITLE_MAX_LENGTH + 1)).build();

        assertThat(validate(dto))
                .singleElement()
                .satisfies(violation -> assertThat(violation.getMessage()).contains("at most 255"));
    }

    @Test
    void acceptsAValidTitleRegardlessOfTheOtherFields() {
        TaskDto withoutVersion = TaskDto.builder().title("Valid").completed(true).build();
        TaskDto withVersion = TaskDto.builder().id(1L).title("Valid").completed(false).version(4L).build();

        assertThat(validate(withoutVersion)).isEmpty();
        assertThat(validate(withVersion)).isEmpty();
    }

    @Test
    void fromCopiesEveryFieldOfTheEntity() {
        Task entity = Task.builder()
                .id(11L)
                .title("Mapped across")
                .completed(true)
                .version(6L)
                .build();

        TaskDto dto = TaskDto.from(entity);

        assertThat(dto.getId()).isEqualTo(11L);
        assertThat(dto.getTitle()).isEqualTo("Mapped across");
        assertThat(dto.isCompleted()).isTrue();
        assertThat(dto.getVersion()).isEqualTo(6L);
    }

    @Test
    void fromCarriesNullIdAndVersionForAnUnsavedEntity() {
        Task unsaved = Task.builder().title("Not yet persisted").completed(false).build();

        TaskDto dto = TaskDto.from(unsaved);

        assertThat(dto.getId()).isNull();
        assertThat(dto.getVersion()).isNull();
        assertThat(dto.getTitle()).isEqualTo("Not yet persisted");
    }

    @Test
    void fromRejectsNullRatherThanReturningAnEmptyDto() {
        assertThatNullPointerException()
                .isThrownBy(() -> TaskDto.from(null))
                .withMessage("task must not be null");
    }

    @Test
    void dtoUsesValueEquality() {
        // Unlike the entity, whose identity is its id, two DTOs describing
        // the same payload are interchangeable.
        TaskDto first = TaskDto.builder().id(1L).title("Same").completed(true).version(0L).build();
        TaskDto second = TaskDto.builder().id(1L).title("Same").completed(true).version(0L).build();

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }
}
