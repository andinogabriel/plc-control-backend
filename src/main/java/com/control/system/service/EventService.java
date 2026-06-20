package com.control.system.service;

import com.control.system.domain.entity.Measurement;
import com.control.system.domain.enums.EventType;
import com.control.system.domain.enums.SystemStatus;
import com.control.system.repository.MeasurementRepository;
import com.control.system.web.dto.response.EventResponse;
import com.control.system.web.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds the events/alarms log from the measurement series and paginates it server-side, so the
 * client only ever fetches one page (not the whole history). Events are derived on the fly from the
 * requested window: status transitions (entering an out-of-range/critical state, returning to
 * normal) and cooler ON/OFF actions.
 *
 * <p>Derivation needs the readings in order, so the window is loaded sorted and scanned once; the
 * window itself bounds the work. A future scaling step would persist events as they happen and page
 * them straight from the database.
 */
@Service
@RequiredArgsConstructor
public class EventService {

    /** Window used when the caller does not provide {@code from}. */
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(30);

    private final MeasurementRepository measurementRepository;
    private final DateRangeValidator dateRangeValidator;

    public PageResponse<EventResponse> getEvents(final Instant from, final Instant to, final Pageable pageable) {
        dateRangeValidator.validate(from, to);
        final Instant effectiveTo = to != null ? to : Instant.now();
        final Instant effectiveFrom = from != null ? from : effectiveTo.minus(DEFAULT_WINDOW);

        final List<Measurement> measurements =
            measurementRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(effectiveFrom, effectiveTo);
        return paginate(deriveEvents(measurements), pageable);
    }

    /**
     * Derives events from a chronological list of measurements, returned newest-first. The first
     * sample has no predecessor, so it never emits an event.
     */
    static List<EventResponse> deriveEvents(final List<Measurement> measurements) {
        final List<EventResponse> events = new ArrayList<>();
        SystemStatus prevStatus = null;
        Boolean prevCooler = null;

        for (final Measurement m : measurements) {
            if (prevStatus != null && m.getStatus() != prevStatus) {
                events.add(EventResponse.of(m.getId() + "-s", m.getCreatedAt(), statusEventType(m.getStatus())));
            }
            if (prevCooler != null && m.isCoolerOn() != prevCooler) {
                events.add(EventResponse.of(m.getId() + "-c", m.getCreatedAt(),
                    m.isCoolerOn() ? EventType.COOLER_ON : EventType.COOLER_OFF));
            }
            prevStatus = m.getStatus();
            prevCooler = m.isCoolerOn();
        }

        Collections.reverse(events);
        return events;
    }

    private static EventType statusEventType(final SystemStatus status) {
        return switch (status) {
            case NORMAL -> EventType.RETURN_TO_NORMAL;
            case WARNING_TEMP -> EventType.TEMP_OUT_OF_RANGE;
            case WARNING_HUMIDITY -> EventType.HUMIDITY_OUT_OF_RANGE;
            case CRITICAL -> EventType.CRITICAL;
        };
    }

    private static PageResponse<EventResponse> paginate(final List<EventResponse> events, final Pageable pageable) {
        final int size = pageable.getPageSize();
        final int page = pageable.getPageNumber();
        final int total = events.size();
        final int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        final int start = (int) Math.min((long) page * size, total);
        final int end = Math.min(start + size, total);
        return new PageResponse<>(List.copyOf(events.subList(start, end)), total, totalPages, size, page);
    }
}
