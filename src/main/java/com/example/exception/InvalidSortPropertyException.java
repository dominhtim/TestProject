package com.example.exception;

import lombok.Getter;

import java.io.Serial;
import java.util.Set;
import java.util.TreeSet;

/**
 * Raised when a client asks to sort by a property that is not sortable.
 * <p>
 * Without this check the unknown property reaches Spring Data's criteria
 * builder and blows up as an unhandled runtime exception, which the generic
 * handler reports as a 500 - a server fault for what is plainly a bad
 * request. The allowlist also stops a caller ordering by a column the API
 * does not expose, which would let them infer its values from the sequence.
 */
@Getter
public class InvalidSortPropertyException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String property;
    private final Set<String> sortableProperties;

    public InvalidSortPropertyException(String property, Set<String> sortableProperties) {
        super("Cannot sort by '" + property + "'");
        this.property = property;
        this.sortableProperties = new TreeSet<>(sortableProperties);
    }
}
