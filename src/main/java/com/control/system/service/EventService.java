package com.control.system.service;

import com.control.system.domain.entity.EventAck;
import com.control.system.domain.entity.Measurement;
import com.control.system.domain.enums.EventType;
import com.control.system.domain.enums.SystemStatus;
import com.control.system.infrastructure.config.SensorProperties;
import com.control.system.repository.EventAckRepository;
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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Builds the events/alarms log from the measurement series and paginates it server-side, so the
 * client only ever fetches one page (not the whole history). Events are derived on the fly from the
 * requested window: status transitions (entering an out-of-range/critical state, returning to
 * normal) and cooler ON/OFF actions.
 *
 * <p>Acknowledgements are persisted (collection {@code event_acks}) keyed by the stable event id,
 * so an ACK is shared across clients and survives restarts, and the unacknowledged count is global
 * across the whole window (not just the page being viewed).
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
    private final EventAckRepository eventAckRepository;
    private final DateRangeValidator dateRangeValidator;
    private final SensorProperties sensorProperties;

    public PageResponse<EventResponse> getEvents(final Instant from, final Instant to, final Pageable pageable) {
        final List<EventResponse> events = deriveWindow(from, to);

        final int size = pageable.getPageSize();
        final int page = pageable.getPageNumber();
        final int total = events.size();
        final int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        final int start = (int) Math.min((long) page * size, total);
        final int end = Math.min(start + size, total);

        final List<EventResponse> pageEvents = events.subList(start, end);
        final Set<String> acked = ackedAmong(pageEvents.stream().map(EventResponse::id).toList());
        final List<EventResponse> content = pageEvents.stream()
            .map(e -> acked.contains(e.id()) ? e.withAcknowledged(true) : e)
            .toList();

        return new PageResponse<>(content, total, totalPages, size, page);
    }

    /** Marks a single event as acknowledged. Idempotent: the event id is the document id. */
    public void acknowledge(final String eventId) {
        eventAckRepository.save(new EventAck(eventId, Instant.now()));
    }

    /** Acknowledges every still-unacknowledged alarm in the window. Returns how many were acked. */
    public long acknowledgeAll(final Instant from, final Instant to) {
        final List<String> ackableIds = ackableIds(deriveWindow(from, to));
        final Set<String> alreadyAcked = ackedAmong(ackableIds);
        final List<EventAck> toAck = ackableIds.stream()
            .filter(id -> !alreadyAcked.contains(id))
            .map(id -> new EventAck(id, Instant.now()))
            .toList();
        eventAckRepository.saveAll(toAck);
        return toAck.size();
    }

    /** Count of unacknowledged alarms across the whole window (for the global badge). */
    public long countUnacknowledged(final Instant from, final Instant to) {
        final List<String> ackableIds = ackableIds(deriveWindow(from, to));
        return ackableIds.size() - ackedAmong(ackableIds).size();
    }

    private List<EventResponse> deriveWindow(final Instant from, final Instant to) {
        dateRangeValidator.validate(from, to);
        final Instant effectiveTo = to != null ? to : Instant.now();
        final Instant effectiveFrom = from != null ? from : effectiveTo.minus(DEFAULT_WINDOW);
        final List<Measurement> window =
            measurementRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(effectiveFrom, effectiveTo);
        final List<EventResponse> events = deriveEvents(window);
        maybeAddOfflineEvent(events, window, to);
        return events;
    }

    /**
     * Live view only ({@code to == null}, i.e. up to "now"): if the most recent reading is older
     * than the offline threshold, surface a synthetic SENSOR_OFFLINE alarm as the newest event. Its
     * id is tied to that last reading, so the ACK sticks for this outage and a later outage (after a
     * new reading arrives) is a distinct alarm. Historical queries (explicit {@code to}) never get it.
     */
    private void maybeAddOfflineEvent(final List<EventResponse> events, final List<Measurement> window, final Instant to) {
        if (to != null || window.isEmpty()) {
            return;
        }
        final Measurement latest = window.get(window.size() - 1);
        final Instant offlineSince = latest.getCreatedAt().plusSeconds(sensorProperties.offlineAfterSeconds());
        if (Instant.now().isAfter(offlineSince)) {
            events.add(0, EventResponse.of("offline-" + latest.getId(), offlineSince, EventType.SENSOR_OFFLINE));
        }
    }

    private static List<String> ackableIds(final List<EventResponse> events) {
        // Auto-resolved (transient) alarms come back already acknowledged, so they neither count
        // toward the badge nor get re-acked by "acknowledge all" — only still-active ones do.
        return events.stream()
            .filter(e -> e.ackable() && !e.acknowledged())
            .map(EventResponse::id)
            .toList();
    }

    private Set<String> ackedAmong(final List<String> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return StreamSupport.stream(eventAckRepository.findAllById(ids).spliterator(), false)
            .map(EventAck::getId)
            .collect(Collectors.toSet());
    }

    /**
     * Derives events from a chronological list of measurements, returned newest-first. The first
     * sample has no predecessor, so it never emits an event.
     */
    static List<EventResponse> deriveEvents(final List<Measurement> measurements) {
        // A transient excursion is one the system already recovered from: its alarm stays in the log
        // but is auto-resolved (marked acknowledged) so it no longer demands operator action. The
        // boundary is the last NORMAL reading — an alarm before it has a later return to normal;
        // only alarms after it (i.e. the currently active excursion, if any) stay open.
        Instant lastNormalTime = null;
        for (final Measurement m : measurements) {
            if (m.getStatus() == SystemStatus.NORMAL) {
                lastNormalTime = m.getCreatedAt();
            }
        }

        final List<EventResponse> events = new ArrayList<>();
        SystemStatus prevStatus = null;
        Boolean prevCooler = null;

        for (final Measurement m : measurements) {
            if (prevStatus != null && m.getStatus() != prevStatus) {
                EventResponse event = EventResponse.of(m.getId() + "-s", m.getCreatedAt(), statusEventType(m.getStatus()));
                if (event.ackable() && lastNormalTime != null && event.time().isBefore(lastNormalTime)) {
                    event = event.withAcknowledged(true);
                }
                events.add(event);
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
}
