package com.control.system.service;

import com.control.system.domain.enums.SystemStatus;
import com.control.system.infrastructure.config.StreamProperties;
import com.control.system.web.dto.response.MeasurementResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MeasurementStreamServiceTest {

    private static StreamProperties props(final boolean enabled, final int max, final int perIp) {
        return new StreamProperties(enabled, 20000L, max, perIp, 0L);
    }

    @Test
    @DisplayName("Given a new subscriber, when subscribe is called, then it is registered")
    void givenSubscriber_whenSubscribe_thenRegistered() {
        final MeasurementStreamService service = new MeasurementStreamService(props(true, 20, 3));
        service.subscribe("1.1.1.1");
        assertThat(service.subscriberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Given the stream disabled, when subscribe is called, then no connection is kept")
    void givenDisabled_whenSubscribe_thenNotRegistered() {
        final MeasurementStreamService service = new MeasurementStreamService(props(false, 20, 3));
        service.subscribe("1.1.1.1");
        assertThat(service.subscriberCount()).isZero();
    }

    @Test
    @DisplayName("Given the per-IP cap is reached, when the same IP subscribes again, then it is rejected")
    void givenPerIpCapReached_whenSubscribeAgain_thenRejected() {
        final MeasurementStreamService service = new MeasurementStreamService(props(true, 20, 2));
        service.subscribe("1.1.1.1");
        service.subscribe("1.1.1.1");
        service.subscribe("1.1.1.1"); // over the per-IP cap
        assertThat(service.subscriberCount()).isEqualTo(2);
        // A different IP can still connect.
        service.subscribe("2.2.2.2");
        assertThat(service.subscriberCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Given the global cap is reached, when another IP subscribes, then it is rejected")
    void givenGlobalCapReached_whenSubscribe_thenRejected() {
        final MeasurementStreamService service = new MeasurementStreamService(props(true, 2, 5));
        service.subscribe("1.1.1.1");
        service.subscribe("2.2.2.2");
        service.subscribe("3.3.3.3"); // over the global cap
        assertThat(service.subscriberCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Given a subscriber, when a measurement is published, then it does not throw")
    void givenSubscriber_whenPublish_thenDoesNotThrow() {
        final MeasurementStreamService service = new MeasurementStreamService(props(true, 20, 3));
        service.subscribe("1.1.1.1");
        final MeasurementResponse m = new MeasurementResponse(
            "1", 24.0, 45.0, false, false, SystemStatus.NORMAL, Instant.now());
        assertThatCode(() -> service.publish(m)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Given no subscribers, when the heartbeat runs, then it does nothing")
    void givenNoSubscribers_whenHeartbeat_thenNoop() {
        final MeasurementStreamService service = new MeasurementStreamService(props(true, 20, 3));
        assertThatCode(service::heartbeat).doesNotThrowAnyException();
        assertThat(service.subscriberCount()).isZero();
    }
}
