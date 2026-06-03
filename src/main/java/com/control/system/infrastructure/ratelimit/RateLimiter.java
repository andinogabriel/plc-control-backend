package com.control.system.infrastructure.ratelimit;

import com.control.system.infrastructure.ratelimit.RateLimitProperties.Window;

/**
 * Abstraction over the rate-limiting mechanism (DIP): the in-memory sliding window
 * implementation can be swapped for a Redis/bucket4j one without touching callers.
 */
public interface RateLimiter {

    /**
     * Registers one hit against {@code key} and enforces the given {@link Window}.
     *
     * @throws RateLimitException if the limit is exceeded or the key is blacklisted
     */
    void check(String key, Window window);
}
