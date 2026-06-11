package com.control.system.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the global per-IP rate limit returns 429 once the (here, tiny) ceiling is exceeded.
 * Skipped without Docker; runs in CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "app.rate-limit.global.max-requests=2",
    "app.rate-limit.global.window-seconds=60",
    "app.rate-limit.global.blacklist-threshold=100",
})
class RateLimitIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("Exceeding the global per-IP limit returns 429")
    void exceedingGlobalLimitReturns429() {
        rest.getForEntity("/api/config/latest", String.class);
        rest.getForEntity("/api/config/latest", String.class);
        assertThat(rest.getForEntity("/api/config/latest", String.class).getStatusCode())
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
