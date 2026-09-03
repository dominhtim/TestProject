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

/** Detached value object, so @Data is right here and wrong on Task (java:S4684). */
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

    /** Always on responses; optional on PUT as a precondition. Omitting it waives the
     *  precondition, not optimistic locking. */
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
