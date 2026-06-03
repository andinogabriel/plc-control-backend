package com.control.system.repository.filter;

import java.time.Instant;

/**
 * Optional, composable filter for querying config history. Any null/blank field is ignored,
 * letting the frontend send only the columns the admin filters on.
 *
 * <p>Text filters ({@code createdByName}, {@code createdByEmail}) are case- and
 * accent-insensitive "contains" matches, resolved against the normalised backing fields.
 * Numeric filters ({@code temperatureMin}, {@code temperatureMax}, {@code humidityMin},
 * {@code humidityMax}) are exact-value matches on the stored thresholds.
 */
public record ConfigSearchFilter(
    Instant from,
    Instant to,
    String createdByName,
    String createdByEmail,
    Double temperatureMin,
    Double temperatureMax,
    Double humidityMin,
    Double humidityMax
) {}
