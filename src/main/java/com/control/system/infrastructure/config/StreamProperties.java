package com.control.system.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables for the real-time SSE stream (prefix {@code app.stream}). Keeps resource usage
 * bounded and lets the feature be turned off entirely. Every value has a sensible default and
 * can be overridden with an environment variable (e.g. {@code APP_STREAM_MAX_SUBSCRIBERS=10}).
 *
 * @param enabled             master switch; when false no subscribers are accepted and nothing
 *                            is pushed (the frontend falls back to polling).
 * @param heartbeatIntervalMs how often a keep-alive comment is sent to detect dead connections.
 * @param maxSubscribers      hard cap on TOTAL concurrent open streams across all clients; extra
 *                            connections are closed immediately so the server cannot be exhausted.
 * @param maxSubscribersPerIp per-IP cap so a single client opening many tabs/kiosks cannot hog
 *                            all the slots.
 * @param timeoutMs           per-connection timeout (0 = no timeout). A finite value recycles
 *                            long-lived/stale connections behind proxies.
 */
@ConfigurationProperties(prefix = "app.stream")
public record StreamProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("20000") long heartbeatIntervalMs,
    @DefaultValue("20") int maxSubscribers,
    @DefaultValue("3") int maxSubscribersPerIp,
    @DefaultValue("0") long timeoutMs
) {
}
