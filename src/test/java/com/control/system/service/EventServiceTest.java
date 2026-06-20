package com.control.system.service;

import com.control.system.domain.entity.EventAck;
import com.control.system.domain.entity.Measurement;
import com.control.system.domain.enums.EventSeverity;
import com.control.system.domain.enums.EventType;
import com.control.system.domain.enums.SystemStatus;
import com.control.system.repository.EventAckRepository;
import com.control.system.repository.MeasurementRepository;
import com.control.system.web.dto.response.EventResponse;
import com.control.system.web.dto.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private MeasurementRepository measurementRepository;
    @Mock
    private EventAckRepository eventAckRepository;
    @Mock
    private DateRangeValidator dateRangeValidator;

    @InjectMocks
    private EventService eventService;

    private Measurement m(final String id, final String at, final SystemStatus status, final boolean cooler) {
        return Measurement.builder()
            .id(id).temperature(20).humidity(50).coolerOn(cooler).relayOn(cooler)
            .status(status).createdAt(Instant.parse(at)).build();
    }

    @Test
    void derivesAlarmReturnAndCoolerEventsNewestFirstIgnoringFirstSample() {
        final List<EventResponse> events = EventService.deriveEvents(List.of(
            m("1", "2026-06-01T10:00:00Z", SystemStatus.NORMAL, false),
            m("2", "2026-06-01T10:01:00Z", SystemStatus.WARNING_TEMP, true),   // status -> alarm + cooler ON
            m("3", "2026-06-01T10:02:00Z", SystemStatus.NORMAL, true)          // status -> return to normal
        ));

        assertThat(events).extracting(EventResponse::type)
            .containsExactly(EventType.RETURN_TO_NORMAL, EventType.COOLER_ON, EventType.TEMP_OUT_OF_RANGE);
        assertThat(events.get(0)).extracting(EventResponse::severity, EventResponse::ackable)
            .containsExactly(EventSeverity.SUCCESS, false);
        assertThat(events).extracting(EventResponse::id).containsExactly("3-s", "2-c", "2-s");
    }

    @Test
    void emitsNoEventsWhenNothingChanges() {
        final List<EventResponse> events = EventService.deriveEvents(List.of(
            m("1", "2026-06-01T10:00:00Z", SystemStatus.NORMAL, false),
            m("2", "2026-06-01T10:01:00Z", SystemStatus.NORMAL, false)
        ));
        assertThat(events).isEmpty();
    }

    @Test
    void paginatesDerivedEventsAndFlagsAcknowledged() {
        when(measurementRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(
            m("1", "2026-06-01T10:00:00Z", SystemStatus.NORMAL, false),
            m("2", "2026-06-01T10:01:00Z", SystemStatus.WARNING_TEMP, false),
            m("3", "2026-06-01T10:02:00Z", SystemStatus.NORMAL, false),
            m("4", "2026-06-01T10:03:00Z", SystemStatus.CRITICAL, false),
            m("5", "2026-06-01T10:04:00Z", SystemStatus.NORMAL, false),
            m("6", "2026-06-01T10:05:00Z", SystemStatus.WARNING_HUMIDITY, false)
        ));
        // Newest first, the first page holds "6-s" (HUMIDITY) and "5-s" (RETURN). "6-s" is acked.
        when(eventAckRepository.findAllById(anyIterable())).thenReturn(List.of(new EventAck("6-s", Instant.now())));

        final PageResponse<EventResponse> first = eventService.getEvents(null, null, PageRequest.of(0, 2));

        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.content()).hasSize(2);
        assertThat(first.content().get(0)).extracting(EventResponse::id, EventResponse::acknowledged)
            .containsExactly("6-s", true);
        assertThat(first.content().get(1).acknowledged()).isFalse();
    }

    @Test
    void countsUnacknowledgedAlarmsAcrossTheWindow() {
        when(measurementRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(
            m("1", "2026-06-01T10:00:00Z", SystemStatus.NORMAL, false),
            m("2", "2026-06-01T10:01:00Z", SystemStatus.WARNING_TEMP, false),  // alarm 2-s
            m("3", "2026-06-01T10:02:00Z", SystemStatus.CRITICAL, false)        // alarm 3-s
        ));
        when(eventAckRepository.findAllById(anyIterable())).thenReturn(List.of(new EventAck("3-s", Instant.now())));

        assertThat(eventService.countUnacknowledged(null, null)).isEqualTo(1);
    }

    @Test
    void acknowledgeAllSavesOnlyTheNotYetAcknowledgedAlarms() {
        when(measurementRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(
            m("1", "2026-06-01T10:00:00Z", SystemStatus.NORMAL, false),
            m("2", "2026-06-01T10:01:00Z", SystemStatus.WARNING_TEMP, false),  // alarm 2-s
            m("3", "2026-06-01T10:02:00Z", SystemStatus.CRITICAL, false)        // alarm 3-s (already acked)
        ));
        when(eventAckRepository.findAllById(anyIterable())).thenReturn(List.of(new EventAck("3-s", Instant.now())));

        final long acked = eventService.acknowledgeAll(null, null);

        assertThat(acked).isEqualTo(1);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<EventAck>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventAckRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(EventAck::getId).containsExactly("2-s");
    }

    @Test
    void acknowledgePersistsTheEventId() {
        eventService.acknowledge("42-s");

        final ArgumentCaptor<EventAck> captor = ArgumentCaptor.forClass(EventAck.class);
        verify(eventAckRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("42-s");
        assertThat(captor.getValue().getAckedAt()).isNotNull();
    }
}
