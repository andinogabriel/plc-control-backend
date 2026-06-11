package com.control.system.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Data retention tunables (prefix {@code app.retention}). The measurement collection grows
 * continuously (one document per reading), so a TTL index drops old data automatically instead
 * of letting the database grow without bound.
 *
 * @param measurementDays how many days to keep measurements; {@code 0} disables expiry (no TTL).
 */
@ConfigurationProperties(prefix = "app.retention")
public record RetentionProperties(
    @DefaultValue("90") long measurementDays
) {
}
