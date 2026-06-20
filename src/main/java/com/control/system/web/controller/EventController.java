package com.control.system.web.controller;

import com.control.system.service.EventService;
import com.control.system.web.dto.request.EventHistoryQuery;
import com.control.system.web.dto.response.EventAckSummary;
import com.control.system.web.dto.response.EventResponse;
import com.control.system.web.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Eventos", description = "Registro de eventos y alarmas derivado del histórico de mediciones")
public class EventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "Eventos y alarmas (paginado)",
        description = "Deriva del histórico las transiciones de estado (entrada a fuera de rango / crítico, "
            + "retorno a normal) y las acciones del cooler, y las pagina en el servidor (más nuevo primero). "
            + "El cliente recibe sólo una página, no todo el histórico.")
    @ApiResponse(responseCode = "200", description = "Página de eventos")
    public PageResponse<EventResponse> getEvents(
        @ParameterObject final EventHistoryQuery query,
        @ParameterObject @PageableDefault(size = 20) final Pageable pageable
    ) {
        return eventService.getEvents(query.from(), query.to(), pageable);
    }

    @GetMapping("/unacknowledged-count")
    @Operation(summary = "Conteo global de alarmas sin reconocer",
        description = "Cuenta las alarmas sin ACK en toda la ventana (no sólo la página), para el badge global.")
    @ApiResponse(responseCode = "200", description = "Resumen de reconocimiento")
    public EventAckSummary getUnacknowledgedCount(@ParameterObject final EventHistoryQuery query) {
        return new EventAckSummary(eventService.countUnacknowledged(query.from(), query.to()));
    }

    @PostMapping("/{id}/ack")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reconocer una alarma",
        description = "Marca una alarma como reconocida (ACK). Idempotente.")
    @ApiResponse(responseCode = "204", description = "Alarma reconocida")
    public void acknowledge(@PathVariable final String id) {
        eventService.acknowledge(id);
    }

    @PostMapping("/ack-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reconocer todas las alarmas de la ventana",
        description = "Reconoce todas las alarmas sin ACK dentro de la ventana consultada.")
    @ApiResponse(responseCode = "204", description = "Alarmas reconocidas")
    public void acknowledgeAll(@ParameterObject final EventHistoryQuery query) {
        eventService.acknowledgeAll(query.from(), query.to());
    }
}
