package com.control.system.web.dto.response;

import com.control.system.domain.enums.SystemStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Medición registrada")
public record MeasurementResponse(
    String id,
    double temperature,
    double humidity,
    boolean coolerOn,
    boolean relayOn,
    SystemStatus status,
    Instant createdAt
) {}
