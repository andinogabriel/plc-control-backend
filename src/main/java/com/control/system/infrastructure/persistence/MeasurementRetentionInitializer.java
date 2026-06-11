package com.control.system.infrastructure.persistence;

import com.control.system.infrastructure.config.RetentionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Ensures a TTL index on {@code measurements.createdAt} so old readings are dropped automatically
 * (the collection would otherwise grow without bound). The index is dropped and recreated on
 * startup so the configured retention is always applied; a non-positive value disables expiry.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MeasurementRetentionInitializer {

    static final String TTL_INDEX = "measurement_ttl";

    private final MongoTemplate mongoTemplate;
    private final RetentionProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureTtlIndex() {
        final IndexOperations indexOps = mongoTemplate.indexOps("measurements");

        // Drop the previous TTL index (if any) so a changed retention value takes effect.
        final boolean exists = indexOps.getIndexInfo().stream().anyMatch(i -> TTL_INDEX.equals(i.getName()));
        if (exists) {
            indexOps.dropIndex(TTL_INDEX);
        }

        if (properties.measurementDays() > 0) {
            indexOps.ensureIndex(new Index()
                .on("createdAt", Sort.Direction.ASC)
                .named(TTL_INDEX)
                .expire(Duration.ofDays(properties.measurementDays())));
            log.info("Measurement TTL index ensured: keeping {} day(s)", properties.measurementDays());
        } else {
            log.info("Measurement retention disabled (no TTL index)");
        }
    }
}
