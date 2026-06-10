package com.control.system.service;

import com.control.system.domain.entity.Measurement;
import com.control.system.domain.enums.SystemStatus;
import com.control.system.infrastructure.i18n.MessageResolver;
import com.control.system.infrastructure.ratelimit.RateLimitService;
import com.control.system.mapping.MeasurementMapperImpl;
import com.control.system.repository.MeasurementRepository;
import com.control.system.web.dto.request.MeasurementRequest;
import com.control.system.web.dto.response.MeasurementResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.control.system.web.exception.ResourceNotFoundException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeasurementServiceTest {

    @Mock
    private MeasurementRepository measurementRepository;
    @Mock
    private RateLimitService rateLimitService;
    @Spy
    private MeasurementMapperImpl measurementMapper;
    @Mock
    private MessageResolver messages;
    @Mock
    private MeasurementStreamService streamService;

    @InjectMocks
    private MeasurementService measurementService;

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
}
