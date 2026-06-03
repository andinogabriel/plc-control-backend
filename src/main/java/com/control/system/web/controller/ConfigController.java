package com.control.system.web.controller;

import com.control.system.infrastructure.web.ClientIpResolver;
import com.control.system.service.ConfigService;
import com.control.system.web.dto.request.ConfigHistoryQuery;
import com.control.system.web.dto.request.ConfigRequest;
import com.control.system.web.dto.response.ConfigResponse;
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
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "Configuración", description = "Gestión de umbrales, histéresis e intervalo de medición (historial versionado y auditado)")
public class ConfigController {

    private final ConfigService configService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Crear una nueva configuración",
        description = "Crea una configuración nueva y la marca como activa, desactivando la anterior. "
            + "Registra metadata de auditoría (nombre, email, IP, user-agent)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Configuración creada y marcada como activa"),
        @ApiResponse(responseCode = "400", description = "Body inválido o reglas cruzadas (min >= max)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "413", description = "Body demasiado grande",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Límite de solicitudes excedido (rate limiting)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    public ConfigResponse createConfig(
        @Valid @RequestBody final ConfigRequest request,
        final HttpServletRequest httpRequest
    ) {
        final String clientIp = clientIpResolver.resolve(httpRequest);
        final String userAgent = httpRequest.getHeader("User-Agent");
        return configService.createConfig(request, clientIp, userAgent);
    }

    @GetMapping("/latest")
    @Operation(summary = "Obtener la configuración activa",
        description = "Devuelve la configuración marcada como activa más reciente.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Configuración activa"),
        @ApiResponse(responseCode = "404", description = "No hay configuración activa",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    public ConfigResponse getLatestConfig() {
        return configService.getLatestConfig();
    }

    @GetMapping("/history")
    @Operation(summary = "Historial de configuraciones (paginado)",
        description = "Lista el historial con filtros opcionales por fecha, nombre, email y rangos de umbrales. "
            + "Soporta paginación y ordenamiento estándar de Spring (page, size, sort).")
    @ApiResponse(responseCode = "200", description = "Página de configuraciones")
    public PageResponse<ConfigResponse> getConfigHistory(
        @ParameterObject final ConfigHistoryQuery query,
        @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) final Pageable pageable
    ) {
        return configService.searchConfigHistory(query.toSearchFilter(), pageable);
    }
}
