package com.control.system.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload to create a configuration. All messages resolve from
 * {@code messages.properties} (Spanish) via the {@code {key}} interpolation syntax.
 *
 * @param hysteresisTemperature dead-band (in degrees Celsius) around the temperature
 *                              thresholds. Hysteresis prevents the relay/cooler from rapidly
 *                              toggling when the reading hovers at a threshold: the cooler
 *                              turns on at {@code temperatureMax} and only off again once the
 *                              reading drops below {@code temperatureMax - hysteresisTemperature}.
 * @param hysteresisHumidity   dead-band (in percentage points) around the humidity thresholds,
 *                              applied with the same anti-chatter logic as the temperature one.
 * @param measurementIntervalSeconds how often the Raspberry samples the sensor and publishes a
 *                              measurement; versioned with the thresholds so it is auditable.
 */
@Schema(description = "Payload para crear una configuración (umbrales, histéresis e intervalo)")
public record ConfigRequest(

    @Schema(description = "Nombre de quien realiza el cambio", example = "Gabriel Andino")
    @NotBlank(message = "{config.createdByName.required}")
    @Size(max = 100, message = "{config.createdByName.size}")
    String createdByName,

    @Schema(description = "Email de quien realiza el cambio", example = "gabriel@example.com")
    @NotBlank(message = "{config.createdByEmail.required}")
    @Email(message = "{config.createdByEmail.invalid}")
    @Size(max = 150, message = "{config.createdByEmail.size}")
    String createdByEmail,

    @Schema(description = "Umbral mínimo de temperatura (°C)", example = "18", minimum = "-10", maximum = "60")
    @NotNull(message = "{config.temperatureMin.required}")
    @DecimalMin(value = "-10.0", message = "{config.temperature.min}")
    @DecimalMax(value = "60.0", message = "{config.temperature.max}")
    Double temperatureMin,

    @Schema(description = "Umbral máximo de temperatura (°C)", example = "26", minimum = "-10", maximum = "60")
    @NotNull(message = "{config.temperatureMax.required}")
    @DecimalMin(value = "-10.0", message = "{config.temperature.min}")
    @DecimalMax(value = "60.0", message = "{config.temperature.max}")
    Double temperatureMax,

    @Schema(description = "Umbral mínimo de humedad (%)", example = "30", minimum = "0", maximum = "100")
    @NotNull(message = "{config.humidityMin.required}")
    @DecimalMin(value = "0.0", message = "{config.humidity.min}")
    @DecimalMax(value = "100.0", message = "{config.humidity.max}")
    Double humidityMin,

    @Schema(description = "Umbral máximo de humedad (%)", example = "60", minimum = "0", maximum = "100")
    @NotNull(message = "{config.humidityMax.required}")
    @DecimalMin(value = "0.0", message = "{config.humidity.min}")
    @DecimalMax(value = "100.0", message = "{config.humidity.max}")
    Double humidityMax,

    @Schema(description = "Banda muerta de temperatura para evitar oscilaciones del cooler", example = "1.5")
    @NotNull(message = "{config.hysteresisTemperature.required}")
    @Positive(message = "{config.hysteresis.positive}")
    @DecimalMax(value = "20.0", message = "{config.hysteresis.max}")
    Double hysteresisTemperature,

    @Schema(description = "Banda muerta de humedad para evitar oscilaciones del cooler", example = "2.0")
    @NotNull(message = "{config.hysteresisHumidity.required}")
    @Positive(message = "{config.hysteresis.positive}")
    @DecimalMax(value = "20.0", message = "{config.hysteresis.max}")
    Double hysteresisHumidity,

    @Schema(description = "Cada cuántos segundos la Raspberry mide y publica", example = "30", minimum = "5", maximum = "3600")
    @NotNull(message = "{config.measurementInterval.required}")
    @Min(value = 5, message = "{config.measurementInterval.range}")
    @Max(value = 3600, message = "{config.measurementInterval.range}")
    Integer measurementIntervalSeconds,

    @Schema(description = "Identificador opcional del dispositivo del cliente (anti-abuso)", example = "fp-abc123")
    @Size(max = 200, message = "{config.deviceFingerprint.size}")
    String deviceFingerprint
) {}
