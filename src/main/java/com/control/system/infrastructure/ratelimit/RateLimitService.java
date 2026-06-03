package com.control.system.infrastructure.ratelimit;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Application-level rate-limit policy. Composes the generic {@link RateLimiter} with the
 * business buckets. Knows nothing about HTTP — callers pass the resolved identifiers.
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    /** Global ceiling applied to every API request by the rate-limit filter. */
    public void checkGlobal(final String clientIp) {
        rateLimiter.check("global:" + clientIp, properties.global());
    }

    /** Stricter limits for the audited, write-heavy config creation path. */
    public void checkConfigCreation(final String clientIp, final String email, final String fingerprint) {
        rateLimiter.check("config-ip:" + clientIp, properties.configPerIp());
        rateLimiter.check("config-email:" + StringUtils.lowerCase(email), properties.configPerEmail());
        if (StringUtils.isNotBlank(fingerprint)) {
            rateLimiter.check("config-fp:" + fingerprint, properties.configPerFingerprint());
        }
    }

    /** Limit for the Raspberry's measurement ingestion path. */
    public void checkMeasurementCreation(final String clientIp) {
        rateLimiter.check("measurement-ip:" + clientIp, properties.measurementPerIp());
    }
}
