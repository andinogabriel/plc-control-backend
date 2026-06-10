package com.control.system.service;

import com.control.system.domain.entity.Measurement;
import com.control.system.domain.enums.SystemStatus;
import com.control.system.infrastructure.i18n.MessageResolver;
import com.control.system.infrastructure.ratelimit.RateLimitService;
import com.control.system.mapping.MeasurementMapper;
import com.control.system.repository.MeasurementRepository;
import com.control.system.repository.filter.MeasurementSearchFilter;
import com.control.system.web.dto.request.MeasurementRequest;
import com.control.system.web.dto.response.MeasurementResponse;
import com.control.system.web.dto.response.PageResponse;
import com.control.system.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasurementService {

    private final MeasurementRepository measurementRepository;
    private final RateLimitService rateLimitService;
    private final MeasurementMapper measurementMapper;
    private final MessageResolver messages;
    private final DateRangeValidator dateRangeValidator;
    private final MeasurementStreamService streamService;

    public MeasurementResponse createMeasurement(final MeasurementRequest request, final String clientIp) {
        rateLimitService.checkMeasurementCreation(clientIp);

        final Measurement measurement = measurementMapper.toEntity(request);
        if (measurement.getStatus() == null) {
            measurement.setStatus(SystemStatus.NORMAL);
        }
        measurement.setCreatedAt(Instant.now());

        final Measurement saved = measurementRepository.save(measurement);
        log.debug("Measurement saved id={} temp={} hum={}", saved.getId(), saved.getTemperature(), saved.getHumidity());
        final MeasurementResponse response = measurementMapper.toResponse(saved);
        streamService.publish(response);
        return response;
    }

    public MeasurementResponse getLatestMeasurement() {
        return measurementRepository.findFirstByOrderByCreatedAtDesc()
            .map(measurementMapper::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException(messages.get("measurement.notFound")));
    }

    public PageResponse<MeasurementResponse> searchMeasurements(final MeasurementSearchFilter filter, final Pageable pageable) {
        dateRangeValidator.validate(filter.from(), filter.to());
        return PageResponse.from(measurementRepository.search(filter, pageable).map(measurementMapper::toResponse));
    }
}
