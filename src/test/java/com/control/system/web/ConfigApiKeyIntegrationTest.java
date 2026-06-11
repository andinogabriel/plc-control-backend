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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check that the optional API-key gate is actually wired into the Spring filter chain:
 * with {@code app.security.config-api-key} set, {@code POST /api/config} requires a matching
 * {@code X-Api-Key} header (401 otherwise), while unrelated reads stay open. The filter is also
 * unit-tested in {@code ConfigApiKeyFilterTest}; this proves the real wiring. Skipped without
 * Docker; runs in CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "app.security.config-api-key=" + ConfigApiKeyIntegrationTest.API_KEY)
class ConfigApiKeyIntegrationTest {

    static final String API_KEY = "secret-test-key";

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

    private static ConfigRequest validConfig() {
        return new ConfigRequest("Test", "test@example.com", 18.0, 29.0, 31.0, 65.0, 1.5, 3.0, 30, null);
    }

    private ResponseEntity<String> postConfig(final String apiKey) {
        final HttpHeaders headers = new HttpHeaders();
        if (apiKey != null) {
            headers.set("X-Api-Key", apiKey);
        }
        return rest.exchange("/api/config", HttpMethod.POST, new HttpEntity<>(validConfig(), headers), String.class);
    }

    @Test
    @DisplayName("POST /api/config without the X-Api-Key header returns 401")
    void postWithoutKeyReturns401() {
        assertThat(postConfig(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /api/config with a wrong X-Api-Key returns 401")
    void postWithWrongKeyReturns401() {
        assertThat(postConfig("wrong-key").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /api/config with the correct X-Api-Key returns 201")
    void postWithValidKeyReturns201() {
        final HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Key", API_KEY);
        final ResponseEntity<ConfigResponse> created = rest.exchange(
            "/api/config", HttpMethod.POST, new HttpEntity<>(validConfig(), headers), ConfigResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().active()).isTrue();
    }

    @Test
    @DisplayName("The gate only covers POST /api/config: GET /api/config/latest stays open (404 when empty)")
    void getLatestIsNotGated() {
        assertThat(rest.getForEntity("/api/config/latest", String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
