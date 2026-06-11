package com.control.system.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the actuator liveness/readiness probes are actually exposed and green when the app
 * is up with a reachable MongoDB. The container HEALTHCHECK targets the liveness endpoint, so a
 * regression in the management config (e.g. probes disabled) would silently break orchestration.
 * Skipped without Docker; runs in CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class HealthProbesIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("GET /actuator/health/liveness returns 200")
    void livenessProbeIsUp() {
        assertThat(rest.getForEntity("/actuator/health/liveness", String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /actuator/health/readiness returns 200 when MongoDB is reachable")
    void readinessProbeIsUp() {
        assertThat(rest.getForEntity("/actuator/health/readiness", String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
    }
}
