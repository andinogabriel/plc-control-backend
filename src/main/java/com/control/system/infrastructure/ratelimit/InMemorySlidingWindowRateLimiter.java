package com.control.system.infrastructure.ratelimit;

import com.control.system.infrastructure.ratelimit.RateLimitProperties.Window;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * In-memory sliding-window rate limiter. Single responsibility: count hits per key
 * within a window and escalate to a temporary blacklist when configured.
 *
 * <p>Deliberately dependency-free (no Redis/bucket4j) so the project stays defensible and
 * cheap to host. For a multi-instance deployment, provide another {@link RateLimiter} bean.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InMemorySlidingWindowRateLimiter implements RateLimiter {

    private final RateLimitProperties properties;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Map<String, Long> blacklistUntil = new ConcurrentHashMap<>();

    @Override
    public void check(final String key, final Window window) {
        final long now = System.currentTimeMillis();
        enforceBlacklist(key, now);

        final Deque<Long> timestamps = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            evictExpired(timestamps, now, window.windowMillis());
            timestamps.addLast(now);
            final int count = timestamps.size();

            if (window.blacklistEnabled() && count > window.blacklistThreshold()) {
                blacklist(key, now);
            }
            if (count > window.maxRequests()) {
                // Log only the bucket, never the identifier (IP / email / fingerprint).
                log.warn("Rate limit exceeded on bucket '{}' ({}/{})", bucketOf(key), count, window.maxRequests());
                throw new RateLimitException("Rate limit exceeded", "error.rateLimit.exceeded", key, false);
            }
        }
    }

    private void enforceBlacklist(final String key, final long now) {
        final Long until = blacklistUntil.get(key);
        if (until == null) {
            return;
        }
        if (now < until) {
            throw new RateLimitException("Temporarily blacklisted", "error.rateLimit.blacklisted", key, true);
        }
        blacklistUntil.remove(key);
    }

    private void blacklist(final String key, final long now) {
        final long until = now + TimeUnit.MINUTES.toMillis(properties.blacklistDurationMinutes());
        blacklistUntil.put(key, until);
        log.warn("Blacklisting on bucket '{}' until {}", bucketOf(key), Instant.ofEpochMilli(until));
        throw new RateLimitException("Temporarily blacklisted", "error.rateLimit.blacklisted", key, true);
    }

    /** Returns the bucket prefix of a key ("config-ip", "global", ...) without the identifier. */
    private static String bucketOf(final String key) {
        final int sep = key.indexOf(':');
        return sep > 0 ? key.substring(0, sep) : key;
    }

    private void evictExpired(final Deque<Long> timestamps, final long now, final long windowMs) {
        timestamps.removeIf(t -> now - t > windowMs);
    }

    /** Periodic housekeeping so idle keys do not leak memory on a long-running host. */
    @Scheduled(fixedDelay = 300_000L)
    void cleanup() {
        final long now = System.currentTimeMillis();
        hits.values().forEach(deque -> {
            synchronized (deque) {
                deque.removeIf(t -> now - t > TimeUnit.MINUTES.toMillis(10));
            }
        });
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty();
            }
        });
        blacklistUntil.entrySet().removeIf(e -> now >= e.getValue());
    }
}
