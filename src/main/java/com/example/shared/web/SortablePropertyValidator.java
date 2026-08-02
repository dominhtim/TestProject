package com.example.shared.web;

import com.example.shared.error.InvalidSortPropertyException;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Screens a caller-supplied {@code ?sort=} against an allowlist.
 * <p>
 * Shared because every paginated endpoint needs the identical loop; only the
 * permitted set varies, and that belongs to the endpoint.
 */
public final class SortablePropertyValidator {

    private SortablePropertyValidator() {
    }

    /**
     * @throws InvalidSortPropertyException on the first property not in
     *         {@code sortableProperties}, which the exception handler renders
     *         as a 400 rather than letting it reach the criteria builder and
     *         fail there as a 500
     */
    public static void assertSortableOnly(Sort sort, Set<String> sortableProperties) {
        for (Sort.Order order : sort) {
            if (!sortableProperties.contains(order.getProperty())) {
                throw new InvalidSortPropertyException(order.getProperty(), sortableProperties);
            }
        }
    }
}
