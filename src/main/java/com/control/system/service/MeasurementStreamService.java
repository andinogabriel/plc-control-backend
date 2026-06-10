package com.control.system.service;

import com.control.system.infrastructure.config.StreamProperties;
import com.control.system.web.dto.response.MeasurementResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-Sent Events hub for live measurements. Clients subscribe with an open HTTP connection;
 * every new measurement is pushed to all of them, so the frontend updates in real time instead
 * of polling.
 * <p>
 * Resource usage is bounded so many open tabs/kiosks (or a flood) cannot take the server down:
 * a global cap on total connections, a per-IP cap so one client cannot hog the slots, an
 * optional per-connection timeout, and a heartbeat that prunes dead connections. The whole
 * feature can be disabled via {@link StreamProperties#enabled()}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeasurementStreamService {

    private record Subscriber(String ip, SseEmitter emitter) {
    }

    private final StreamProperties props;
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    /**
     * Registers a new subscriber for the given client IP. If the stream is disabled or a limit
     * (global or per-IP) is reached, returns an already-completed emitter so the client closes
     * the connection and keeps polling, without the server holding it open.
     */
    public SseEmitter subscribe(final String ip) {
        if (!props.enabled() || subscribers.size() >= props.maxSubscribers() || perIpCount(ip) >= props.maxSubscribersPerIp()) {
            log.debug("SSE subscription rejected (enabled={} total={} ipCount={})",
                props.enabled(), subscribers.size(), perIpCount(ip));
            final SseEmitter rejected = new SseEmitter(1L);
            try { rejected.complete(); } catch (final RuntimeException ignored) { /* already closed */ }
            return rejected;
        }

        final SseEmitter emitter = new SseEmitter(props.timeoutMs());
        final Subscriber subscriber = new Subscriber(ip, emitter);
        emitter.onCompletion(() -> subscribers.remove(subscriber));
        emitter.onTimeout(() -> subscribers.remove(subscriber));
        emitter.onError(t -> subscribers.remove(subscriber));
        subscribers.add(subscriber);
        log.debug("SSE subscriber added (total={})", subscribers.size());
        return emitter;
    }

    /** Pushes a measurement to every subscriber, dropping the ones whose connection is gone. */
    public void publish(final MeasurementResponse measurement) {
        if (!props.enabled()) {
            return;
        }
        send(SseEmitter.event().name("measurement").data(measurement));
    }

    @Scheduled(fixedRateString = "${app.stream.heartbeat-interval-ms:20000}")
    void heartbeat() {
        if (!subscribers.isEmpty()) {
            send(SseEmitter.event().comment("ping"));
        }
    }

    private long perIpCount(final String ip) {
        return subscribers.stream().filter(s -> s.ip().equals(ip)).count();
    }

    private void send(final SseEmitter.SseEventBuilder event) {
        for (final Subscriber subscriber : subscribers) {
            try {
                subscriber.emitter().send(event);
            } catch (final IOException | IllegalStateException ex) {
                subscribers.remove(subscriber);
            }
        }
    }

    /** Visible for tests. */
    int subscriberCount() {
        return subscribers.size();
    }
}
