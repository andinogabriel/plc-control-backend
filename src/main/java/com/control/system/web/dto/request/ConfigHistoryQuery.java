package com.control.system.web.dto.request;

import com.control.system.repository.filter.ConfigSearchFilter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

/**
 * Query-parameter object for the config history endpoint. Spring binds matching request
 * parameters by name into this record (constructor binding), keeping the controller method
 * signature small instead of a long list of {@code @RequestParam}s. Pagination/sorting are
 * handled separately by the {@code Pageable} argument.
 *
 * <p>Text filters are case/accent-insensitive "contains"; numeric filters are exact matches.
 */
public record ConfigHistoryQuery(

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    Instant from,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    Instant to,

    String createdByName,
    String createdByEmail,
    Double temperatureMin,
    Double temperatureMax,
    Double humidityMin,
    Double humidityMax
) {

    /** Translates this web-layer query into the repository search filter. */
    public ConfigSearchFilter toSearchFilter() {
        return new ConfigSearchFilter(
            from, to, createdByName, createdByEmail,
            temperatureMin, temperatureMax, humidityMin, humidityMax);
    }
}
