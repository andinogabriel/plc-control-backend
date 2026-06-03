package com.control.system.web.dto.request;

import com.control.system.domain.enums.SystemStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Lectura del sensor publicada por la Raspberry, con el estado calculado del cooler")
public record MeasurementRequest(

    @Schema(description = "Temperatura medida (°C)", example = "24.3", minimum = "-10", maximum = "100")
    @NotNull(message = "{measurement.temperature.required}")
    @DecimalMin(value = "-10.0", message = "{measurement.temperature.min}")
    @DecimalMax(value = "100.0", message = "{measurement.temperature.max}")
    Double temperature,

    @Schema(description = "Humedad medida (%)", example = "45.1", minimum = "0", maximum = "100")
    @NotNull(message = "{measurement.humidity.required}")
    @DecimalMin(value = "0.0", message = "{measurement.humidity.min}")
    @DecimalMax(value = "100.0", message = "{measurement.humidity.max}")
    Double humidity,

    @Schema(description = "Estado calculado del cooler", example = "false")
    @NotNull(message = "{measurement.coolerOn.required}")
    Boolean coolerOn,

    @Schema(description = "Estado del relay que acciona el cooler", example = "false")
    @NotNull(message = "{measurement.relayOn.required}")
    Boolean relayOn,

    @Schema(description = "Estado general del sistema; si se omite, el backend usa NORMAL", example = "NORMAL")
    SystemStatus status
) {}
