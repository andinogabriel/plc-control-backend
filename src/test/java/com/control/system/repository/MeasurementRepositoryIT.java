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
import java.util.ArrayList;
import java.util.Comparator;
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
        return new MeasurementSearchFilter(from, to, status, tMin, tMax, hMin, hMax, coolerOn, null);
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
        // @CreatedDate auditing may override createdAt on save, so assert against the actual
        // persisted timestamps instead of hard-coded ones.
        final List<Measurement> all = repository.findAll();
        final Instant min = all.stream().map(Measurement::getCreatedAt).min(Comparator.naturalOrder()).orElseThrow();
        final Instant max = all.stream().map(Measurement::getCreatedAt).max(Comparator.naturalOrder()).orElseThrow();

        // A range covering every record returns all of them.
        assertThat(repository.search(filter(min, max, null, null, null, null, null, null), PageRequest.of(0, 10))
            .getTotalElements()).isEqualTo(3);

        // A range entirely after the data returns none.
        assertThat(repository.search(
            filter(max.plusSeconds(3600), max.plusSeconds(7200), null, null, null, null, null, null), PageRequest.of(0, 10))
            .getTotalElements()).isEqualTo(0);
    }

    @Test
    void downSamplesToAtMostMaxPointsButKeepsFullSetOtherwise() {
        repository.deleteAll();
        final List<Measurement> many = new ArrayList<>();
        final Instant base = Instant.parse("2026-02-01T00:00:00Z");
        for (int i = 0; i < 50; i += 1) {
            many.add(measurement(20.0 + i * 0.1, 40.0, i % 2 == 0, SystemStatus.NORMAL, base.plusSeconds(i * 60L)));
        }
        repository.saveAll(many);

        // With maxPoints the wide range is down-sampled to ~maxPoints real points.
        final var downsampled = repository.search(
            new MeasurementSearchFilter(null, null, null, null, null, null, null, null, 10),
            PageRequest.of(0, 1000));
        assertThat(downsampled.getContent()).hasSizeLessThanOrEqualTo(10).hasSizeGreaterThan(1);

        // Without maxPoints the full set comes back (subject to the page size).
        final var full = repository.search(
            new MeasurementSearchFilter(null, null, null, null, null, null, null, null, null),
            PageRequest.of(0, 1000));
        assertThat(full.getContent()).hasSize(50);
    }
}
