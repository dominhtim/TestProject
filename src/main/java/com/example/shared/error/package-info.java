/**
 * Error handling shared across every feature.
 * <p>
 * Null-marked because {@link
 * org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler}
 * is: its package carries {@code @NullMarked}, so its methods declare
 * non-null parameters and a {@code @Nullable} return. An override living in
 * an unmarked package has <em>unspecified</em> nullness, which cannot be
 * checked against that contract - annotating the override alone does not fix
 * it, because {@code @Nullable} only has defined meaning inside null-marked
 * scope.
 */
@NullMarked
package com.example.shared.error;

import org.jspecify.annotations.NullMarked;
