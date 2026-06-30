package com.control.system.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Estado de actividad del sensor/gateway (Raspberry), inferido de la antigüedad "
    + "de la última medición recibida.")
public record SensorStatusResponse(

    @Schema(description = "true si llegó una medición dentro del umbral de actividad", example = "false")
    boolean online,

    @Schema(description = "Instante de la última medición recibida; null si nunca se recibió ninguna")
    Instant lastMeasurementAt,

    @Schema(description = "Antigüedad de la última medición en segundos; null si no hay ninguna", example = "5400")
    Long ageSeconds,

    @Schema(description = "Umbral de inactividad en segundos: si la antigüedad lo supera, se considera offline",
        example = "3600")
    long offlineAfterSeconds
) {
}
