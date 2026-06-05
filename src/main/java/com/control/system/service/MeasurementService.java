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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasurementService {

    private final MeasurementRepository measurementRepository;
    private final RateLimitService rateLimitService;
    private final MeasurementMapper measurementMapper;
    private final MessageResolver messages;

    public MeasurementResponse createMeasurement(final MeasurementRequest request, final String clientIp) {
        rateLimitService.checkMeasurementCreation(clientIp);

        final Measurement measurement = measurementMapper.toEntity(request);
        if (measurement.getStatus() == null) {
            measurement.setStatus(SystemStatus.NORMAL);
        }
        measurement.setCreatedAt(Instant.now());

        final Measurement saved = measurementRepository.save(measurement);
        log.debug("Measurement saved id={} temp={} hum={}", saved.getId(), saved.getTemperature(), saved.getHumidity());
        return measurementMapper.toResponse(saved);
    }

    public MeasurementResponse getLatestMeasurement() {
        return measurementRepository.findFirstByOrderByCreatedAtDesc()
            .map(measurementMapper::toResponse)
            .orElseThrow(() -> new NoSuchElementException(messages.get("measurement.notFound")));
    }

    public PageResponse<MeasurementResponse> searchMeasurements(final MeasurementSearchFilter filter, final Pageable pageable) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new IllegalArgumentException(messages.get("error.dateRange"));
        }
        return PageResponse.from(measurementRepository.search(filter, pageable).map(measurementMapper::toResponse));
    }
}
