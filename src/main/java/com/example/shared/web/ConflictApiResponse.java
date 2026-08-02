package com.example.shared.web;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Shared by every operation that writes a versioned row. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "409",
        description = "The resource was modified concurrently; the supplied version is stale")
public @interface ConflictApiResponse {
}
