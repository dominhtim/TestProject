package com.example.shared.web;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Shared by every operation that binds a request body with {@code @Valid}. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "400",
        description = "Request validation failed; the offending fields are listed under 'errors'")
public @interface ValidationFailedApiResponse {
}
