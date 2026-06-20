package com.control.system.service;

import com.control.system.domain.entity.Measurement;
import com.control.system.domain.enums.EventSeverity;
import com.control.system.domain.enums.EventType;
import com.control.system.domain.enums.SystemStatus;
import com.control.system.repository.MeasurementRepository;
import com.control.system.web.dto.response.EventResponse;
import com.control.system.web.dto.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private MeasurementRepository measurementRepository;
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

        // Newest first: return-to-normal, then (cooler-on, temp-alarm) from sample 2.
        assertThat(events).extracting(EventResponse::type)
            .containsExactly(EventType.RETURN_TO_NORMAL, EventType.COOLER_ON, EventType.TEMP_OUT_OF_RANGE);
        assertThat(events.get(0)).extracting(EventResponse::severity, EventResponse::ackable)
            .containsExactly(EventSeverity.SUCCESS, false);
        // Stable id derives from the triggering measurement id.
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
    void paginatesDerivedEventsServerSide() {
        // Five status flips -> five events; page them 2 at a time.
        when(measurementRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(
            m("1", "2026-06-01T10:00:00Z", SystemStatus.NORMAL, false),
            m("2", "2026-06-01T10:01:00Z", SystemStatus.WARNING_TEMP, false),
            m("3", "2026-06-01T10:02:00Z", SystemStatus.NORMAL, false),
            m("4", "2026-06-01T10:03:00Z", SystemStatus.CRITICAL, false),
            m("5", "2026-06-01T10:04:00Z", SystemStatus.NORMAL, false),
            m("6", "2026-06-01T10:05:00Z", SystemStatus.WARNING_HUMIDITY, false)
        ));

        final PageResponse<EventResponse> first = eventService.getEvents(null, null, PageRequest.of(0, 2));
        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.number()).isZero();
        assertThat(first.content()).hasSize(2);

        final PageResponse<EventResponse> last = eventService.getEvents(null, null, PageRequest.of(2, 2));
        assertThat(last.content()).hasSize(1);
    }
}
