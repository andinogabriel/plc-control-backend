package com.control.system.infrastructure.persistence;

import com.control.system.domain.entity.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link MongoIndexInitializer} creates the expected query indexes and that the
 * single-active-config invariant is enforced at the storage layer. Skipped without Docker; CI runs it.
 */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
class MongoIndexInitializerIT {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate.getDb().drop();
        new MongoIndexInitializer(mongoTemplate).ensureIndexes();
    }

    private Set<String> indexNames(final String collection) {
        return mongoTemplate.indexOps(collection).getIndexInfo().stream()
            .map(i -> i.getName())
            .collect(Collectors.toSet());
    }

    private static Config active(final boolean isActive) {
        final Config c = new Config();
        c.setTemperatureMin(18);
        c.setTemperatureMax(26);
        c.setHumidityMin(30);
        c.setHumidityMax(60);
        c.setHysteresisTemperature(1.0);
        c.setHysteresisHumidity(2.0);
        c.setMeasurementIntervalSeconds(30);
        c.setCreatedByName("Test");
        c.setCreatedByEmail("test@example.com");
        c.setActive(isActive);
        c.setCreatedAt(Instant.now());
        return c;
    }

    @Test
    @DisplayName("The measurement and config query indexes are created")
    void queryIndexesCreated() {
        assertThat(indexNames("measurements")).contains(MongoIndexInitializer.MEASUREMENT_STATUS_INDEX);
        assertThat(indexNames("configs"))
            .contains(MongoIndexInitializer.CONFIG_ACTIVE_INDEX, MongoIndexInitializer.CONFIG_SINGLE_ACTIVE_INDEX);
    }

    @Test
    @DisplayName("A second active config is rejected by the unique partial index")
    void secondActiveConfigRejected() {
        mongoTemplate.insert(active(true));

        assertThatThrownBy(() -> mongoTemplate.insert(active(true)))
            .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("Multiple inactive configs are allowed (the unique index is partial on active=true)")
    void multipleInactiveConfigsAllowed() {
        mongoTemplate.insert(active(false));
        mongoTemplate.insert(active(false));

        assertThat(mongoTemplate.findAll(Config.class)).hasSize(2);
    }
}
