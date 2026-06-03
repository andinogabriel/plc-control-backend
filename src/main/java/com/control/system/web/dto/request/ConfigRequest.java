package com.control.system.web.dto.request;

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
public record ConfigRequest(

    @NotBlank(message = "{config.createdByName.required}")
    @Size(max = 100, message = "{config.createdByName.size}")
    String createdByName,

    @NotBlank(message = "{config.createdByEmail.required}")
    @Email(message = "{config.createdByEmail.invalid}")
    @Size(max = 150, message = "{config.createdByEmail.size}")
    String createdByEmail,

    @NotNull(message = "{config.temperatureMin.required}")
    @DecimalMin(value = "-10.0", message = "{config.temperature.min}")
    @DecimalMax(value = "60.0", message = "{config.temperature.max}")
    Double temperatureMin,

    @NotNull(message = "{config.temperatureMax.required}")
    @DecimalMin(value = "-10.0", message = "{config.temperature.min}")
    @DecimalMax(value = "60.0", message = "{config.temperature.max}")
    Double temperatureMax,

    @NotNull(message = "{config.humidityMin.required}")
    @DecimalMin(value = "0.0", message = "{config.humidity.min}")
    @DecimalMax(value = "100.0", message = "{config.humidity.max}")
    Double humidityMin,

    @NotNull(message = "{config.humidityMax.required}")
    @DecimalMin(value = "0.0", message = "{config.humidity.min}")
    @DecimalMax(value = "100.0", message = "{config.humidity.max}")
    Double humidityMax,

    @NotNull(message = "{config.hysteresisTemperature.required}")
    @Positive(message = "{config.hysteresis.positive}")
    @DecimalMax(value = "20.0", message = "{config.hysteresis.max}")
    Double hysteresisTemperature,

    @NotNull(message = "{config.hysteresisHumidity.required}")
    @Positive(message = "{config.hysteresis.positive}")
    @DecimalMax(value = "20.0", message = "{config.hysteresis.max}")
    Double hysteresisHumidity,

    @NotNull(message = "{config.measurementInterval.required}")
    @Min(value = 5, message = "{config.measurementInterval.range}")
    @Max(value = 3600, message = "{config.measurementInterval.range}")
    Integer measurementIntervalSeconds,

    @Size(max = 200, message = "{config.deviceFingerprint.size}")
    String deviceFingerprint
) {}
