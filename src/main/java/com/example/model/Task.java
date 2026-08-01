package com.example.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA Entity representing a simple To-Do Task.
 */
@Entity
@Data
@NoArgsConstructor
// @JsonCreator(mode = DISABLED): without this, Jackson 3 auto-detects this
// all-args constructor as the deserialization creator (since the project
// compiles with -parameters) and then requires every argument - including
// the primitive `completed` - to be present, breaking partial JSON bodies
// like {"title": "..."}. Disabling it keeps Jackson on the no-arg
// constructor + setters instead.
@AllArgsConstructor(onConstructor_ = @__(@JsonCreator(mode = JsonCreator.Mode.DISABLED)))
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title is mandatory")
    private String title;

    private boolean completed = false;
}
