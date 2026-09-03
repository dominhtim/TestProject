package com.example.shared.web;

import com.example.shared.error.InvalidSortPropertyException;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Screens a caller-supplied {@code ?sort=} against an endpoint's allowlist. */
public final class SortablePropertyValidator {

    private SortablePropertyValidator() {
    }

    /** @throws InvalidSortPropertyException on the first disallowed property, which the
     *          handler renders as 400 instead of a 500 from the criteria builder */
    public static void assertSortableOnly(Sort sort, Set<String> sortableProperties) {
        for (Sort.Order order : sort) {
            if (!sortableProperties.contains(order.getProperty())) {
                throw new InvalidSortPropertyException(order.getProperty(), sortableProperties);
            }
        }
    }
}
