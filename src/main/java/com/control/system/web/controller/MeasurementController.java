package com.control.system.web.controller;

import com.control.system.infrastructure.web.ClientIpResolver;
import com.control.system.service.MeasurementService;
import com.control.system.web.dto.request.MeasurementHistoryQuery;
import com.control.system.web.dto.request.MeasurementRequest;
import com.control.system.web.dto.response.MeasurementResponse;
import com.control.system.web.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "Measurements", description = "Temperature and humidity readings from the sensor")
public class MeasurementController {

    private final MeasurementService measurementService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a new measurement from the Raspberry Pi")
    public MeasurementResponse createMeasurement(
        @Valid @RequestBody final MeasurementRequest request,
        final HttpServletRequest httpRequest
    ) {
        return measurementService.createMeasurement(request, clientIpResolver.resolve(httpRequest));
    }

    @GetMapping("/latest")
    @Operation(summary = "Get the most recent measurement (last valid value)")
    public MeasurementResponse getLatestMeasurement() {
        return measurementService.getLatestMeasurement();
    }

    @GetMapping
    @Operation(summary = "Get paginated measurements with optional date/numeric/status/cooler filters")
    public PageResponse<MeasurementResponse> getMeasurements(
        final MeasurementHistoryQuery query,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) final Pageable pageable
    ) {
        return measurementService.searchMeasurements(query.toSearchFilter(), pageable);
    }
}
