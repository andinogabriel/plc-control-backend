package com.control.system.infrastructure.ratelimit;

import lombok.Getter;

/**
 * Thrown when a rate limit is exceeded or a key is temporarily blacklisted. Carries a
 * message <em>code</em> (resolved against {@code messages.properties} at the HTTP boundary)
 * rather than a literal string, so the client-facing text stays in Spanish and centralised.
 * The superclass message is kept in English for logs.
 */
@Getter
public class RateLimitException extends RuntimeException {

    private final String messageCode;
    private final String key;
    private final boolean blacklisted;

    public RateLimitException(final String logMessage, final String messageCode, final String key, final boolean blacklisted) {
        super(logMessage);
        this.messageCode = messageCode;
        this.key = key;
        this.blacklisted = blacklisted;
    }
}
