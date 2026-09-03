package com.example.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Identity equality on purpose: no @Data, no hand-written equals/hashCode. See CLAUDE.md. */
@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
// DISABLED keeps Jackson on no-arg constructor + setters, so partial bodies work.
@AllArgsConstructor(onConstructor_ = @__(@JsonCreator(mode = JsonCreator.Mode.DISABLED)))
@Builder
public class Task {

    public static final int TITLE_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title is mandatory")
    @Size(max = TITLE_MAX_LENGTH, message = "Title must be at most " + TITLE_MAX_LENGTH + " characters")
    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(nullable = false)
    private boolean completed;

    /** Lost update -> OptimisticLockingFailureException -> 409. Null until persisted. */
    @Version
    @Column(nullable = false)
    private Long version;
}
