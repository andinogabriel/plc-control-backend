package com.control.system.web;

import com.control.system.web.dto.request.ConfigRequest;
import com.control.system.web.dto.response.ConfigResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Web-layer integration tests against a real MongoDB: exercise the controllers, validation and
 * the global error handler end to end (404 / 400 / 201 / 200). Skipped without Docker; runs in CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class ApiIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        mongoTemplate.getDb().drop();
    }

    @Test
    @DisplayName("GET /api/measurements/latest with no data returns 404")
    void latestMeasurementEmptyReturns404() {
        assertThat(rest.getForEntity("/api/measurements/latest", String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /api/config/latest with no active config returns 404")
    void latestConfigEmptyReturns404() {
        assertThat(rest.getForEntity("/api/config/latest", String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("POST /api/config with an invalid body returns 400")
    void postInvalidConfigReturns400() {
        final ConfigRequest invalid = new ConfigRequest(
            "Bad", "bad@example.com", -100.0, 29.0, 31.0, 65.0, 1.5, 3.0, 30, null);
        assertThat(rest.postForEntity("/api/config", invalid, String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST a valid config returns 201 and then GET latest returns it")
    void postValidConfigThenGetLatest() {
        final ConfigRequest valid = new ConfigRequest(
            "Test", "test@example.com", 18.0, 29.0, 31.0, 65.0, 1.5, 3.0, 30, null);

        final ResponseEntity<ConfigResponse> created = rest.postForEntity("/api/config", valid, ConfigResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        final ResponseEntity<ConfigResponse> latest = rest.getForEntity("/api/config/latest", ConfigResponse.class);
        assertThat(latest.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(latest.getBody()).isNotNull();
        assertThat(latest.getBody().temperatureMax()).isEqualTo(29.0);
        assertThat(latest.getBody().active()).isTrue();
    }
}
