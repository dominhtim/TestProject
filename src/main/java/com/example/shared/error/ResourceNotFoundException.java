package com.example.shared.error;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/** Lookup by id found nothing. Resource type is data, not a subclass, so a tenth
 *  resource needs no new exception and no new handler. */
@Getter
public class ResourceNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String resourceType;

    /** Serializable, not Object, to satisfy java:S1948 honestly - transient would make
     *  this null after any round trip. */
    private final Serializable resourceId;

    public ResourceNotFoundException(String resourceType, Serializable resourceId) {
        super("No " + resourceType + " with id " + resourceId);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
}
