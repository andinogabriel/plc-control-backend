package com.control.system.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check of the SSE endpoint wiring (controller + ClientIpResolver + the stream
 * service's cap logic). The global cap is forced to 0 so every subscription is rejected and the
 * emitter completes immediately: the request returns a clean {@code text/event-stream} 200 instead
 * of holding the connection open. The cap logic itself is unit-tested in MeasurementStreamServiceTest.
 * Skipped without Docker; runs in CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "app.stream.max-subscribers=0")
class StreamEndpointIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("GET /api/measurements/stream over the cap returns a completed text/event-stream 200 (no hang)")
    void streamEndpointRejectsOverCapCleanly() {
        final ResponseEntity<String> response = rest.getForEntity("/api/measurements/stream", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Type"))
            .contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }
}
