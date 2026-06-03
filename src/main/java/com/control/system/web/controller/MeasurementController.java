package com.control.system.web.controller;

import com.control.system.infrastructure.web.ClientIpResolver;
import com.control.system.service.MeasurementService;
import com.control.system.web.dto.request.MeasurementHistoryQuery;
import com.control.system.web.dto.request.MeasurementRequest;
import com.control.system.web.dto.response.MeasurementResponse;
import com.control.system.web.dto.response.PageResponse;
import com.control.system.web.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/measurements")
@RequiredArgsConstructor
@Tag(name = "Mediciones", description = "Lecturas de temperatura y humedad enviadas por la Raspberry")
public class MeasurementController {

    private final MeasurementService measurementService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una medición",
        description = "Persiste una lectura del sensor con el estado calculado del cooler. Lo usa la Raspberry.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Medición registrada"),
        @ApiResponse(responseCode = "400", description = "Body inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "413", description = "Body demasiado grande",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Límite de solicitudes excedido (rate limiting)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    public MeasurementResponse createMeasurement(
        @Valid @RequestBody final MeasurementRequest request,
        final HttpServletRequest httpRequest
    ) {
        return measurementService.createMeasurement(request, clientIpResolver.resolve(httpRequest));
    }

    @GetMapping("/latest")
    @Operation(summary = "Obtener la última medición",
        description = "Devuelve la medición más reciente (último valor válido).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Última medición"),
        @ApiResponse(responseCode = "404", description = "No hay mediciones",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    public MeasurementResponse getLatestMeasurement() {
        return measurementService.getLatestMeasurement();
    }

    @GetMapping
    @Operation(summary = "Mediciones (paginado)",
        description = "Lista las mediciones con filtros opcionales por fecha, estado, rangos de temperatura/humedad "
            + "y cooler. Soporta paginación y ordenamiento estándar de Spring (page, size, sort).")
    @ApiResponse(responseCode = "200", description = "Página de mediciones")
    public PageResponse<MeasurementResponse> getMeasurements(
        @ParameterObject final MeasurementHistoryQuery query,
        @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) final Pageable pageable
    ) {
        return measurementService.searchMeasurements(query.toSearchFilter(), pageable);
    }
}
