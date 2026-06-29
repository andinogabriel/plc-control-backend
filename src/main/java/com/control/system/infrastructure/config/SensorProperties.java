package com.control.system.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables for sensor/gateway liveness (prefix {@code app.sensor}). The backend is passive about
 * ingestion, so "offline" is inferred from the age of the most recent measurement.
 *
 * @param offlineAfterSeconds the Raspberry/gateway is considered offline when no measurement has
 *                            arrived in this many seconds (default 3600 = 1 hour). Drives both
 *                            {@code GET /api/measurements/status} and the derived SENSOR_OFFLINE
 *                            alarm. Override with {@code APP_SENSOR_OFFLINE_AFTER_SECONDS}.
 */
@ConfigurationProperties(prefix = "app.sensor")
public record SensorProperties(
    @DefaultValue("3600") long offlineAfterSeconds
) {
}
