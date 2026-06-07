package com.control.system.web.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the request is syntactically valid but breaks a business/validation rule
 * (e.g. an invalid date range, thresholds out of order). Maps to HTTP 400.
 */
public class BadRequestException extends ApiException {

    public BadRequestException(final String message) {
        super(HttpStatus.BAD_REQUEST, "status.badRequest", message);
    }
}
