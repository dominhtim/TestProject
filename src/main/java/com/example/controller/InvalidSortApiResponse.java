package com.example.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Shared by every paginated collection endpoint. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "400",
        description = "The requested sort property is not sortable; the permitted set is listed "
                + "under 'sortableProperties'")
public @interface InvalidSortApiResponse {
}
