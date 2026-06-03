package com.control.system.web.dto.response;

import java.time.Instant;

/**
 * Outbound view of a configuration.
 *
 * @param hysteresisTemperature dead-band (degrees Celsius) around the temperature thresholds
 *                              that prevents the relay/cooler from rapidly toggling near a limit.
 * @param hysteresisHumidity    dead-band (percentage points) around the humidity thresholds,
 *                              with the same anti-chatter purpose.
 */
public record ConfigResponse(
    String id,
    double temperatureMin,
    double temperatureMax,
    double humidityMin,
    double humidityMax,
    double hysteresisTemperature,
    double hysteresisHumidity,
    String createdByName,
    String createdByEmail,
    String clientIp,
    String userAgent,
    String deviceFingerprint,
    boolean active,
    Instant createdAt
) {}
