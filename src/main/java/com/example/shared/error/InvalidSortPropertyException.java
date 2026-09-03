package com.example.shared.error;

import lombok.Getter;

import java.io.Serial;
import java.util.Set;
import java.util.TreeSet;

/** Client asked to sort by a non-sortable property. Unscreened this is a 500 from the
 *  criteria builder, and orders by columns the API does not expose. See CLAUDE.md. */
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
