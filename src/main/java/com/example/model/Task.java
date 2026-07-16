package com.example.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * JPA Entity representing a simple To-Do Task.
 * Uses Lombok for automated getter/setter/constructor generation.
 */
@Entity
@Data // Generates getters, setters, toString, equals, and hashCode
@NoArgsConstructor // Generates a constructor with no arguments
// Generates a constructor with all fields, for tests to build fixtures with.
// @JsonCreator(mode = DISABLED) stops Jackson from treating this as the
// deserialization "creator": Jackson 3 (unlike Jackson 2) will auto-detect
// an all-args constructor as the creator once parameter names are
// resolvable (this project compiles with -parameters, for springdoc's
// benefit), even when a no-arg constructor also exists. Without this,
// partial JSON bodies like {"title": "..."} fail because the primitive
// `completed` parameter has no value to bind. Disabling it here forces
// Jackson back to the no-arg constructor + setters, so missing fields just
// keep their Java-side defaults as originally intended.
@AllArgsConstructor(onConstructor_ = @__(@JsonCreator(mode = JsonCreator.Mode.DISABLED)))
public class Task {

    // Primary Key
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Task description, cannot be blank
    @NotBlank(message = "Title is mandatory")
    private String title;

    // Status of the task
    private boolean completed = false;
}
