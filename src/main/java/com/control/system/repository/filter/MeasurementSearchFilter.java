package com.control.system.repository.filter;

import com.control.system.domain.enums.SystemStatus;

import java.time.Instant;

/**
 * Optional, composable filter for querying measurement history. Any null field is ignored.
 *
 * <p>Date and numeric fields are ranges (inclusive); {@code status} and {@code coolerOn} are
 * exact matches. Backs both the chart query (date range + status) and the table's per-column
 * filters (temperature/humidity ranges, cooler on/off).
 *
 * <p>{@code maxPoints} (chart-only) caps the returned series: when the match exceeds it, the
 * result is down-sampled to roughly {@code maxPoints} points spread across the whole range
 * (instead of the most recent page), so wide ranges show their full span.
 */
public record MeasurementSearchFilter(
    Instant from,
    Instant to,
    SystemStatus status,
    Double temperatureMin,
    Double temperatureMax,
    Double humidityMin,
    Double humidityMax,
    Boolean coolerOn,
    Integer maxPoints
) {}
