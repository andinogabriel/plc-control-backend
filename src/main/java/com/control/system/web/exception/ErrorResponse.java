package com.control.system.web.exception;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    int status,
    String error,
    String message,
    Instant timestamp,
    List<String> details
) {
    public static ErrorResponse of(final int status, final String error, final String message) {
        return new ErrorResponse(status, error, message, Instant.now(), List.of());
    }

    public static ErrorResponse of(final int status, final String error, final String message, final List<String> details) {
        return new ErrorResponse(status, error, message, Instant.now(), details);
    }
}
