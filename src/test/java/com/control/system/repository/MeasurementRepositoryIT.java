package com.control.system.repository;

import com.control.system.domain.entity.Measurement;
import com.control.system.domain.enums.SystemStatus;
import com.control.system.repository.filter.MeasurementSearchFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the custom measurement search (date/numeric/status/cooler filters)
 * against a real MongoDB via Testcontainers. Skipped automatically where Docker is not
 * available (e.g. local runs without Docker); runs in CI.
 */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
class MeasurementRepositoryIT {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Autowired
    private MeasurementRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.saveAll(List.of(
            measurement(20.0, 40.0, false, SystemStatus.NORMAL, Instant.parse("2026-01-01T10:00:00Z")),
            measurement(30.0, 80.0, true, SystemStatus.WARNING_TEMP, Instant.parse("2026-01-02T10:00:00Z")),
            measurement(26.0, 50.0, false, SystemStatus.NORMAL, Instant.parse("2026-01-03T10:00:00Z"))
        ));
    }

    private Measurement measurement(final double t, final double h, final boolean cooler, final SystemStatus s, final Instant at) {
        return Measurement.builder()
            .temperature(t).humidity(h).coolerOn(cooler).relayOn(cooler).status(s).createdAt(at).build();
    }

    private MeasurementSearchFilter filter(final Instant from, final Instant to, final SystemStatus status,
                                           final Double tMin, final Double tMax, final Double hMin, final Double hMax,
                                           final Boolean coolerOn) {
        return new MeasurementSearchFilter(from, to, status, tMin, tMax, hMin, hMax, coolerOn);
    }

    @Test
    void filtersByCoolerOn() {
        final var page = repository.search(filter(null, null, null, null, null, null, null, true), PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTemperature()).isEqualTo(30.0);
    }

    @Test
    void filtersByTemperatureRange() {
        final var page = repository.search(filter(null, null, null, 25.0, 40.0, null, null, null), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void filtersByStatus() {
        final var page = repository.search(filter(null, null, SystemStatus.NORMAL, null, null, null, null, null), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void filtersByDateRange() {
        final var page = repository.search(
            filter(Instant.parse("2026-01-02T00:00:00Z"), Instant.parse("2026-01-02T23:59:59Z"), null, null, null, null, null, null),
            PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}
