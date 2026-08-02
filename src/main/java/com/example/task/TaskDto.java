package com.example.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Kept separate from the {@link Task} entity per SonarQube rule java:S4684
 * (persistent entities shouldn't be @RequestMapping arguments or return
 * types). {@code @Data} is right here and wrong on Task: this is a detached
 * value object with no persistence identity, so value equality is correct.
 */
@Data
@NoArgsConstructor
// Same @JsonCreator(mode = DISABLED) reasoning as Task.java.
@AllArgsConstructor(onConstructor_ = @__(@JsonCreator(mode = JsonCreator.Mode.DISABLED)))
@Builder
public class TaskDto {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @NotBlank(message = "Title is mandatory")
    @Size(max = Task.TITLE_MAX_LENGTH, message = "Title must be at most 255 characters")
    private String title;

    private boolean completed;

    /**
     * Always present on responses. Optional on a PUT, where it is a
     * precondition: supply the version you last read and a stale write is
     * rejected with 409.
     * <p>
     * Omitting it waives that precondition - it does not disable optimistic
     * locking. Hibernate still checks the version column at flush, so two
     * genuinely concurrent writers can both omit it and the loser still gets
     * a 409.
     */
    private Long version;

    public static TaskDto from(Task task) {
        Objects.requireNonNull(task, "task must not be null");
        return TaskDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .completed(task.isCompleted())
                .version(task.getVersion())
                .build();
    }
}
