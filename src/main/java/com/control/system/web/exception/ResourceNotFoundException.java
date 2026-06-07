package com.control.system.web.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource does not exist (e.g. no active config, no measurements yet).
 * Maps to HTTP 404.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(final String message) {
        super(HttpStatus.NOT_FOUND, "status.notFound", message);
    }
}
