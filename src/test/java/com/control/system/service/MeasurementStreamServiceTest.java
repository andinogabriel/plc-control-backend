package com.control.system.service;

import com.control.system.domain.enums.SystemStatus;
import com.control.system.web.dto.response.MeasurementResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MeasurementStreamServiceTest {

    private final MeasurementStreamService service = new MeasurementStreamService();

    @Test
    @DisplayName("Given a new subscriber, when subscribe is called, then it is registered")
    void givenSubscriber_whenSubscribe_thenRegistered() {
        service.subscribe();
        assertThat(service.subscriberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Given a subscriber, when a measurement is published, then it does not throw")
    void givenSubscriber_whenPublish_thenDoesNotThrow() {
        service.subscribe();
        final MeasurementResponse m = new MeasurementResponse(
            "1", 24.0, 45.0, false, false, SystemStatus.NORMAL, Instant.now());
        assertThatCode(() -> service.publish(m)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Given no subscribers, when the heartbeat runs, then it does nothing")
    void givenNoSubscribers_whenHeartbeat_thenNoop() {
        assertThatCode(service::heartbeat).doesNotThrowAnyException();
        assertThat(service.subscriberCount()).isZero();
    }
}
