package com.example.shared.error;

import lombok.Getter;

import java.io.Serial;

/**
 * Raised when a lookup by id finds nothing.
 * <p>
 * Carries the resource type as data rather than encoding it in the class
 * name, so a second or tenth resource needs no new exception and no new
 * handler - only a different string. A per-resource hierarchy would grow one
 * near-identical class and one near-identical
 * {@code @ExceptionHandler} per entity, all differing by a title.
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String resourceType;
    private final Object resourceId;

    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super("No " + resourceType + " with id " + resourceId);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
}
