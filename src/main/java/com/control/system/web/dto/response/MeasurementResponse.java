package com.control.system.web.dto.response;

import com.control.system.domain.enums.SystemStatus;

import java.time.Instant;

public record MeasurementResponse(
    String id,
    double temperature,
    double humidity,
    boolean coolerOn,
    boolean relayOn,
    SystemStatus status,
    Instant createdAt
) {}
