package com.control.system.service;

import com.control.system.domain.entity.Measurement;
import com.control.system.domain.enums.SystemStatus;
import com.control.system.infrastructure.config.SensorProperties;
import com.control.system.infrastructure.i18n.MessageResolver;
import com.control.system.infrastructure.ratelimit.RateLimitService;
import com.control.system.mapping.MeasurementMapperImpl;
import com.control.system.repository.MeasurementRepository;
import com.control.system.web.dto.request.MeasurementRequest;
import com.control.system.web.dto.response.MeasurementResponse;
import com.control.system.web.dto.response.SensorStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.control.system.web.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeasurementServiceTest {

    private static final long OFFLINE_AFTER_SECONDS = 3600;

    @Mock
    private MeasurementRepository measurementRepository;
    @Mock
    private RateLimitService rateLimitService;
    @Spy
    private MeasurementMapperImpl measurementMapper;
    @Mock
    private MessageResolver messages;
    @Mock
    private DateRangeValidator dateRangeValidator;
    @Mock
    private MeasurementStreamService streamService;

    private MeasurementService measurementService;

    @BeforeEach
    void setUp() {
        measurementService = new MeasurementService(measurementRepository, rateLimitService, measurementMapper,
            messages, dateRangeValidator, streamService, new SensorProperties(OFFLINE_AFTER_SECONDS));
    }

    private Measurement measurementAt(final Instant createdAt) {
        return Measurement.builder()
            .id("m1").temperature(24).humidity(50).coolerOn(false).relayOn(false)
            .status(SystemStatus.NORMAL).createdAt(createdAt).build();
    }

    @Test
    void createMeasurementDefaultsStatusToNormalWhenAbsentAndStampsCreatedAt() {
        when(measurementRepository.save(any(Measurement.class))).thenAnswer(inv -> inv.getArgument(0));
        final MeasurementRequest request = new MeasurementRequest(24.3, 45.1, false, false, null);

        final MeasurementResponse response = measurementService.createMeasurement(request, "1.2.3.4");

        verify(rateLimitService).checkMeasurementCreation("1.2.3.4");
        final ArgumentCaptor<Measurement> captor = ArgumentCaptor.forClass(Measurement.class);
        verify(measurementRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SystemStatus.NORMAL);
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(response.status()).isEqualTo(SystemStatus.NORMAL);
        verify(streamService).publish(response);
    }

    @Test
    void createMeasurementKeepsProvidedStatus() {
        when(measurementRepository.save(any(Measurement.class))).thenAnswer(inv -> inv.getArgument(0));
        final MeasurementRequest request = new MeasurementRequest(40.0, 90.0, true, true, SystemStatus.CRITICAL);

        final MeasurementResponse response = measurementService.createMeasurement(request, "ip");

        assertThat(response.status()).isEqualTo(SystemStatus.CRITICAL);
    }

    @Test
    void getLatestMeasurementThrowsWhenEmpty() {
        when(messages.get("measurement.notFound")).thenReturn("measurement.notFound");
        when(measurementRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> measurementService.getLatestMeasurement())
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void sensorStatusOnlineWhenLatestMeasurementIsFresh() {
        final Instant recent = Instant.now().minusSeconds(60);
        when(measurementRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.of(measurementAt(recent)));

        final SensorStatusResponse status = measurementService.getSensorStatus();

        assertThat(status.online()).isTrue();
        assertThat(status.lastMeasurementAt()).isEqualTo(recent);
        assertThat(status.ageSeconds()).isLessThanOrEqualTo(OFFLINE_AFTER_SECONDS);
        assertThat(status.offlineAfterSeconds()).isEqualTo(OFFLINE_AFTER_SECONDS);
    }

    @Test
    void sensorStatusOfflineWhenLatestMeasurementIsStale() {
        final Instant stale = Instant.now().minusSeconds(OFFLINE_AFTER_SECONDS * 2);
        when(measurementRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.of(measurementAt(stale)));

        final SensorStatusResponse status = measurementService.getSensorStatus();

        assertThat(status.online()).isFalse();
        assertThat(status.ageSeconds()).isGreaterThan(OFFLINE_AFTER_SECONDS);
    }

    @Test
    void sensorStatusOfflineWithNullAgeWhenNoMeasurements() {
        when(measurementRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        final SensorStatusResponse status = measurementService.getSensorStatus();

        assertThat(status.online()).isFalse();
        assertThat(status.lastMeasurementAt()).isNull();
        assertThat(status.ageSeconds()).isNull();
        assertThat(status.offlineAfterSeconds()).isEqualTo(OFFLINE_AFTER_SECONDS);
    }
}
