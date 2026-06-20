package com.control.system.web.dto.request;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

/**
 * Query-parameter object for the events endpoint. Both bounds are optional; when {@code from} is
 * omitted the service falls back to a default window ending at {@code to} (or now).
 */
public record EventHistoryQuery(

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    Instant from,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    Instant to
) {}
