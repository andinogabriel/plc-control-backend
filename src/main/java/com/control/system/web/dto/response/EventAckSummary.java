package com.control.system.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resumen de reconocimiento de alarmas para la ventana consultada")
public record EventAckSummary(

    @Schema(description = "Cantidad de alarmas sin reconocer en toda la ventana", example = "7")
    long unacknowledged
) {}
