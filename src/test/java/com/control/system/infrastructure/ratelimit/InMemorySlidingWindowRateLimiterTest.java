package com.control.system.infrastructure.ratelimit;

import com.control.system.infrastructure.ratelimit.RateLimitProperties.Window;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void countsAtomicallyUnderConcurrentLoad() throws InterruptedException {
        final int maxRequests = 50;
        final int threads = 400;
        final Window window = new Window(maxRequests, 60, 0); // long window, no blacklist
        final InMemorySlidingWindowRateLimiter limiter = limiterWith(window);

        final ExecutorService pool = Executors.newFixedThreadPool(32);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicInteger allowed = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                try {
                    start.await();
                    limiter.check("shared-key", window);
                    allowed.incrementAndGet();
                } catch (final RateLimitException e) {
                    rejected.incrementAndGet();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown(); // release all threads at once to maximise contention
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // Exactly maxRequests must pass; the synchronized counting must not over- or under-admit.
        assertThat(allowed.get()).isEqualTo(maxRequests);
        assertThat(rejected.get()).isEqualTo(threads - maxRequests);
    }
}
