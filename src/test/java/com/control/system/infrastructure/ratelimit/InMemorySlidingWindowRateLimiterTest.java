package com.control.system.infrastructure.ratelimit;

import com.control.system.infrastructure.ratelimit.RateLimitProperties.Window;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemorySlidingWindowRateLimiterTest {

    private InMemorySlidingWindowRateLimiter limiterWith(final Window window) {
        final RateLimitProperties props = new RateLimitProperties(15, window, window, window, window, window);
        return new InMemorySlidingWindowRateLimiter(props);
    }

    @Test
    void allowsRequestsWithinTheLimit() {
        final Window window = new Window(2, 60, 0);
        final InMemorySlidingWindowRateLimiter limiter = limiterWith(window);

        assertThatCode(() -> {
            limiter.check("k", window);
            limiter.check("k", window);
        }).doesNotThrowAnyException();
    }

    @Test
    void throws429WhenLimitExceededWithoutBlacklist() {
        final Window window = new Window(2, 60, 0);
        final InMemorySlidingWindowRateLimiter limiter = limiterWith(window);
        limiter.check("k", window);
        limiter.check("k", window);

        final RateLimitException ex = assertThrows(RateLimitException.class, () -> limiter.check("k", window));
        assertThat(ex.isBlacklisted()).isFalse();
    }

    @Test
    void blacklistsKeyWhenThresholdExceeded() {
        final Window window = new Window(2, 60, 2);
        final InMemorySlidingWindowRateLimiter limiter = limiterWith(window);
        limiter.check("k", window);
        limiter.check("k", window);

        final RateLimitException ex = assertThrows(RateLimitException.class, () -> limiter.check("k", window));
        assertThat(ex.isBlacklisted()).isTrue();

        // Subsequent calls remain blocked while blacklisted.
        assertThatThrownBy(() -> limiter.check("k", window))
            .isInstanceOf(RateLimitException.class)
            .hasMessageContaining("blacklisted");
    }

    @Test
    void tracksKeysIndependently() {
        final Window window = new Window(1, 60, 0);
        final InMemorySlidingWindowRateLimiter limiter = limiterWith(window);

        assertThatCode(() -> {
            limiter.check("a", window);
            limiter.check("b", window);
        }).doesNotThrowAnyException();
    }
}
