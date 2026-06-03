package com.control.system.web.dto.request;

import com.control.system.domain.enums.SystemStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record MeasurementRequest(

    @NotNull(message = "{measurement.temperature.required}")
    @DecimalMin(value = "-10.0", message = "{measurement.temperature.min}")
    @DecimalMax(value = "100.0", message = "{measurement.temperature.max}")
    Double temperature,

    @NotNull(message = "{measurement.humidity.required}")
    @DecimalMin(value = "0.0", message = "{measurement.humidity.min}")
    @DecimalMax(value = "100.0", message = "{measurement.humidity.max}")
    Double humidity,

    @NotNull(message = "{measurement.coolerOn.required}")
    Boolean coolerOn,

    @NotNull(message = "{measurement.relayOn.required}")
    Boolean relayOn,

    SystemStatus status
) {}
