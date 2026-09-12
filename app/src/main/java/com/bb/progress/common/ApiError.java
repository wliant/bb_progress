package com.bb.progress.common;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiError(
        int status,
        String code,
        String message,
        List<FieldError> fieldErrors,
        OffsetDateTime timestamp) {

    public record FieldError(String field, String code, String message) {
    }

    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, List.of(), OffsetDateTime.now(Sgt.ZONE));
    }

    public static ApiError of(int status, String code, String message, List<FieldError> fieldErrors) {
        return new ApiError(status, code, message, fieldErrors, OffsetDateTime.now(Sgt.ZONE));
    }
}
