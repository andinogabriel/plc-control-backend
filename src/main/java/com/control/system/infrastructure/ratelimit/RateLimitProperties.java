package com.control.system.infrastructure.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Centralised rate-limit configuration, bound from {@code app.rate-limit.*}.
 * Each {@link Window} is a sliding window with an optional blacklist escalation.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    int blacklistDurationMinutes,
    Window global,
    Window configPerIp,
    Window configPerEmail,
    Window configPerFingerprint,
    Window measurementPerIp
) {

    /**
     * @param maxRequests        requests allowed within the window before a 429 is returned
     * @param windowSeconds      length of the sliding window in seconds
     * @param blacklistThreshold attempts within the window that trigger a temporary blacklist
     *                           (use {@code 0} or less to disable blacklisting for this window)
     */
    public record Window(int maxRequests, int windowSeconds, int blacklistThreshold) {

        public long windowMillis() {
            return windowSeconds * 1000L;
        }

        public boolean blacklistEnabled() {
            return blacklistThreshold > 0;
        }
    }
}
