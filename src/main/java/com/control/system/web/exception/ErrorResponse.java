package com.control.system.web.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Respuesta de error estándar (mensajes en español)")
public record ErrorResponse(
    @Schema(example = "400") int status,
    @Schema(example = "Solicitud Incorrecta") String error,
    @Schema(example = "Uno o mas campos son invalidos") String message,
    Instant timestamp,
    @Schema(description = "Detalle por campo cuando aplica") List<String> details
) {
    public static ErrorResponse of(final int status, final String error, final String message) {
        return new ErrorResponse(status, error, message, Instant.now(), List.of());
    }

    public static ErrorResponse of(final int status, final String error, final String message, final List<String> details) {
        return new ErrorResponse(status, error, message, Instant.now(), details);
    }
}
