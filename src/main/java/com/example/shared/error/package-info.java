/**
 * Error handling shared across every feature.
 * <p>
 * Null-marked to match {@link
 * org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler},
 * whose package carries {@code @NullMarked} and whose overridable methods
 * therefore declare non-null parameters and a {@code @Nullable} return. The
 * three overrides in {@link com.example.shared.error.GlobalExceptionHandler}
 * mirror that contract exactly.
 * <p>
 * Note this did <em>not</em> silence SonarQube java:S2638 on those overrides;
 * see GlobalExceptionHandler's class Javadoc for why that is a false positive
 * and where it is suppressed. This annotation stays because declaring the
 * package's null contract is correct on its own merits, not because it fixed
 * the warning.
 */
@NullMarked
package com.example.shared.error;

import org.jspecify.annotations.NullMarked;
