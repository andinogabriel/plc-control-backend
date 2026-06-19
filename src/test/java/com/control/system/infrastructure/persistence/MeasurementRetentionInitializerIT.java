package com.control.system.infrastructure.persistence;

import com.control.system.infrastructure.config.RetentionProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link MeasurementRetentionInitializer} creates/updates/removes the TTL index on
 * a real MongoDB. Skipped automatically without Docker; runs in CI.
 */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
class MeasurementRetentionInitializerIT {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Autowired
    private MongoTemplate mongoTemplate;

    private Optional<IndexInfo> ttlIndex() {
        return indexNamed(MeasurementRetentionInitializer.TTL_INDEX);
    }

    private Optional<IndexInfo> createdAtIndex() {
        return indexNamed(MeasurementRetentionInitializer.CREATED_AT_INDEX);
    }

    private Optional<IndexInfo> indexNamed(final String name) {
        return mongoTemplate.indexOps("measurements").getIndexInfo().stream()
            .filter(i -> name.equals(i.getName()))
            .findFirst();
    }

    @Test
    @DisplayName("Given a positive retention, when initialized, then a TTL index is created with that expiry")
    void givenPositiveRetention_whenInit_thenTtlIndexCreated() {
        new MeasurementRetentionInitializer(mongoTemplate, new RetentionProperties(30)).ensureTtlIndex();

        final Optional<IndexInfo> index = ttlIndex();
        assertThat(index).isPresent();
        assertThat(index.get().getExpireAfter()).contains(Duration.ofDays(30));
        // The TTL index is the createdAt index; no separate plain one (same key would collide).
        assertThat(createdAtIndex()).isEmpty();
    }

    @Test
    @DisplayName("Given a changed retention, when re-initialized, then the TTL index is updated")
    void givenChangedRetention_whenReinit_thenTtlIndexUpdated() {
        new MeasurementRetentionInitializer(mongoTemplate, new RetentionProperties(30)).ensureTtlIndex();
        new MeasurementRetentionInitializer(mongoTemplate, new RetentionProperties(7)).ensureTtlIndex();

        assertThat(ttlIndex()).map(IndexInfo::getExpireAfter).contains(Optional.of(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("Given retention disabled (0), then the TTL is gone but a plain createdAt index remains")
    void givenDisabled_whenInit_thenPlainCreatedAtIndexRemains() {
        new MeasurementRetentionInitializer(mongoTemplate, new RetentionProperties(30)).ensureTtlIndex();
        new MeasurementRetentionInitializer(mongoTemplate, new RetentionProperties(0)).ensureTtlIndex();

        // No expiry, but createdAt stays indexed so range/sort queries never do a collection scan.
        assertThat(ttlIndex()).isEmpty();
        assertThat(createdAtIndex()).isPresent();
        assertThat(createdAtIndex().orElseThrow().getExpireAfter()).isEmpty();
    }
}
