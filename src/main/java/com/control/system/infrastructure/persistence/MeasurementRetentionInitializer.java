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
 * Owns the single {@code measurements.createdAt} index (auto-index creation from {@code @Indexed}
 * is disabled). With retention on, it is a TTL index that both expires old readings and serves the
 * date range/sort queries; with retention off it is a plain index, so range/sort queries stay
 * indexed (never a collection scan). Recreated on startup so a changed retention value applies.
 * Exactly one of the two exists at a time (Mongo rejects two indexes with the same key).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MeasurementRetentionInitializer {

    static final String TTL_INDEX = "measurement_ttl";
    static final String CREATED_AT_INDEX = "measurement_createdAt";

    private final MongoTemplate mongoTemplate;
    private final RetentionProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureTtlIndex() {
        final IndexOperations indexOps = mongoTemplate.indexOps("measurements");

        if (properties.measurementDays() > 0) {
            // TTL index doubles as the createdAt range/sort index. Drop the plain one (if switching
            // from disabled) and recreate the TTL so a changed retention value takes effect.
            dropIfPresent(indexOps, CREATED_AT_INDEX);
            dropIfPresent(indexOps, TTL_INDEX);
            indexOps.ensureIndex(new Index()
                .on("createdAt", Sort.Direction.ASC)
                .named(TTL_INDEX)
                .expire(Duration.ofDays(properties.measurementDays())));
            log.info("Measurement TTL index ensured: keeping {} day(s)", properties.measurementDays());
        } else {
            // No expiry, but createdAt must still be indexed for the date range/sort queries.
            dropIfPresent(indexOps, TTL_INDEX);
            indexOps.ensureIndex(new Index().on("createdAt", Sort.Direction.ASC).named(CREATED_AT_INDEX));
            log.info("Measurement retention disabled; plain createdAt index ensured");
        }
    }

    private static void dropIfPresent(final IndexOperations indexOps, final String name) {
        if (indexOps.getIndexInfo().stream().anyMatch(i -> name.equals(i.getName()))) {
            indexOps.dropIndex(name);
        }
    }
}
