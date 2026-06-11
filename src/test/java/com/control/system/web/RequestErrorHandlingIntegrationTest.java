package com.control.system.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Web-layer integration tests for malformed requests: a broken body, a wrong method and an
 * unsupported content type must map to 400 / 405 / 415, not a generic 500. Skipped without Docker;
 * runs in CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class RequestErrorHandlingIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("POST /api/config with a syntactically broken JSON body returns 400")
    void malformedJsonBodyReturns400() {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        final HttpEntity<String> request = new HttpEntity<>("{ \"temperatureMin\": ", headers);

        assertThat(rest.exchange("/api/config", HttpMethod.POST, request, String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("An unsupported method on a mapped path returns 405")
    void unsupportedMethodReturns405() {
        assertThat(rest.exchange("/api/measurements/latest", HttpMethod.DELETE, HttpEntity.EMPTY, String.class)
            .getStatusCode())
            .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("POST /api/config with an unsupported content type returns 415")
    void unsupportedMediaTypeReturns415() {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        final HttpEntity<String> request = new HttpEntity<>("not json", headers);

        assertThat(rest.exchange("/api/config", HttpMethod.POST, request, String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }
}
