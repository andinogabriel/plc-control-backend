package com.control.system.web.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for application-level exceptions that map to a deliberate HTTP response.
 * <p>
 * Carrying the {@link HttpStatus} (and the i18n key for the response title) on the exception
 * itself decouples the HTTP status from generic JDK exception types. A stray
 * {@link IllegalArgumentException} thrown by a bug or a third-party library is no longer
 * silently turned into a 400; only the explicit subclasses here produce a 4xx, while anything
 * unexpected falls through to the generic 500 handler.
 */
public abstract class ApiException extends RuntimeException {

    private final transient HttpStatus status;
    private final String titleKey;

    protected ApiException(final HttpStatus status, final String titleKey, final String message) {
        super(message);
        this.status = status;
        this.titleKey = titleKey;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** i18n message key for the human-readable response title (e.g. {@code status.notFound}). */
    public String getTitleKey() {
        return titleKey;
    }
}
