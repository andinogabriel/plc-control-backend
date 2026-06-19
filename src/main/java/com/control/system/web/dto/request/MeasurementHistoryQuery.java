package com.control.system.web.dto.request;

import com.control.system.domain.enums.SystemStatus;
import com.control.system.repository.filter.MeasurementSearchFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

/**
 * Query-parameter object for the measurement history endpoint. Spring binds matching request
 * parameters by name, keeping the controller method signature small. All fields are optional.
 *
 * <p>Date and numeric fields are ranges; {@code status} and {@code coolerOn} are exact matches.
 */
public record MeasurementHistoryQuery(

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    Instant from,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    Instant to,

    SystemStatus status,
    Double temperatureMin,
    Double temperatureMax,
    Double humidityMin,
    Double humidityMax,
    Boolean coolerOn,

    @Schema(description = "Para gráficos: si el rango tiene más lecturas que esto, se devuelve una "
        + "serie down-sampleada repartida en todo el rango (en vez de la página más reciente). "
        + "Se ignora <= 0 y se acota a 5000.",
        example = "800")
    Integer maxPoints
) {

    public MeasurementSearchFilter toSearchFilter() {
        return new MeasurementSearchFilter(
            from, to, status, temperatureMin, temperatureMax, humidityMin, humidityMax, coolerOn, maxPoints);
    }
}
