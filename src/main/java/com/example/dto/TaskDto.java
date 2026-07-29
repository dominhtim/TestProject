package com.example.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request/response DTO for the Task REST API - kept separate from the JPA
 * entity ({@link com.example.model.Task}) per SonarQube rule java:S4684
 * (persistent entities shouldn't be used as @RequestMapping arguments or
 * return types). TaskController maps between this and the entity.
 *
 * Same Lombok constructor setup as Task.java, for the same reason:
 * @JsonCreator(mode = DISABLED) stops Jackson 3 from auto-detecting the
 * all-args constructor as its deserialization creator, which would break
 * partial JSON bodies like {"title": "..."}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(onConstructor_ = @__(@JsonCreator(mode = JsonCreator.Mode.DISABLED)))
@Builder
public class TaskDto {

    private Long id;

    @NotBlank(message = "Title is mandatory")
    private String title;

    private boolean completed;
}
