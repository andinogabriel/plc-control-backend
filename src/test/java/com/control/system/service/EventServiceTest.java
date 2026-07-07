package com.control.system.service;

import com.control.system.domain.entity.EventAck;
import com.control.system.domain.entity.Measurement;
import com.control.system.domain.enums.EventSeverity;
import com.control.system.domain.enums.EventType;
import com.control.system.domain.enums.SystemStatus;
import com.control.system.infrastructure.config.SensorProperties;
import com.control.system.repository.EventAckRepository;
import com.control.system.repository.MeasurementRepository;
import com.control.system.web.dto.response.EventResponse;
import com.control.system.web.dto.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    private static final long OFFLINE_AFTER_SECONDS = 3600;

    @Mock
    private MeasurementRepository measurementRepository;
    @Mock
    private EventAckRepository eventAckRepository;
    @Mock
    private DateRangeValidator dateRangeValidator;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(measurementRepository, eventAckRepository, dateRangeValidator,
            new SensorProperties(OFFLINE_AFTER_SECONDS));
    }

    private Measurement m(final String id, final String at, final SystemStatus status, final boolean cooler) {
        return at(id, Instant.parse(at), status, cooler);
    }

    private Measurement at(final String id, final Instant at, final SystemStatus status, final boolean cooler) {
        return Measurement.builder()
            .id(id).temperature(20).humidity(50).coolerOn(cooler).relayOn(cooler)
            .status(status).createdAt(at).build();
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
        // Fresh readings (latest within the offline threshold), so no SENSOR_OFFLINE alarm is added.
        final Instant base = Instant.now().minusSeconds(600);
        when(measurementRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(
            at("1", base, SystemStatus.NORMAL, false),
            at("2", base.plusSeconds(60), SystemStatus.WARNING_TEMP, false),
            at("3", base.plusSeconds(120), SystemStatus.NORMAL, false),
            at("4", base.plusSeconds(180), SystemStatus.CRITICAL, false),
            at("5", base.plusSeconds(240), SystemStatus.NORMAL, false),
            at("6", base.plusSeconds(300), SystemStatus.WARNING_HUMIDITY, false)
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
        final Instant base = Instant.now().minusSeconds(600);
        when(measurementRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(
            at("1", base, SystemStatus.NORMAL, false),
            at("2", base.plusSeconds(60), SystemStatus.WARNING_TEMP, false),  // alarm 2-s
            at("3", base.plusSeconds(120), SystemStatus.CRITICAL, false)       // alarm 3-s
        ));
        when(eventAckRepository.findAllById(anyIterable())).thenReturn(List.of(new EventAck("3-s", Instant.now())));

        assertThat(eventService.countUnacknowledged(null, null)).isEqualTo(1);
    }

    @Test
    void acknowledgeAllSavesOnlyTheNotYetAcknowledgedAlarms() {
        final Instant base = Instant.now().minusSeconds(600);
        when(measurementRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(
            at("1", base, SystemStatus.NORMAL, false),
            at("2", base.plusSeconds(60), SystemStatus.WARNING_TEMP, false),  // alarm 2-s
            at("3", base.plusSeconds(120), SystemStatus.CRITICAL, false)       // alarm 3-s (already acked)
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

    @Test
    void emitsSensorOfflineAlarmWhenLatestReadingIsStaleOnLiveWindow() {
        final Instant stale = Instant.now().minusSeconds(OFFLINE_AFTER_SECONDS * 2); // well past the threshold
        when(measurementRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(
            at("1", stale.minusSeconds(60), SystemStatus.NORMAL, false),
            at("2", stale, SystemStatus.NORMAL, false)
        ));
        when(eventAckRepository.findAllById(anyIterable())).thenReturn(List.of());

        final PageResponse<EventResponse> page = eventService.getEvents(null, null, PageRequest.of(0, 20));

        final EventResponse newest = page.content().get(0);
        assertThat(newest.type()).isEqualTo(EventType.SENSOR_OFFLINE);
        assertThat(newest.id()).isEqualTo("offline-2"); // tied to the last reading's id
        assertThat(newest.severity()).isEqualTo(EventSeverity.WARNING);
        assertThat(newest.ackable()).isTrue();
        // The offline alarm also lights the global unacknowledged badge.
        assertThat(eventService.countUnacknowledged(null, null)).isEqualTo(1);
    }

    @Test
    void noSensorOfflineAlarmWhenLatestReadingIsFresh() {
        when(measurementRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(
            at("1", Instant.now().minusSeconds(120), SystemStatus.NORMAL, false),
            at("2", Instant.now().minusSeconds(30), SystemStatus.NORMAL, false)
        ));

        final PageResponse<EventResponse> page = eventService.getEvents(null, null, PageRequest.of(0, 20));

        assertThat(page.content()).noneMatch(e -> e.type() == EventType.SENSOR_OFFLINE);
    }

    @Test
    void autoResolvesTransientExcursionsButKeepsTheActiveOne() {
        final Instant base = Instant.now().minusSeconds(600);
        when(measurementRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(
            at("1", base, SystemStatus.NORMAL, false),
            at("2", base.plusSeconds(60), SystemStatus.WARNING_TEMP, false),      // transient: recovers below
            at("3", base.plusSeconds(120), SystemStatus.NORMAL, false),
            at("4", base.plusSeconds(180), SystemStatus.WARNING_HUMIDITY, false)  // still active (no later NORMAL)
        ));
        when(eventAckRepository.findAllById(anyIterable())).thenReturn(List.of());

        // Only the still-active excursion counts; the one that returned to normal is auto-resolved.
        assertThat(eventService.countUnacknowledged(null, null)).isEqualTo(1);
    }

    @Test
    void autoResolvesEveryExcursionOnceTheSystemHasRecovered() {
        final Instant base = Instant.now().minusSeconds(600);
        when(measurementRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(
            at("1", base, SystemStatus.NORMAL, false),
            at("2", base.plusSeconds(60), SystemStatus.WARNING_TEMP, false),
            at("3", base.plusSeconds(120), SystemStatus.CRITICAL, false),
            at("4", base.plusSeconds(180), SystemStatus.NORMAL, false)            // recovered → both resolved
        ));

        assertThat(eventService.countUnacknowledged(null, null)).isEqualTo(0);
    }

    @Test
    void marksAResolvedExcursionAcknowledgedInTheLog() {
        final Instant base = Instant.now().minusSeconds(600);
        when(measurementRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(
            at("1", base, SystemStatus.NORMAL, false),
            at("2", base.plusSeconds(60), SystemStatus.WARNING_TEMP, false),
            at("3", base.plusSeconds(120), SystemStatus.NORMAL, false)
        ));
        when(eventAckRepository.findAllById(anyIterable())).thenReturn(List.of());

        final PageResponse<EventResponse> page = eventService.getEvents(null, null, PageRequest.of(0, 20));

        final EventResponse alarm = page.content().stream()
            .filter(e -> e.id().equals("2-s")).findFirst().orElseThrow();
        assertThat(alarm.acknowledged()).isTrue(); // auto-resolved: still logged, but no operator ACK needed
    }

    @Test
    void noSensorOfflineAlarmForHistoricalWindowWithExplicitTo() {
        final Instant stale = Instant.now().minusSeconds(OFFLINE_AFTER_SECONDS * 2);
        when(measurementRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(
            at("1", stale.minusSeconds(60), SystemStatus.NORMAL, false),
            at("2", stale, SystemStatus.NORMAL, false)
        ));

        final PageResponse<EventResponse> page = eventService.getEvents(null, Instant.now(), PageRequest.of(0, 20));

        assertThat(page.content()).noneMatch(e -> e.type() == EventType.SENSOR_OFFLINE);
    }
}
