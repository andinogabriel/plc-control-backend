package com.control.system.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.core.JsonProcessingException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract guard for the generated OpenAPI document. Boots the app and asserts that every public
 * endpoint and the core schemas are present in {@code /api-docs}, so accidentally removing,
 * renaming or breaking an endpoint's annotations is caught in CI rather than by a client.
 * Skipped without Docker; runs in CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class OpenApiContractIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Autowired
    private TestRestTemplate rest;

    private JsonNode fetchApiDocs() throws JsonProcessingException {
        final ResponseEntity<String> response = rest.getForEntity("/api-docs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new ObjectMapper().readTree(response.getBody());
    }

    @Test
    @DisplayName("/api-docs exposes every public path")
    void exposesAllPaths() throws JsonProcessingException {
        final JsonNode paths = fetchApiDocs().path("paths");

        assertThat(paths.has("/api/config")).isTrue();
        assertThat(paths.has("/api/config/latest")).isTrue();
        assertThat(paths.has("/api/config/history")).isTrue();
        assertThat(paths.has("/api/measurements")).isTrue();
        assertThat(paths.has("/api/measurements/latest")).isTrue();
        assertThat(paths.has("/api/measurements/stream")).isTrue();
    }

    @Test
    @DisplayName("/api-docs declares the expected HTTP verbs on the config endpoints")
    void declaresExpectedVerbs() throws JsonProcessingException {
        final JsonNode paths = fetchApiDocs().path("paths");

        assertThat(paths.path("/api/config").has("post")).isTrue();
        assertThat(paths.path("/api/config/latest").has("get")).isTrue();
        assertThat(paths.path("/api/measurements").has("post")).isTrue();
        assertThat(paths.path("/api/measurements").has("get")).isTrue();
    }

    @Test
    @DisplayName("/api-docs registers the core request/response/error schemas")
    void registersCoreSchemas() throws JsonProcessingException {
        final JsonNode schemas = fetchApiDocs().path("components").path("schemas");

        assertThat(schemas.has("ConfigRequest")).isTrue();
        assertThat(schemas.has("ConfigResponse")).isTrue();
        assertThat(schemas.has("MeasurementResponse")).isTrue();
        assertThat(schemas.has("ErrorResponse")).isTrue();
    }
}
