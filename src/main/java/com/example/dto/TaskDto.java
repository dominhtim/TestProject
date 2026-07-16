package com.example.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Plain request/response DTO for the Task REST API.
 *
 * Kept separate from the JPA entity ({@link com.example.model.Task}) per
 * SonarQube rule java:S4684 ("Persistent entities should not be used as
 * arguments of @RequestMapping methods"): exposing the entity directly
 * couples the public API contract to the persistence model, and can leak
 * JPA-internal state or enable mass-assignment-style issues as the entity
 * grows more fields/relationships over time. TaskController maps between
 * this DTO and the entity.
 *
 * Mirrors the same Lombok constructor setup as Task.java, and for the same
 * reason: @JsonCreator(mode = DISABLED) on the all-args constructor stops
 * Jackson 3 from auto-detecting it as the deserialization creator (it would
 * otherwise do so once parameter names are resolvable, which they are here
 * since the project compiles with -parameters), which would break partial
 * JSON bodies like {"title": "..."} by requiring every constructor argument
 * - including the primitive `completed` - to be present.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(onConstructor_ = @__(@JsonCreator(mode = JsonCreator.Mode.DISABLED)))
public class TaskDto {

    private Long id;

    @NotBlank(message = "Title is mandatory")
    private String title;

    private boolean completed;
}
