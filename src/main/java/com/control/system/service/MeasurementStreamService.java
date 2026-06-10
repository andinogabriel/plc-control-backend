package com.control.system.service;

import com.control.system.web.dto.response.MeasurementResponse;
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
 * of polling. A periodic heartbeat keeps connections alive and drops dead ones.
 */
@Service
@Slf4j
public class MeasurementStreamService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** Registers a new subscriber. The emitter never times out on its own; cleanup is by events. */
    public SseEmitter subscribe() {
        final SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(t -> emitters.remove(emitter));
        emitters.add(emitter);
        log.debug("SSE subscriber added (total={})", emitters.size());
        return emitter;
    }

    /** Pushes a measurement to every subscriber, dropping the ones whose connection is gone. */
    public void publish(final MeasurementResponse measurement) {
        send(SseEmitter.event().name("measurement").data(measurement));
    }

    @Scheduled(fixedRate = 20_000L)
    void heartbeat() {
        if (!emitters.isEmpty()) {
            send(SseEmitter.event().comment("ping"));
        }
    }

    private void send(final SseEmitter.SseEventBuilder event) {
        for (final SseEmitter emitter : emitters) {
            try {
                emitter.send(event);
            } catch (final IOException | IllegalStateException ex) {
                emitters.remove(emitter);
            }
        }
    }

    /** Visible for tests. */
    int subscriberCount() {
        return emitters.size();
    }
}
