package com.control.system.web.dto.response;

import com.control.system.domain.enums.EventSeverity;
import com.control.system.domain.enums.EventType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Evento derivado del histórico: una transición de estado o una acción del cooler. "
    + "El cliente traduce 'type' a etiqueta/mensaje legible.")
public record EventResponse(

    @Schema(description = "Id estable (id de la medición que disparó el evento + sufijo), "
        + "para que el reconocimiento (ACK) sobreviva recargas", example = "665f...-s")
    String id,

    @Schema(description = "Instante en que ocurrió el evento")
    Instant time,

    @Schema(description = "Severidad para colorear la lámpara del anunciador")
    EventSeverity severity,

    @Schema(description = "Tipo de evento")
    EventType type,

    @Schema(description = "true si es una alarma que el operador debe reconocer")
    boolean ackable,

    @Schema(description = "true si la alarma ya fue reconocida (ACK)")
    boolean acknowledged
) {
    public static EventResponse of(final String id, final Instant time, final EventType type) {
        return new EventResponse(id, time, type.severity(), type, type.ackable(), false);
    }

    public EventResponse withAcknowledged(final boolean ack) {
        return new EventResponse(id, time, severity, type, ackable, ack);
    }
}
