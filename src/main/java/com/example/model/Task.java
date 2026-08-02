package com.example.model;

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

/**
 * Not {@code @Data} - and no hand-written equals/hashCode either.
 * <p>
 * {@code @Data} derives both from every field including the mutable id, so an
 * entity added to a HashSet while transient changes its hash the moment
 * persist() assigns an id and the set can no longer find it. Object's
 * inherited identity semantics have no such problem: the identity hash never
 * changes, and inside a persistence context Hibernate already guarantees one
 * instance per row, which is what makes reference equality correct here.
 * <p>
 * An id-based implementation would only buy something if two instances of the
 * same row had to compare equal across persistence contexts. Nothing here
 * needs that: Task has no associations and never enters a hash-based
 * collection. Write one when that changes, not before.
 */
@Entity
@Getter
@Setter
@ToString
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

    /**
     * Turns a silent lost update into an OptimisticLockingFailureException,
     * which GlobalExceptionHandler surfaces as HTTP 409. Nullable because a
     * not-yet-persisted instance has no version.
     */
    @Version
    @Column(nullable = false)
    private Long version;
}
